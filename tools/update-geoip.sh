#!/bin/sh
#
# Update the GeoIP database that Rakkyo bundles: Tor's "geoip" file (IPv4
# ranges to country codes), where Orchid looks for it on the classpath,
# app/src/main/resources/com/subgraph/orchid/geoip/geoip.  It stays plain
# text in the repository, so that updates can be reviewed as diffs; the
# APK compresses it.
#
# Tor makes the file from the IPFire Location database
# (https://location.ipfire.org/, CC BY-SA 4.0).
#
# Usage:
#   tools/update-geoip.sh                       download Tor's current file
#   tools/update-geoip.sh /usr/share/tor/geoip  use a local copy (Debian: tor-geoipdb)

set -eu

URL=https://gitlab.torproject.org/tpo/core/tor/-/raw/main/src/config/geoip
OUT=app/src/main/resources/com/subgraph/orchid/geoip/geoip

cd "$(dirname "$0")/.."
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

if [ $# -ge 1 ]; then
    cp "$1" "$tmp"
else
    curl -fsSL -o "$tmp" "$URL"
fi

# Sanity check: a few hundred thousand "LOW,HIGH,CC" lines
lines=$(grep -c '^[0-9][0-9]*,[0-9][0-9]*,..$' "$tmp" || true)
if [ "$lines" -lt 100000 ]; then
    echo "This does not look like Tor's geoip file ($lines ranges)" >&2
    exit 1
fi

grep -m1 '^# Generated:' "$tmp" || true
mkdir -p "$(dirname "$OUT")"
mv "$tmp" "$OUT"
chmod 644 "$OUT"
trap - EXIT
echo "Wrote $OUT ($lines ranges)"
