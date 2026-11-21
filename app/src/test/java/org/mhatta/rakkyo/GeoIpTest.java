package org.mhatta.rakkyo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;

import com.subgraph.orchid.data.IPv4Address;
import com.subgraph.orchid.geoip.CountryCodeService;

/** The bundled database is where Orchid looks for it, and is usable. */
public class GeoIpTest {
    @Test
    public void headerDate() {
        final Date date = GeoIp.generatedDate(GeoIp.class.getResourceAsStream(GeoIp.RESOURCE));
        assertNotNull("geoip is missing or has no Generated: line", date);
        final SimpleDateFormat f = new SimpleDateFormat("yyyy", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        assertTrue(Integer.parseInt(f.format(date)) >= 2026);
    }

    @Test
    public void orchidFindsTheBundledDatabase() {
        final CountryCodeService s = CountryCodeService.getInstance();
        assertTrue("Orchid found no geoip database", s.isAvailable());
        assertEquals("classpath:" + GeoIp.RESOURCE, s.getSource());
        assertTrue(s.getRangeCount() > 100000);
        // Addresses whose country is stable: Google Public DNS, a Japanese university network.
        assertEquals("US", s.getCountryCodeForAddress(IPv4Address.createFromString("8.8.8.8")));
        assertEquals("JP", s.getCountryCodeForAddress(IPv4Address.createFromString("133.11.0.1")));
        assertEquals(CountryCodeService.UNKNOWN, s.getCountryCodeForAddress(IPv4Address.createFromString("10.0.0.1")));
    }
}
