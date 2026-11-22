# Rakkyo
[![Android](https://github.com/mhatta/Rakkyo/actions/workflows/android.yml/badge.svg)](https://github.com/mhatta/Rakkyo/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/mhatta/Rakkyo)](https://github.com/mhatta/Rakkyo/releases/latest)
[![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84)](#)
[![Orchid](https://img.shields.io/maven-central/v/org.mhatta/orchid?label=Orchid)](https://github.com/mhatta/Orchid)
[![License](https://img.shields.io/github/license/mhatta/Rakkyo)](LICENSE)

Rakkyo is an Android app that runs a local proxy through the Tor™ network.
Other apps on the device connect to it with SOCKS5 or HTTP CONNECT.
It is built on [Orchid](https://github.com/mhatta/Orchid), a Tor client
in pure Java: no native code, no root, and no VPN slot.

*Rakkyo* (らっきょう) is a Japanese bulb that is all layers: peel one off
and there is another underneath, like onion routing.

## Features

- SOCKS5 on `127.0.0.1:9150`, and optionally HTTP CONNECT on
  `127.0.0.1:8118` (for the Wi-Fi proxy setting); both configurable
- A random password for each app (SOCKS5 user name/password,
  `Proxy-Authorization`), required by default; each app has circuits of
  its own
- Circuits separated by destination host (Tor's IsolateDestAddr), on by default
- While disconnected, the ports are kept and refuse connections, so that
  no other app can take them
- Warns when an app looks host names up outside Tor (gives only an IP
  address); can refuse such connections (SafeSocks)
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

Rakkyo depends on `org.mhatta:orchid:2.9.0` from Maven Central.  To build
against a local checkout of Orchid instead (to change both at once):

```sh
./gradlew -Porchid.dir=../Orchid assembleDebug
```

### Dependency verification

Gradle checks every dependency and plugin it downloads against
`gradle/verification-metadata.xml`, and fails the build on a mismatch.
Orchid must be signed by its maintainer's key (any version); Google's
artifacts by Google's key; the rest have SHA-256 checksums.  The public
keys are in `gradle/verification-keyring.keys`, so that no key server is
needed.  Android Studio's sources and javadoc jars are not checked.

After changing a dependency, the Android Gradle plugin or Gradle itself
(also on a Dependabot pull request, whose build fails until this is done):

```sh
tools/update-verification-metadata.sh
git diff -- gradle/verification-metadata.xml gradle/verification-keyring.keys
```

The script runs the tasks that CI and releases run, as CI does: with an
empty Gradle home and every task run again (`--rerun-tasks`), so that
nothing is missed because it was in the cache or a task was up to date.
It lists the keys and components that are new, and checks that Orchid is
still trusted by its key alone.  It downloads everything, which takes a
few minutes.

Gradle does not record aapt2, which the Android Gradle plugin fetches in
a configuration of its own: the script adds its checksums itself, for
the jars of Linux, Windows and macOS (so that Android Studio works on
each), from Google's Maven repository, after checking the Linux one
against the copy the build downloaded.

Gradle trusts whatever it downloads at that moment, so read the list: a
new key or group should be one that the change explains.
When a build fails verification, `build/reports/dependency-verification/`
says which artifact did not match (CI keeps it as an artifact).
Do not work around a failure with `--dependency-verification=off` or
`lenient`.

## Verifying a release

Release APKs on GitHub are signed by the developer, with APK Signature
Scheme v2 and v3.  The SHA-256 of the signing certificate is:

```
dce02131fc9e7ba250380fbd477211d169fe756f22740aa6e8f43a2ee7dbd2db
```

```sh
apksigner verify --print-certs Rakkyo-1.1.0.apk   # "certificate SHA-256 digest"
sha256sum -c SHA256SUMS && gpg --verify SHA256SUMS.asc SHA256SUMS
```

Builds are meant to be reproducible: the unsigned APK built from a
release tag with `./gradlew assembleRelease` should be identical to the
release APK without its signature.  The release notes give the JDK and
build-tools used, and the SHA-256 of the unsigned APK.

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
  or exported to other apps; the service is not exported.  Release builds
  write nothing of Orchid's to logcat.
- The notifications are not shown on a secure lock screen, since they say
  that Tor is in use.
- The bridge lines and the guard details are kept out of screenshots and
  the recent apps screen; the keyboard is asked not to learn the bridge
  lines and ports, and a copied password is taken off the clipboard after
  a minute.
- Every app on the device can connect to the proxy.  By default it takes
  only the passwords of the apps set up in Settings › Apps, one per app,
  so that other apps can neither use it nor share an app's circuits; with
  the password turned off (for the Wi-Fi proxy setting), any app can use
  it and tell that Tor is in use.  The ports stay open while the proxy
  restarts, and while it is disconnected (paused) unless that is turned
  off, so that no other app can take them; they are closed when the proxy
  fails to start or the user closes them.
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
