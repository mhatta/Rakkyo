package org.mhatta.rakkyo;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The user's settings.  They are read when Orchid starts, so that a change
 * takes effect at the next connection ("Changes apply from the next
 * connection" on the settings screen).
 *
 * The shared preferences live in the app's private storage and are kept out
 * of backups and device transfers (res/xml/data_extraction_rules.xml).
 */
final class Prefs {
    static final int DEFAULT_SOCKS_PORT = 9150;
    static final int DEFAULT_HTTP_PORT = 8118;
    /** Ports below 1024 cannot be bound by apps. */
    static final int MIN_PORT = 1024;
    static final int MAX_PORT = 65535;

    private static final String FILE = "settings";
    private static final String SOCKS_PORT = "socks_port";
    private static final String HTTP_ENABLED = "http_enabled";
    private static final String HTTP_PORT = "http_port";
    private static final String PASSWORD = "password";
    private static final String ISOLATE_DEST = "isolate_dest";
    private static final String USE_BRIDGES = "use_bridges";
    private static final String BRIDGES = "bridges";
    private static final String START_ON_BOOT = "start_on_boot";
    private static final String WANTS_RUNNING = "wants_running";
    private static final String ASKED_NOTIFICATIONS = "asked_notifications";

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** What Orchid is started with: one consistent copy of the settings. */
    static final class Snapshot {
        final int socksPort;
        /** 0 when the HTTP CONNECT port is off. */
        final int httpPort;
        /** null for no password. */
        final String password;
        final boolean isolateDest;
        final boolean useBridges;
        final String bridges;

        Snapshot(int socksPort, int httpPort, String password, boolean isolateDest, boolean useBridges, String bridges) {
            this.socksPort = socksPort;
            this.httpPort = httpPort;
            this.password = password;
            this.isolateDest = isolateDest;
            this.useBridges = useBridges;
            this.bridges = bridges;
        }
    }

    Snapshot snapshot() {
        return new Snapshot(getSocksPort(), isHttpEnabled() ? getHttpPort() : 0, getPassword(),
                isIsolateDest(), isUseBridges(), getBridges());
    }

    static boolean isValidPort(int port) {
        return port >= MIN_PORT && port <= MAX_PORT;
    }

    int getSocksPort() {
        return sp.getInt(SOCKS_PORT, DEFAULT_SOCKS_PORT);
    }

    void setSocksPort(int port) {
        sp.edit().putInt(SOCKS_PORT, port).apply();
    }

    boolean isHttpEnabled() {
        return sp.getBoolean(HTTP_ENABLED, true);
    }

    void setHttpEnabled(boolean enabled) {
        sp.edit().putBoolean(HTTP_ENABLED, enabled).apply();
    }

    int getHttpPort() {
        return sp.getInt(HTTP_PORT, DEFAULT_HTTP_PORT);
    }

    void setHttpPort(int port) {
        sp.edit().putInt(HTTP_PORT, port).apply();
    }

    /** The proxy password, or null when the proxy takes none (the default). */
    String getPassword() {
        return sp.getString(PASSWORD, null);
    }

    /** A new random password, which replaces the old one. */
    String usePassword() {
        final String password = Passwords.generate();
        sp.edit().putString(PASSWORD, password).apply();
        return password;
    }

    void clearPassword() {
        sp.edit().remove(PASSWORD).apply();
    }

    /** IsolateDestAddr; on by default, as in the mock-up. */
    boolean isIsolateDest() {
        return sp.getBoolean(ISOLATE_DEST, true);
    }

    void setIsolateDest(boolean value) {
        sp.edit().putBoolean(ISOLATE_DEST, value).apply();
    }

    boolean isUseBridges() {
        return sp.getBoolean(USE_BRIDGES, false);
    }

    void setUseBridges(boolean value) {
        sp.edit().putBoolean(USE_BRIDGES, value).apply();
    }

    String getBridges() {
        return sp.getString(BRIDGES, "");
    }

    void setBridges(String lines) {
        sp.edit().putString(BRIDGES, lines).apply();
    }

    boolean isStartOnBoot() {
        return sp.getBoolean(START_ON_BOOT, false);
    }

    void setStartOnBoot(boolean value) {
        sp.edit().putBoolean(START_ON_BOOT, value).apply();
    }

    /**
     * Whether the user last asked for the proxy to run: used when Android
     * restarts the service after killing the process.
     */
    boolean wantsRunning() {
        return sp.getBoolean(WANTS_RUNNING, false);
    }

    void setWantsRunning(boolean value) {
        sp.edit().putBoolean(WANTS_RUNNING, value).apply();
    }

    boolean hasAskedForNotifications() {
        return sp.getBoolean(ASKED_NOTIFICATIONS, false);
    }

    void setAskedForNotifications() {
        sp.edit().putBoolean(ASKED_NOTIFICATIONS, true).apply();
    }
}
