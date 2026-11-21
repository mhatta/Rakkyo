package org.mhatta.rakkyo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the bridge lines the user enters in the settings.
 *
 * Orchid connects to plain ("vanilla") bridges only: an IPv4 address, a
 * port and optionally the bridge's RSA fingerprint, one per line, as in a
 * torrc "Bridge" line:
 *
 * <pre>
 * 192.0.2.1:443 0123456789ABCDEF0123456789ABCDEF01234567
 * Bridge 198.51.100.7:9001
 * </pre>
 *
 * Lines of pluggable transports (obfs4, snowflake, webtunnel, meek) are
 * recognized so that the user is told why they cannot be used, rather than
 * being told that the line is malformed.  Blank lines and lines starting
 * with '#' are ignored.
 *
 * <p>Pure Java so that it can be tested on the JVM.
 */
final class BridgeLines {
    /** Why a line was refused. */
    enum Problem {
        /** Not of the form ADDRESS:PORT [FINGERPRINT]. */
        MALFORMED,
        /** A pluggable transport line; Orchid does not support transports yet. */
        TRANSPORT,
        /** An IPv6 address; Orchid connects over IPv4 only. */
        IPV6
    }

    static final class Bridge {
        final String address;
        final int port;
        /** 40 upper-case hex digits, or null. */
        final String fingerprint;

        Bridge(String address, int port, String fingerprint) {
            this.address = address;
            this.port = port;
            this.fingerprint = fingerprint;
        }

        @Override
        public String toString() {
            return address + ":" + port + (fingerprint == null ? "" : " " + fingerprint);
        }
    }

    static final class Result {
        final List<Bridge> bridges;
        /** The first bad line (1-based), or 0 if every line is good. */
        final int badLine;
        final Problem problem;

        Result(List<Bridge> bridges, int badLine, Problem problem) {
            this.bridges = Collections.unmodifiableList(bridges);
            this.badLine = badLine;
            this.problem = problem;
        }

        boolean isValid() {
            return badLine == 0;
        }
    }

    private static final Pattern LINE = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{1,5})(?:\\s+([0-9A-Fa-f]{40}|(?:[0-9A-Fa-f]{4}\\s){9}[0-9A-Fa-f]{4}))?");
    private static final Pattern IPV6 = Pattern.compile("\\[[0-9A-Fa-f:.]+\\]:\\d{1,5}.*");
    private static final String[] TRANSPORTS = {
        "obfs2", "obfs3", "obfs4", "scramblesuit", "fte", "meek", "meek_lite", "snowflake", "webtunnel",
    };

    private BridgeLines() {
    }

    static Result parse(String text) {
        final List<Bridge> bridges = new ArrayList<Bridge>();
        if (text == null) {
            return new Result(bridges, 0, null);
        }
        final String[] lines = text.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.regionMatches(true, 0, "Bridge ", 0, 7)) {
                line = line.substring(7).trim();
            }
            final String first = line.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
            for (String transport : TRANSPORTS) {
                if (first.equals(transport)) {
                    return new Result(bridges, i + 1, Problem.TRANSPORT);
                }
            }
            if (IPV6.matcher(line).matches()) {
                return new Result(bridges, i + 1, Problem.IPV6);
            }
            final Bridge bridge = parseLine(line);
            if (bridge == null) {
                return new Result(bridges, i + 1, Problem.MALFORMED);
            }
            bridges.add(bridge);
        }
        return new Result(bridges, 0, null);
    }

    private static Bridge parseLine(String line) {
        final Matcher m = LINE.matcher(line);
        if (!m.matches()) {
            return null;
        }
        for (String octet : m.group(1).split("\\.")) {
            if (Integer.parseInt(octet) > 255) {
                return null;
            }
        }
        final int port = Integer.parseInt(m.group(2));
        if (port < 1 || port > 65535) {
            return null;
        }
        final String fp = m.group(3) == null ? null : m.group(3).replace(" ", "").toUpperCase(Locale.ROOT);
        return new Bridge(m.group(1), port, fp);
    }
}
