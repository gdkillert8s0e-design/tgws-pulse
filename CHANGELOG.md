# Changelog

## 1.0.0

- Renamed to TG Pulse; new package `com.tgwsmobile.proxy`.
- Removed ByeByeDPI, VpnService, SOCKS5 and all related permissions and screens.
- New dark interface with a state-driven power control and a motion system that respects reduce motion.
- Connected status is shown only when the local port answers (TCP probe every 3 s).
- Three consecutive failed probes stop the service with an error and a journal entry.
- Fixed: stopping the proxy right after a client disconnected could take 15 s and report an error.
- The proxy secret is encrypted with an Android Keystore key.
- Autostart after boot is optional and off by default.
