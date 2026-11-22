package org.mhatta.rakkyo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The profiles: one name and one random password for each app that uses
 * the proxy ("Firefox", "Messenger").  Orchid accepts any of the passwords
 * (TorConfig#setSocksPasswords), and since streams with different
 * credentials never share a circuit, each app has circuits of its own: an
 * app cannot ride on another's circuits, or learn from their timing which
 * hosts it went to.  With no profile, the proxy takes no password.
 *
 * <p>Stored as one line per profile, the name and the password separated
 * by a tab; names have no control characters, and passwords are
 * {@link Passwords} (letters and digits).
 */
final class Profiles {
    /** Enough for every proxy-aware app on a phone. */
    static final int MAX = 16;
    /** In characters; what fits on a settings row. */
    static final int MAX_NAME = 32;

    static final class Profile {
        final String name;
        final String password;

        Profile(String name, String password) {
            this.name = name;
            this.password = password;
        }
    }

    enum NameProblem { EMPTY, TOO_LONG, CONTROL, TAKEN }

    private Profiles() {
    }

    static List<Profile> parse(String stored) {
        final List<Profile> list = new ArrayList<Profile>();
        if (stored == null) {
            return list;
        }
        for (String line : stored.split("\n")) {
            final int tab = line.indexOf('\t');
            if (tab <= 0) {
                continue;
            }
            final String name = line.substring(0, tab);
            final String password = line.substring(tab + 1);
            if (checkName(name, list) == null && Passwords.isPlausible(password) && list.size() < MAX) {
                list.add(new Profile(name, password));
            }
        }
        return list;
    }

    static String format(List<Profile> profiles) {
        final StringBuilder sb = new StringBuilder();
        for (Profile p : profiles) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(p.name).append('\t').append(p.password);
        }
        return sb.toString();
    }

    /** What is wrong with a name for a new profile, or null if nothing. */
    static NameProblem checkName(String name, List<Profile> existing) {
        if (name == null || name.trim().isEmpty()) {
            return NameProblem.EMPTY;
        }
        if (name.codePointCount(0, name.length()) > MAX_NAME) {
            return NameProblem.TOO_LONG;
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) {
                return NameProblem.CONTROL;
            }
        }
        for (Profile p : existing) {
            if (p.name.equalsIgnoreCase(name)) {
                return NameProblem.TAKEN;
            }
        }
        return null;
    }

    /** A copy with a new profile at the end. */
    static List<Profile> add(List<Profile> profiles, String name, String password) {
        final List<Profile> list = new ArrayList<Profile>(profiles);
        list.add(new Profile(name, password));
        return list;
    }

    /** A copy without the profile of that name. */
    static List<Profile> remove(List<Profile> profiles, String name) {
        final List<Profile> list = new ArrayList<Profile>();
        for (Profile p : profiles) {
            if (!p.name.equals(name)) {
                list.add(p);
            }
        }
        return list;
    }

    /** A copy with a new password for the profile of that name. */
    static List<Profile> renew(List<Profile> profiles, String name, String password) {
        final List<Profile> list = new ArrayList<Profile>();
        for (Profile p : profiles) {
            list.add(p.name.equals(name) ? new Profile(name, password) : p);
        }
        return list;
    }

    static Profile find(List<Profile> profiles, String name) {
        for (Profile p : profiles) {
            if (p.name.equals(name)) {
                return p;
            }
        }
        return null;
    }

    /** For TorConfig#setSocksPasswords: name to password, in order. */
    static Map<String, String> passwords(List<Profile> profiles) {
        final Map<String, String> map = new LinkedHashMap<String, String>();
        for (Profile p : profiles) {
            map.put(p.name, p.password);
        }
        return Collections.unmodifiableMap(map);
    }
}
