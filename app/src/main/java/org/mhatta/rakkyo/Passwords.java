package org.mhatta.rakkyo;

import java.security.SecureRandom;

/**
 * Random proxy passwords.  Every app on the device can connect to a port
 * on 127.0.0.1; a random password keeps out the apps that were not given it.
 */
final class Passwords {
    /** Letters and digits that cannot be mistaken for one another when typed. */
    private static final char[] ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    /** 24 characters of 55: about 138 bits. */
    static final int LENGTH = 24;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {
    }

    static String generate() {
        final char[] c = new char[LENGTH];
        for (int i = 0; i < c.length; i++) {
            c[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(c);
    }

    static boolean isPlausible(String password) {
        if (password == null || password.length() != LENGTH) {
            return false;
        }
        for (char ch : password.toCharArray()) {
            if (new String(ALPHABET).indexOf(ch) < 0) {
                return false;
            }
        }
        return true;
    }
}
