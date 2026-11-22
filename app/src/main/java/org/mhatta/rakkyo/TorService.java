package org.mhatta.rakkyo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

/**
 * Keeps the process alive while the proxy runs or is paused (holding its
 * ports), as a foreground service with an ongoing notification.  Orchid
 * itself lives in {@link TorController}.
 *
 * <p>The service type is "specialUse" (Android 14+): a local proxy fits none
 * of the other types, and "dataSync" would be stopped after six hours on
 * Android 15.  Google Play asks for a justification of it; see the
 * PROPERTY_SPECIAL_USE_FGS_SUBTYPE in the manifest.
 */
public final class TorService extends Service implements TorController.Listener {
    static final String ACTION_START = "org.mhatta.rakkyo.action.START";
    static final String ACTION_STOP = "org.mhatta.rakkyo.action.STOP";
    static final String ACTION_NEW_IDENTITY = "org.mhatta.rakkyo.action.NEW_IDENTITY";
    static final String ACTION_RESTART = "org.mhatta.rakkyo.action.RESTART";
    static final String ACTION_PAUSE = "org.mhatta.rakkyo.action.PAUSE";

    private TorController controller;
    private boolean inForeground;
    /**
     * The status when the last start was asked for.  A STOPPED or FAILED
     * status that is not newer is from before it, and does not end the
     * service.
     */
    private long startedAt = Long.MIN_VALUE;

    /** Start the proxy (from the UI, or at boot). */
    static void start(Context context) {
        send(context, ACTION_START);
    }

    /** What the Disconnect button does: pause, or stop if the user turned "Keep the ports" off. */
    static void disconnect(Context context) {
        if (new Prefs(context).isKeepPorts()) {
            pause(context);
        } else {
            stop(context);
        }
    }

    /** Keep the ports and refuse connections ({@link TorController#pause}). */
    static void pause(Context context) {
        send(context, ACTION_PAUSE);
    }

    /** Stop the proxy and close its ports. */
    static void stop(Context context) {
        final Prefs prefs = new Prefs(context);
        prefs.setWantsRunning(false);
        prefs.setWantsPaused(false);
        // Stop Orchid even if the service is not running (it failed to start).
        final TorController controller = TorController.get(context);
        final boolean wasForeground = controller.status().isActive()
                || controller.status().state == TorController.State.PAUSED;
        controller.stop();
        if (wasForeground) {
            send(context, ACTION_STOP);
        }
    }

    /** Stop and start again with the current settings. */
    static void restart(Context context) {
        send(context, ACTION_RESTART);
    }

    private static void send(Context context, String action) {
        final Intent intent = new Intent(context, TorService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        controller = TorController.get(this);
        Notifications.createChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Every start must call startForeground() soon, even a STOP: Android
        // kills an app whose startForegroundService() is not followed by it.
        goForeground(controller.status());

        // A null intent: Android restarted the service after killing the
        // process.  Carry on only if the user had left the proxy running.
        // The wish is recorded here, so that the notification's actions,
        // which come straight to the service, record it too.
        final Prefs prefs = new Prefs(this);
        final String action = intent == null ? null : intent.getAction();
        if (action == null || ACTION_START.equals(action) || ACTION_RESTART.equals(action)
                || ACTION_PAUSE.equals(action)) {
            startedAt = controller.status().seq;
        }
        if (action == null) {
            if (prefs.wantsRunning()) {
                controller.start();
            } else if (prefs.wantsPaused() && prefs.isKeepPorts()) {
                controller.pause();
            } else {
                finish();
                return START_NOT_STICKY;
            }
        } else if (ACTION_START.equals(action) || ACTION_RESTART.equals(action)) {
            prefs.setWantsRunning(true);
            prefs.setWantsPaused(false);
            if (ACTION_START.equals(action)) {
                controller.start();
            } else {
                controller.restart();
            }
        } else if (ACTION_PAUSE.equals(action)) {
            prefs.setWantsRunning(false);
            prefs.setWantsPaused(true);
            controller.pause();
        } else if (ACTION_STOP.equals(action)) {
            prefs.setWantsRunning(false);
            prefs.setWantsPaused(false);
            startedAt = Long.MIN_VALUE;
            controller.stop();
        } else if (ACTION_NEW_IDENTITY.equals(action)) {
            newIdentity();
        }
        controller.removeListener(this);
        controller.addListener(this);
        return START_STICKY;
    }

    private void newIdentity() {
        controller.newIdentity(new TorController.Callback<Long>() {
            public void onResult(Long delay) {
                Toast.makeText(TorService.this, NewIdentity.message(TorService.this, delay), Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onStatusChanged(TorController.Status status) {
        final boolean terminal = status.state == TorController.State.STOPPED
                || status.state == TorController.State.FAILED;
        if (terminal && status.seq <= startedAt) {
            return;
        }
        switch (status.state) {
        case STARTING:
        case RUNNING:
        case PAUSED:
            goForeground(status);
            break;
        case FAILED:
            // Tell the user even if no screen of the app is open (start at boot).
            final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(Notifications.ID_FAILED, Notifications.failed(this, status));
            finish();
            break;
        case STOPPED:
            finish();
            break;
        case STOPPING:
            break;
        }
    }

    private void goForeground(TorController.Status status) {
        final Notification n = Notifications.ongoing(this, status);
        if (inForeground) {
            final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(Notifications.ID_ONGOING, n);
            return;
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifications.ID_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(Notifications.ID_ONGOING, n);
        }
        inForeground = true;
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(Notifications.ID_FAILED);
    }

    private void finish() {
        controller.removeListener(this);
        stopForeground(STOP_FOREGROUND_REMOVE);
        inForeground = false;
        stopSelf();
    }

    @Override
    public void onDestroy() {
        controller.removeListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
