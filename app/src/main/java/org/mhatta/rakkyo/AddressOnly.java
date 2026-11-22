package org.mhatta.rakkyo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.subgraph.orchid.ProxyStatistics;

/**
 * The requests that gave only an IP address, not a host name.  An app that
 * sends those has looked the name up itself, outside Tor, so that the
 * local network (and its DNS server) saw which host it was going to.
 * Orchid counts them by profile, which tells the user which app it is.
 */
final class AddressOnly {
    /** One profile's count; {@link ProxyStatistics#NO_PROFILE} for requests without a password. */
    static final class Entry {
        final String profile;
        final long count;

        Entry(String profile, long count) {
            this.profile = profile;
            this.count = count;
        }
    }

    final long total;
    final long refused;
    /** The profiles with any, most first. */
    final List<Entry> byProfile;

    private AddressOnly(long total, long refused, List<Entry> byProfile) {
        this.total = total;
        this.refused = refused;
        this.byProfile = byProfile;
    }

    static AddressOnly of(ProxyStatistics stats) {
        if (stats == null) {
            return new AddressOnly(0, 0, Collections.<Entry>emptyList());
        }
        final List<Entry> list = new ArrayList<Entry>();
        for (Map.Entry<String, ProxyStatistics.Counts> e : stats.getByProfile().entrySet()) {
            final long n = e.getValue().getAddressOnlyRequests();
            if (n > 0) {
                list.add(new Entry(e.getKey(), n));
            }
        }
        Collections.sort(list, new Comparator<Entry>() {
            public int compare(Entry a, Entry b) {
                return a.count == b.count ? a.profile.compareTo(b.profile) : (a.count > b.count ? -1 : 1);
            }
        });
        final ProxyStatistics.Counts total = stats.getTotal();
        return new AddressOnly(total.getAddressOnlyRequests(), total.getRefusedUnsafeRequests(),
                Collections.unmodifiableList(list));
    }
}
