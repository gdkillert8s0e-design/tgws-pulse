# Notices

## TG Pulse (Android application)

Licensed under the GNU General Public License v3.0 (`LICENSE`). Derived from the
Android wrapper of [amurcanov/tg-ws-proxy-android](https://github.com/amurcanov/tg-ws-proxy-android),
which is also distributed under GPL-3.0. Application name, package name, icon and
user interface are original to this project. ByeByeDPI and all VPN components were removed.

## Flowseal TG WS Proxy core

`upstream/flowseal/` is the unmodified Python core by Flowseal, licensed under MIT
(`upstream/flowseal/LICENSE`), except for one documented change recorded in
`upstream/flowseal.lock.json`: the AES implementation is selected from Chaquopy's
bundled OpenSSL on Android.

## Local Android adapter

`app/src/main/python/android_proxy.py` and `route_probe.py` are part of this project
(GPL-3.0). They wrap the core's lifecycle for Android and add route diagnostics.

## Fonts

Unbounded, Onest and JetBrains Mono, licensed under the SIL Open Font License 1.1
(`licenses/fonts/`).

## Trademarks

Telegram is a trademark of its owners. This project is unofficial and not affiliated
with Telegram.
