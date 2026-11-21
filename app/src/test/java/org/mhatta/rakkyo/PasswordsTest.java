package org.mhatta.rakkyo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PasswordsTest {
    @Test
    public void generated() {
        final String a = Passwords.generate();
        assertEquals(Passwords.LENGTH, a.length());
        assertTrue(Passwords.isPlausible(a));
        assertNotEquals(a, Passwords.generate());
        assertFalse(Passwords.isPlausible("short"));
        assertFalse(Passwords.isPlausible("O0O0O0O0O0O0O0O0O0O0O0O0"));
    }
}
