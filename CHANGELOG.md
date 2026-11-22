# Changelog

## 1.1.0 (2026-11-22)

Privacy by default, with Orchid 2.9.0, for Android 16.  1.0.1 was not
released; its fixes are here.

### Other apps on the device

Every app on the device can connect to `127.0.0.1`.  1.0 let any of them
use the proxy, tell from it that Tor is in use, and take its ports while
it was stopped.

- A password for each app: Settings › Apps has a profile (a name and a
  random password) for every app set up to use the proxy, and since
  streams with different credentials never share a circuit, each app has
  circuits of its own (Orchid 2.9.0, `TorConfig#setSocksPasswords`).
- New installs require a password and have the HTTP CONNECT port off; the
  first run asks whether the Wi-Fi proxy setting, which can send no
  password, will be used instead.  Updates from 1.0 keep their settings:
  the single password, if any, becomes the "Default" profile.
- Disconnecting pauses: the ports stay bound and refuse every connection,
  so that the apps set up to use the proxy fail instead of reaching an app
  that took the port.  The notification stays, with Connect and "Close the
  ports".  A pause survives a process restart and a reboot.  Settings can
  turn this off.
- The ports also stay open while the proxy restarts to apply new
  settings (Orchid 2.9.0 accepts on server sockets that Rakkyo keeps).  A
  port held by another app is found before the proxy starts, and the
  message says what it means: the apps set to use the port may be
  sending their traffic to that app, not through Tor.
- Passwords, profiles, the isolation by destination and the new SafeSocks
  setting apply at once, without reconnecting.  Ports and bridges still
  need a reconnection.

### DNS leaks

- While connected, the Home screen warns when an app gives the proxy only
  an IP address, which means it looked the host name up itself, outside
  Tor: how many, from which app, and how to fix it.  Settings shows each
  profile's connections since connecting.
- "Refuse connections by IP address" (Tor's SafeSocks), off by default.

### Lock screen, logs, screenshots

- The notifications are no longer shown on a secure lock screen: they say
  that Tor is in use.  (Visibility "private" would not be enough: with
  Android's default lock screen setting, private notifications are shown
  in full.)
- Release builds no longer write Orchid's warnings to logcat, which adb,
  bug reports and device makers' log collection can read.  Orchid 2.9.0
  also leaves the addresses of guards and bridges out of its log.
- The bridge dialog is kept out of screenshots and the recent apps screen,
  and the keyboard is asked not to learn the bridge lines and port
  numbers (IME_FLAG_NO_PERSONALIZED_LEARNING; Android 8.0 and later).
- A copied password is taken off the clipboard after a minute, if it is
  still there.
- The guide says that the Wi-Fi proxy setting does not apply to mobile
  data or other networks, where apps connect directly.
- Orchid 2.9.0: the ports no longer answer stray requests in words that
  name Tor ("Tor is not an HTTP Proxy"), and TLS sessions with relays are
  no longer resumed, so that a guard or bridge cannot link the
  connections made before and after a network change.

### Android 16

- Targets Android 16 (API 36), as Google Play requires from 31 August
  2026, with the Android Gradle plugin 8.13.2.
- The predictive back gesture: Back goes from another tab to Home, and
  leaves the app from Home with the system's animation.

### Build

- Gradle dependency verification (`gradle/verification-metadata.xml`):
  Orchid must be signed by its maintainer's key, and every other
  dependency and plugin match its key or checksum.
  `tools/update-verification-metadata.sh` updates it after a dependency
  change, as CI builds (an empty Gradle home, every task run), adds
  aapt2 for Linux, Windows and macOS (which Gradle does not record), and
  lists what is new for review.  The keys are kept as text in
  `gradle/verification-keyring.keys`.
- CI builds the release APK too; the GitHub Actions are pinned to
  commits, and Dependabot proposes updates.
- Built with the Android Gradle plugin 8.13.2 on Gradle 8.14.3.  The
  release APK is signed outside the build, with APK Signature Scheme v2
  and v3.

## 1.0.0 (2026-11-21)

First release, with Orchid 2.8.0.  Rakkyo runs a proxy on the device that
sends the connections of other apps through the Tor network.  It needs
Android 7.0 (API 24) or later; no root, no native code, no VPN slot.

### Proxy

- SOCKS5 on `127.0.0.1:9150` and HTTP CONNECT on `127.0.0.1:8118`, both
  on the loopback address only; the ports can be changed, and HTTP CONNECT
  turned off.  HTTP CONNECT carries `https://` sites only.
- Optional random password: SOCKS5 user name/password, or
  `Proxy-Authorization` for HTTP CONNECT.
- Separate circuits per destination host (Tor's IsolateDestAddr), on by
  default.
- New identity, from the Home screen or the notification.
- Plain bridges (`ADDRESS:PORT [FINGERPRINT]`).

### Screens

- Home: connect and disconnect, the progress of the connection in four
  steps, the proxy addresses with copy buttons, and the traffic so far.
- Circuits: the circuits in use, idle and on standby, with the country of
  each relay; a summary of the entry guards, which warns and offers
  bridges when none of them can be reached.  The guard details are on a
  screen of their own.  Both are kept out of screenshots and the recent
  apps screen.
- Settings, and a guide to using the proxy from browsers and the Wi-Fi
  proxy setting.
- English and Japanese (per-app language on Android 13 and later); dark
  theme.

### Running

- A foreground service keeps the proxy running, with a notification to
  stop it or ask for a new identity; optionally started at boot.
- Follows network changes (Wi-Fi to mobile data, back online).
- Rests after an hour without use, and wakes at the next connection.

### Privacy

- Country codes come from Tor's GeoIP database (IPFire Location,
  CC BY-SA 4.0), bundled with the app and used only on the device.
- No relays, guards or traffic in the logs or notifications; the app's
  data, entry guards included, is excluded from backups and device
  transfers.
- No network connections of its own besides those of Tor.

### Not yet supported

- Pluggable transports (obfs4, Snowflake, WebTunnel).
- IPv6.
- A VPN mode: only apps set to use the proxy go through the Tor network.
