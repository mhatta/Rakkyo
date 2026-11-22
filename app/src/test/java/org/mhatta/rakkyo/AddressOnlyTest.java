package org.mhatta.rakkyo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.subgraph.orchid.ProxyStatistics;

public class AddressOnlyTest {
    @Test
    public void none() {
        assertEquals(0, AddressOnly.of(null).total);
        assertTrue(AddressOnly.of(ProxyStatistics.empty()).byProfile.isEmpty());
    }

    @Test
    public void byProfileMostFirst() {
        final Map<String, ProxyStatistics.Counts> m = new LinkedHashMap<String, ProxyStatistics.Counts>();
        m.put("Firefox", new ProxyStatistics.Counts(10, 1, 0));
        m.put(ProxyStatistics.NO_PROFILE, new ProxyStatistics.Counts(5, 4, 4));
        m.put("Chat", new ProxyStatistics.Counts(7, 0, 0));
        final AddressOnly a = AddressOnly.of(new ProxyStatistics(m));
        assertEquals(5, a.total);
        assertEquals(4, a.refused);
        assertEquals(2, a.byProfile.size());
        assertEquals(ProxyStatistics.NO_PROFILE, a.byProfile.get(0).profile);
        assertEquals(4, a.byProfile.get(0).count);
        assertEquals("Firefox", a.byProfile.get(1).profile);
    }
}
