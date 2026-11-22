package org.mhatta.rakkyo;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.subgraph.orchid.CircuitInfo;
import com.subgraph.orchid.DormantListener;
import com.subgraph.orchid.GuardInfo;
import com.subgraph.orchid.TorClient;
import com.subgraph.orchid.TorConfig;
import com.subgraph.orchid.TorInitializationListener;
import com.subgraph.orchid.TrafficInfo;
import com.subgraph.orchid.data.HexDigest;
import com.subgraph.orchid.data.IPv4Address;
import com.subgraph.orchid.logging.LogHandler;
import com.subgraph.orchid.logging.Logger;
import com.subgraph.orchid.logging.SysLog;

/**
 * The one Orchid client of the process: starts and stops it, and tells the
 * UI and the foreground service how it is doing.
 *
 * <p>A TorClient cannot be restarted, so each connection gets a new one.
 * Starting and stopping run on a thread of their own; queries that may be
 * slow ({@link TorClient#getCircuits()}, which loads the GeoIP database on
 * first use) run on another, so that they never wait behind a stop.
 * Listeners are called on the main thread.
 */
final class TorController {
    private static final String TAG = "Rakkyo";
    /** The proxies listen on the loopback address only. */
    static final String LISTEN_ADDRESS = "127.0.0.1";

    enum State { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

    enum Failure {
        /** A port is taken by another app; {@link Status#failedPort} says which. */
        PORT_IN_USE,
        /** Bridges are on but the lines are not usable. */
        BAD_BRIDGES,
        OTHER
    }

    private static final java.util.concurrent.atomic.AtomicLong SEQUENCE = new java.util.concurrent.atomic.AtomicLong();

    /** What the UI shows.  Immutable. */
    static final class Status {
        /** Grows with each new status, so that a listener can tell an old one from a new one. */
        final long seq = SEQUENCE.incrementAndGet();
        final State state;
        final int percent;
        final boolean dormant;
        final Failure failure;
        final int failedPort;
        /** The settings the running client was started with, or null. */
        final Prefs.Snapshot settings;

        Status(State state, int percent, boolean dormant, Failure failure, int failedPort, Prefs.Snapshot settings) {
            this.state = state;
            this.percent = percent;
            this.dormant = dormant;
            this.failure = failure;
            this.failedPort = failedPort;
            this.settings = settings;
        }

        boolean isActive() {
            return state == State.STARTING || state == State.RUNNING;
        }
    }

    interface Listener {
        void onStatusChanged(Status status);
    }

    interface Callback<T> {
        void onResult(T result);
    }

    private static TorController instance;

    private final Context context;
    private final Prefs prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService control = Executors.newSingleThreadExecutor();
    private final ExecutorService query = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    /** Touched on the control thread only, except for reads of the volatile fields. */
    private volatile TorClient client;
    private volatile Status status = new Status(State.STOPPED, 0, false, null, 0, null);
    /** Bumped for each client, so that callbacks of an old one are ignored. */
    private volatile int generation;
    private ConnectivityManager.NetworkCallback networkCallback;
    /*
     * The proxy ports, bound by Rakkyo and kept while the client restarts
     * (Orchid's own ports would be closed by stop() and bound again by the
     * next client, and in between any app could bind them, and receive
     * the connections, host names and passwords meant for Tor).  Closed
     * when the proxy stops or fails.  Control thread only.
     */
    private ServerSocket socksSocket;
    private ServerSocket httpSocket;

    static synchronized TorController get(Context context) {
        if (instance == null) {
            instance = new TorController(context.getApplicationContext());
        }
        return instance;
    }

    private TorController(Context context) {
        this.context = context;
        this.prefs = new Prefs(context);
        installLogHandler((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
    }

    /*
     * Orchid's log goes to logcat in debug builds only.  Orchid leaves out
     * the destinations of streams and the addresses of guards and bridges
     * (SafeLogging is on by default), but even its warnings say what the
     * client does and when, and logcat can be read with adb, in bug reports
     * and by device makers' log collection.  Release builds forward
     * nothing; why the proxy could not start is shown in the app.
     */
    private static void installLogHandler(final boolean debuggable) {
        Logger.setHandler(new LogHandler() {
            public void log(SysLog level, String name, String message, Throwable throwable) {
                if (!debuggable) {
                    return;
                }
                final String text = name + ": " + message;
                if (level.isGreaterOrEqual(SysLog.ERROR)) {
                    Log.e(TAG, text, throwable);
                } else if (level.isGreaterOrEqual(SysLog.WARNING)) {
                    Log.w(TAG, text, throwable);
                } else if (level.isGreaterOrEqual(SysLog.INFORMATIONAL)) {
                    Log.i(TAG, text, throwable);
                }
            }
        });
    }

    Status status() {
        return status;
    }

    /** Add a listener; it is told the current status at once. */
    void addListener(Listener l) {
        listeners.add(l);
        l.onStatusChanged(status);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Start Orchid unless it is starting or running.  Call {@link TorService#start} instead from the UI. */
    void start() {
        control.execute(new Runnable() {
            public void run() {
                if (client == null) {
                    startOnControlThread();
                }
            }
        });
    }

    void stop() {
        control.execute(new Runnable() {
            public void run() {
                stopOnControlThread(State.STOPPED, null, 0);
            }
        });
    }

    /** Stop and start again, so that changed settings take effect. */
    void restart() {
        control.execute(new Runnable() {
            public void run() {
                // No STOPPED in between: the service would take it for the end.
                stopOnControlThread(State.STOPPING, null, 0);
                startOnControlThread();
            }
        });
    }

    private void startOnControlThread() {
        final Prefs.Snapshot settings = prefs.snapshot();
        final int gen = ++generation;
        setStatus(new Status(State.STARTING, 0, false, null, 0, settings));

        final BridgeLines.Result bridges = BridgeLines.parse(settings.bridges);
        if (settings.useBridges && (!bridges.isValid() || bridges.bridges.isEmpty())) {
            closePorts();
            setStatus(new Status(State.FAILED, 0, false, Failure.BAD_BRIDGES, 0, null));
            return;
        }

        // The ports first: a port that another app holds is found before
        // Orchid starts, and one that Rakkyo already holds is kept.
        int port = settings.socksPort;
        try {
            socksSocket = keepOrBind(socksSocket, settings.socksPort);
            port = settings.httpPort;
            if (settings.httpPort != 0) {
                httpSocket = keepOrBind(httpSocket, settings.httpPort);
            } else {
                closeQuietly(httpSocket);
                httpSocket = null;
            }
        } catch (IOException e) {
            Log.w(TAG, "Cannot listen on port " + port + ": " + e.getMessage());
            closePorts();
            setStatus(new Status(State.FAILED, 0, false, Failure.PORT_IN_USE, port, null));
            return;
        }

        final TorClient c = new TorClient();
        final TorConfig config = c.getConfig();
        config.setDataDirectory(new File(context.getNoBackupFilesDir(), "orchid"));
        config.setSocksListenAddress(LISTEN_ADDRESS);
        // One password per profile, or none: see Profiles.
        config.setSocksPasswords(settings.passwords);
        config.setIsolateDestAddr(settings.isolateDest);
        // An hour without use and Orchid rests; the next connection wakes it.
        config.setDormantClientTimeout(1, TimeUnit.HOURS);
        if (settings.useBridges) {
            config.setUseBridges(true);
            for (BridgeLines.Bridge b : bridges.bridges) {
                final IPv4Address address = IPv4Address.createFromString(b.address);
                if (b.fingerprint != null) {
                    config.addBridge(address, b.port, HexDigest.createFromString(b.fingerprint));
                } else {
                    config.addBridge(address, b.port);
                }
            }
        }

        c.addInitializationListener(new TorInitializationListener() {
            public void initializationProgress(String message, int percent) {
                if (gen == generation && status.state == State.STARTING) {
                    setStatus(new Status(State.STARTING, percent, false, null, 0, settings));
                }
            }

            public void initializationCompleted() {
                if (gen == generation) {
                    setStatus(new Status(State.RUNNING, 100, c.isDormant(), null, 0, settings));
                }
            }
        });
        c.addDormantListener(new DormantListener() {
            public void dormantStateChanged(boolean dormant) {
                final Status s = status;
                if (gen == generation && s.state == State.RUNNING) {
                    setStatus(new Status(State.RUNNING, 100, dormant, null, 0, s.settings));
                }
            }
        });

        try {
            c.start();
        } catch (RuntimeException e) {
            Log.e(TAG, "Orchid failed to start", e);
            generation++;
            c.stop();
            closePorts();
            setStatus(new Status(State.FAILED, 0, false, Failure.OTHER, 0, null));
            return;
        }

        // Accepting only after start(): stop() stops a started client only.
        try {
            c.enableSocksListener(socksSocket);
            if (httpSocket != null) {
                c.enableHttpTunnelListener(httpSocket);
            }
        } catch (RuntimeException e) {
            // TorException, or IllegalStateException if the client stopped meanwhile
            Log.w(TAG, "Cannot accept on the proxy ports: " + e.getMessage());
            generation++;
            c.stop();
            closePorts();
            setStatus(new Status(State.FAILED, 0, false, Failure.OTHER, 0, null));
            return;
        }
        client = c;
        watchNetwork();
    }

    /*
     * The server socket for a port: the one already held if it is for that
     * port, else a new one on the loopback address (the old one closed).
     */
    private static ServerSocket keepOrBind(ServerSocket held, int port) throws IOException {
        if (held != null && !held.isClosed() && held.getLocalPort() == port) {
            return held;
        }
        closeQuietly(held);
        return new ServerSocket(port, 50, InetAddress.getByName(LISTEN_ADDRESS));
    }

    private void closePorts() {
        closeQuietly(socksSocket);
        closeQuietly(httpSocket);
        socksSocket = null;
        httpSocket = null;
    }

    private static void closeQuietly(ServerSocket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                // closing anyway
            }
        }
    }

    /*
     * With next STOPPING (a restart), the ports stay open for the next
     * client, which takes them over; Orchid's stop() returns once the old
     * client no longer accepts on them.  Otherwise they are closed.
     */
    private void stopOnControlThread(State next, Failure failure, int failedPort) {
        final TorClient c = client;
        if (c == null) {
            if (next != State.STOPPING) {
                closePorts();
            }
            if (status.state != next || failure != null) {
                setStatus(new Status(next, 0, false, failure, failedPort, null));
            }
            return;
        }
        generation++;
        client = null;
        unwatchNetwork();
        setStatus(new Status(State.STOPPING, 0, false, null, 0, null));
        c.stop();
        if (next != State.STOPPING) {
            closePorts();
        }
        setStatus(new Status(next, 0, false, failure, failedPort, null));
    }

    /* Tell Orchid when the device changes networks (Wi-Fi to mobile data, back online). */
    private void watchNetwork() {
        final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }
        networkCallback = new ConnectivityManager.NetworkCallback() {
            private Network current;
            private boolean lost;

            @Override
            public void onAvailable(Network network) {
                // Also called once at registration, for the network in use: not a change.
                final boolean changed = (current != null && !current.equals(network)) || lost;
                current = network;
                lost = false;
                if (changed) {
                    notifyNetworkChanged();
                }
            }

            @Override
            public void onLost(Network network) {
                if (network.equals(current)) {
                    lost = true;
                }
            }
        };
        try {
            cm.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException e) {
            // Too many callbacks registered (a TooManyRequestsException on 11+).
            Log.w(TAG, "Cannot watch the network: " + e);
            networkCallback = null;
        }
    }

    private void unwatchNetwork() {
        if (networkCallback == null) {
            return;
        }
        final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            cm.unregisterNetworkCallback(networkCallback);
        } catch (RuntimeException e) {
            // Not registered
        }
        networkCallback = null;
    }

    private void notifyNetworkChanged() {
        control.execute(new Runnable() {
            public void run() {
                final TorClient c = client;
                if (c != null && status.state == State.RUNNING) {
                    Log.i(TAG, "The default network changed");
                    c.notifyNetworkChanged();
                }
            }
        });
    }

    /**
     * A new identity: new circuits for everything opened from now on.
     * The callback gets 0 if it took effect now, the milliseconds until it
     * does (Orchid allows one every 10 seconds), or -1 if Orchid is not
     * running.
     */
    void newIdentity(final Callback<Long> callback) {
        control.execute(new Runnable() {
            public void run() {
                final TorClient c = client;
                long result = -1;
                if (c != null && status.state == State.RUNNING) {
                    result = c.newIdentity();
                }
                post(callback, result);
            }
        });
    }

    /** Bytes sent and received, or null when no client runs.  Cheap; any thread. */
    TrafficInfo traffic() {
        final TorClient c = client;
        return c == null ? null : c.getTrafficInfo();
    }

    /** The circuits, on the main thread; an empty list when no client runs. */
    void circuits(final Callback<List<CircuitInfo>> callback) {
        query.execute(new Runnable() {
            public void run() {
                final TorClient c = client;
                List<CircuitInfo> result = Collections.emptyList();
                if (c != null) {
                    try {
                        result = c.getCircuits();
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Cannot list the circuits: " + e);
                    }
                }
                post(callback, result);
            }
        });
    }

    /** The entry guards, on the main thread; an empty list when no client runs. */
    void guards(final Callback<List<GuardInfo>> callback) {
        query.execute(new Runnable() {
            public void run() {
                final TorClient c = client;
                List<GuardInfo> result = Collections.emptyList();
                if (c != null) {
                    try {
                        result = c.getEntryGuards();
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Cannot list the entry guards: " + e);
                    }
                }
                post(callback, result);
            }
        });
    }

    /** Run a slow task off the main thread and hand its result to the main thread. */
    <T> void background(final java.util.concurrent.Callable<T> task, final Callback<T> callback) {
        query.execute(new Runnable() {
            public void run() {
                T result = null;
                try {
                    result = task.call();
                } catch (Exception e) {
                    Log.w(TAG, "Background task failed: " + e);
                }
                post(callback, result);
            }
        });
    }

    private <T> void post(final Callback<T> callback, final T result) {
        main.post(new Runnable() {
            public void run() {
                callback.onResult(result);
            }
        });
    }

    private void setStatus(final Status s) {
        status = s;
        main.post(new Runnable() {
            public void run() {
                // Only the latest status matters; skip ones already superseded.
                if (status != s) {
                    return;
                }
                for (Listener l : listeners) {
                    l.onStatusChanged(s);
                }
            }
        });
    }
}
