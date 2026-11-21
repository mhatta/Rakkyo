package org.mhatta.rakkyo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BridgeLinesTest {
    @Test
    public void plainBridges() {
        final BridgeLines.Result r = BridgeLines.parse(
                "# comment\n\n192.0.2.1:443 0123456789abcdef0123456789ABCDEF01234567\r\nBridge 198.51.100.7:9001\n");
        assertTrue(r.isValid());
        assertEquals(2, r.bridges.size());
        assertEquals("192.0.2.1", r.bridges.get(0).address);
        assertEquals(443, r.bridges.get(0).port);
        assertEquals("0123456789ABCDEF0123456789ABCDEF01234567", r.bridges.get(0).fingerprint);
        assertNull(r.bridges.get(1).fingerprint);
    }

    @Test
    public void spacedFingerprint() {
        final BridgeLines.Result r = BridgeLines.parse("192.0.2.1:443 0123 4567 89AB CDEF 0123 4567 89AB CDEF 0123 4567");
        assertTrue(r.isValid());
        assertEquals("0123456789ABCDEF0123456789ABCDEF01234567", r.bridges.get(0).fingerprint);
    }

    @Test
    public void transportsAreNamed() {
        final BridgeLines.Result r = BridgeLines.parse("192.0.2.1:443\nobfs4 192.0.2.2:80 ABCD cert=x iat-mode=0");
        assertEquals(2, r.badLine);
        assertEquals(BridgeLines.Problem.TRANSPORT, r.problem);
        assertEquals(BridgeLines.Problem.TRANSPORT, BridgeLines.parse("Bridge snowflake 192.0.2.3:80").problem);
        assertEquals(BridgeLines.Problem.TRANSPORT, BridgeLines.parse("webtunnel [2001:db8::1]:443 url=https://x").problem);
    }

    @Test
    public void ipv6() {
        assertEquals(BridgeLines.Problem.IPV6, BridgeLines.parse("[2001:db8::1]:443").problem);
    }

    @Test
    public void malformed() {
        assertEquals(BridgeLines.Problem.MALFORMED, BridgeLines.parse("256.0.0.1:443").problem);
        assertEquals(BridgeLines.Problem.MALFORMED, BridgeLines.parse("192.0.2.1:0").problem);
        assertEquals(BridgeLines.Problem.MALFORMED, BridgeLines.parse("192.0.2.1:65536").problem);
        assertEquals(BridgeLines.Problem.MALFORMED, BridgeLines.parse("192.0.2.1").problem);
        assertEquals(BridgeLines.Problem.MALFORMED, BridgeLines.parse("192.0.2.1:443 ABCD").problem);
        assertEquals(BridgeLines.Problem.MALFORMED, BridgeLines.parse("example.org:443").problem);
    }

    @Test
    public void empty() {
        assertTrue(BridgeLines.parse(null).isValid());
        assertTrue(BridgeLines.parse("").bridges.isEmpty());
    }
}
