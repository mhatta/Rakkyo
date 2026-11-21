package org.mhatta.rakkyo;

import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.Test;

public class FormatsTest {
    @Test
    public void bytes() {
        assertEquals("0 B", Formats.bytes(0, Locale.ROOT));
        assertEquals("999 B", Formats.bytes(999, Locale.ROOT));
        assertEquals("1.0 kB", Formats.bytes(1000, Locale.ROOT));
        assertEquals("1.2 kB", Formats.bytes(1234, Locale.ROOT));
        assertEquals("15 MB", Formats.bytes(15_000_000, Locale.ROOT));
        assertEquals("1.0 MB", Formats.bytes(999_999, Locale.ROOT));
        assertEquals("3.4 GB", Formats.bytes(3_400_000_000L, Locale.ROOT));
        assertEquals("1,2 kB", Formats.bytes(1234, Locale.GERMANY));
    }

    @Test
    public void fingerprints() {
        final String fp = "0123456789abcdef0123456789ABCDEF01234567";
        assertEquals("0123 4567 … 0123 4567", Formats.shortFingerprint(fp));
        assertEquals("0123 4567 89AB CDEF 0123\n4567 89AB CDEF 0123 4567", Formats.groupedFingerprint(fp));
    }

    @Test
    public void minutes() {
        assertEquals(0, Formats.minutes(59));
        assertEquals(4, Formats.minutes(240));
        assertEquals(0, Formats.minutes(-1));
    }
}
