package org.mhatta.rakkyo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The GeoIP database bundled with the app: Tor's "geoip" file, made from
 * the IPFire Location database (CC BY-SA 4.0), at
 * app/src/main/resources/com/subgraph/orchid/geoip/geoip.  Orchid's
 * CountryCodeService finds it there on the classpath by itself; this class
 * only reads the date in its header, for the About section.
 *
 * <p>Update it with tools/update-geoip.sh.
 */
final class GeoIp {
    static final String RESOURCE = "/com/subgraph/orchid/geoip/geoip";

    private GeoIp() {
    }

    /** "25 Jun 2026" in the user's language, or null if the database is missing. */
    static String generated() {
        final Date date = generatedDate(GeoIp.class.getResourceAsStream(RESOURCE));
        if (date == null) {
            return null;
        }
        final DateFormat f = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(date);
    }

    /**
     * The "# Generated: Thu, 25 Jun 2026 04:33:59 GMT" line of the header
     * that Tor's geoip-db-tool copies from the IPFire export.  Null if the
     * stream is null or has no such line.  Reads only the header, and
     * closes the stream.
     */
    static Date generatedDate(InputStream in) {
        if (in == null) {
            return null;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, Charset.forName("US-ASCII")))) {
            String line;
            while ((line = r.readLine()) != null && line.startsWith("#")) {
                final String text = line.substring(1).trim();
                if (text.startsWith("Generated:")) {
                    final SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ROOT);
                    try {
                        return f.parse(text.substring("Generated:".length()).trim());
                    } catch (ParseException e) {
                        return null;
                    }
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }
}
