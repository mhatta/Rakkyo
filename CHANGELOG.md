# Changelog

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
