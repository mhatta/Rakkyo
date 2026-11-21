package org.mhatta.rakkyo;

import java.util.Locale;

/** Number formatting that does not depend on Android, for the JVM tests. */
final class Formats {
    private Formats() {
    }

    /**
     * A byte count for people: "0 B", "999 B", "1.2 kB", "15 MB", "3.4 GB".
     * Decimal units, as Android's own data usage screen uses; one decimal
     * below 10, none above.
     */
    static String bytes(long n, Locale locale) {
        if (n < 1000) {
            return n + " B";
        }
        final String[] units = { "kB", "MB", "GB", "TB" };
        double v = n;
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        if (v >= 999.5 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        final String number = v < 9.95 ? String.format(locale, "%.1f", v) : String.format(locale, "%.0f", v);
        return number + " " + units[u];
    }

    /** "1 min", used with the circuit age in minutes; seconds below a minute are 0. */
    static int minutes(int seconds) {
        return Math.max(0, seconds) / 60;
    }

    /**
     * A fingerprint as four groups of four hex digits at each end
     * ("ABCD EF01 … 89AB CDEF"), short enough for a list but enough to compare
     * with a relay search site.
     */
    static String shortFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.length() < 16) {
            return fingerprint == null ? "" : fingerprint;
        }
        final String f = fingerprint.toUpperCase(Locale.ROOT);
        return f.substring(0, 4) + " " + f.substring(4, 8) + " … "
                + f.substring(f.length() - 8, f.length() - 4) + " " + f.substring(f.length() - 4);
    }

    /** The whole fingerprint in groups of four: "ABCD EF01 2345 …", for comparing with a relay search. */
    static String groupedFingerprint(String fingerprint) {
        if (fingerprint == null) {
            return "";
        }
        final String f = fingerprint.toUpperCase(Locale.ROOT);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < f.length(); i += 4) {
            if (i > 0) {
                sb.append(i == 20 ? '\n' : ' ');
            }
            sb.append(f, i, Math.min(f.length(), i + 4));
        }
        return sb.toString();
    }
}
