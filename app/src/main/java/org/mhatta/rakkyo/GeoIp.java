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

    /**
     * "25 Jun 2026" in the user's language; the header text as it is if it
     * cannot be parsed, "?" if there is none; null only if the database is
     * missing.
     */
    static String generated() {
        final InputStream in = GeoIp.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            return null;
        }
        final String header = generatedHeader(in);
        final Date date = parseGenerated(header);
        if (date == null) {
            return header == null ? "?" : header;
        }
        final DateFormat f = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(date);
    }

    /** The date of the database, or null if the stream is null or the header has none. */
    static Date generatedDate(InputStream in) {
        return parseGenerated(generatedHeader(in));
    }

    /**
     * The text of the "# Generated: Thu, 25 Jun 2026 04:33:59 GMT" line of
     * the header that Tor's geoip-db-tool copies from the IPFire export, or
     * null.  Reads only the header, and closes the stream.
     */
    static String generatedHeader(InputStream in) {
        if (in == null) {
            return null;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, Charset.forName("US-ASCII")))) {
            String line;
            while ((line = r.readLine()) != null && line.startsWith("#")) {
                final String text = line.substring(1).trim();
                if (text.startsWith("Generated:")) {
                    return text.substring("Generated:".length()).trim();
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /*
     * The header is an RFC 1123 date in English.  Locale.US, not
     * Locale.ROOT: on Android (ICU) the root locale names the months "M01"
     * to "M12", so "Jun" does not parse there, though it does on the JVM.
     */
    static Date parseGenerated(String text) {
        if (text == null) {
            return null;
        }
        final SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        try {
            return f.parse(text);
        } catch (ParseException e) {
            return null;
        }
    }
}
