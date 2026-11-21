# Rakkyo

Rakkyo is an Android app that runs a local proxy through the Tor™ network.
Other apps on the device connect to it with SOCKS5 or HTTP CONNECT.
It is built on [Orchid](https://github.com/mhatta/Orchid), a Tor client
in pure Java: no native code, no root, and no VPN slot.

*Rakkyo* (らっきょう) is a Japanese bulb that is all layers: peel one off
and there is another underneath, like onion routing.

## Features

- SOCKS5 on `127.0.0.1:9150` and HTTP CONNECT on `127.0.0.1:8118` (both configurable)
- Optional random password (SOCKS5 user name/password, `Proxy-Authorization`)
- Circuits separated by destination host (Tor's IsolateDestAddr), on by default
- New identity (at most once every 10 seconds, as in Tor)
- The circuits in use, with the country of each relay; entry guards on a
  screen that cannot be captured in screenshots
- Country codes from a GeoIP database bundled with the app (see below)
- Dormant mode after an hour without use; wakes at the next connection
- Plain bridges (no pluggable transports yet)
- Start at boot; a foreground service keeps the proxy running
- English and Japanese

## Building

Requirements: JDK 17 or later, and the Android SDK (Android Studio, or
`ANDROID_HOME` pointing at the command line tools).

```sh
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # unit tests on the JVM
./gradlew lintDebug                # also fails on a missing translation
```

Rakkyo depends on `org.mhatta:orchid:2.8.0` from Maven Central.  To build
against a local checkout of Orchid instead (to change both at once):

```sh
./gradlew -Porchid.dir=../Orchid assembleDebug
```

## GeoIP database

`app/src/main/resources/com/subgraph/orchid/geoip/geoip` is Tor's
`geoip` file, which Tor makes from the
[IPFire Location database](https://location.ipfire.org/)
(CC BY-SA 4.0).  Orchid's `CountryCodeService` finds it on the classpath
by itself.  It is used only on the device, to show the countries of relays.

To update it:

```sh
tools/update-geoip.sh                       # download Tor's current file
tools/update-geoip.sh /usr/share/tor/geoip  # or use Debian's tor-geoipdb
```

## Translations

`app/src/main/res/values/strings.xml` (English) is the source;
`values-ja/strings.xml` is Japanese.  To add a language, copy
`values/strings.xml` to `values-<language>/strings.xml` and translate it;
leave out the strings marked `translatable="false"`.  Lint fails the build
when a string is not translated.

## Privacy

- The proxies listen on the loopback address only.
- Nothing about relays, guards or traffic is logged, put in a notification
  or exported to other apps; the service is not exported.
- The app's data, entry guards included, is excluded from backups and
  device transfers.
- Rakkyo makes no network connections of its own besides Tor's.

## License

BSD-3-Clause; see [LICENSE](LICENSE).  Orchid is BSD-3-Clause, the GeoIP
database CC BY-SA 4.0, and the icons are from Feather (MIT); the app shows
these notices under Settings › Licenses.

"Tor" is a trademark of The Tor Project; all rights reserved.  This
product is not endorsed or sponsored by, or affiliated with, the Tor
Project.  Rakkyo contains no Tor software: it uses Orchid, an independent
implementation of the Tor protocol.  The official Tor software is at
<https://www.torproject.org/>.
