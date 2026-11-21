package org.mhatta.rakkyo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;

import com.subgraph.orchid.data.IPv4Address;
import com.subgraph.orchid.geoip.CountryCodeService;

/**
 * The bundled database is complete, and Orchid can use it.
 *
 * <p>On the JVM, Orchid prefers a geoip file installed on the machine
 * (/usr/share/tor/geoip of Debian's tor-geoipdb, and others) to the bundled
 * one, so which one it loaded depends on where the tests run.  On Android
 * there is no such file and the bundled one is always used.
 */
public class GeoIpTest {
    /** Where Orchid looks before the classpath (CountryCodeService.DEFAULT_LOCATIONS). */
    private static final String[] FILES_BEFORE_CLASSPATH = {
        "data/geoip", "/usr/share/tor/geoip", "/usr/local/share/tor/geoip", "/opt/homebrew/share/tor/geoip",
    };

    @Test
    public void headerDate() {
        final Date date = GeoIp.generatedDate(GeoIp.class.getResourceAsStream(GeoIp.RESOURCE));
        assertNotNull("geoip is missing or has no Generated: line", date);
        final SimpleDateFormat f = new SimpleDateFormat("yyyy", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        assertTrue(Integer.parseInt(f.format(date)) >= 2026);
    }

    @Test
    public void parsesTheHeaderDateWhateverTheDefaultLocale() {
        final Locale saved = Locale.getDefault();
        try {
            for (Locale l : new Locale[] { Locale.JAPAN, Locale.GERMANY, Locale.ROOT }) {
                Locale.setDefault(l);
                final Date d = GeoIp.parseGenerated("Thu, 25 Jun 2026 04:33:59 GMT");
                assertNotNull("Not parsed with the default locale " + l, d);
                assertEquals(1782362039000L, d.getTime());
            }
        } finally {
            Locale.setDefault(saved);
        }
        assertEquals(null, GeoIp.parseGenerated("not a date"));
    }

    @Test
    public void bundledDatabaseIsComplete() throws IOException {
        final InputStream in = GeoIp.class.getResourceAsStream(GeoIp.RESOURCE);
        assertNotNull("geoip is not on the classpath", in);
        int ranges = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, Charset.forName("US-ASCII")))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }
                assertTrue("Not a range line: " + line, line.matches("\\d+,\\d+,[A-Z?]{2}"));
                ranges++;
            }
        }
        assertTrue("Only " + ranges + " ranges", ranges > 100000);
    }

    @Test
    public void orchidLooksUpCountries() {
        final CountryCodeService s = CountryCodeService.getInstance();
        assertTrue("Orchid found no geoip database", s.isAvailable());
        if (!machineHasGeoipFile()) {
            assertEquals("classpath:" + GeoIp.RESOURCE, s.getSource());
        }
        assertTrue(s.getRangeCount() > 100000);
        // Addresses whose country is stable: Google Public DNS, a Japanese university network.
        assertEquals("US", s.getCountryCodeForAddress(IPv4Address.createFromString("8.8.8.8")));
        assertEquals("JP", s.getCountryCodeForAddress(IPv4Address.createFromString("133.11.0.1")));
        assertEquals(CountryCodeService.UNKNOWN, s.getCountryCodeForAddress(IPv4Address.createFromString("10.0.0.1")));
    }

    private static boolean machineHasGeoipFile() {
        final String property = System.getProperty(CountryCodeService.GEOIP_FILE_PROPERTY);
        if (property != null && !property.isEmpty()) {
            return true;
        }
        for (String path : FILES_BEFORE_CLASSPATH) {
            if (new File(path).exists() || new File(path + ".gz").exists()) {
                return true;
            }
        }
        return false;
    }
}
