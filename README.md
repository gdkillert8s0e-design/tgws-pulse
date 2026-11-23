# TG Pulse

Локальный MTProto-прокси для Telegram на Android. Приложение запускает ядро
[Flowseal TG WS Proxy](https://github.com/Flowseal/tg-ws-proxy) прямо на телефоне,
поднимает прокси на `127.0.0.1` и передаёт ссылку `tg://proxy` официальному Telegram.

Не требуются root, VPN и удалённые серверы, которые проксируют ваш трафик.

## Как пользоваться

1. Нажмите большую кнопку — прокси запустится на `127.0.0.1`.
2. Дождитесь статуса «Подключено». Он появляется только тогда, когда порт реально отвечает.
3. Нажмите «Открыть в Telegram» и подтвердите добавление прокси.

## Сборка

Нужны JDK 21, Android SDK (platform 35, build-tools 36), Python 3.13 для Chaquopy.

```
python scripts/run_python_tests.py      # тесты ядра и адаптера
./gradlew :app:testDebugUnitTest         # JVM-тесты приложения
./gradlew :app:assembleRelease           # release APK (без ключа — неподписанный)
```

Подпись: задайте `TGWS_KEYSTORE_FILE`, `TGWS_KEYSTORE_PASSWORD`, `TGWS_KEY_ALIAS`,
`TGWS_KEY_PASSWORD` или поля в `local.properties`.

## Лицензии

Приложение распространяется под GPL-3.0 (см. `LICENSE`). Ядро Flowseal — MIT
(`upstream/flowseal/LICENSE`). Шрифты — SIL OFL 1.1 (`licenses/fonts`).
Подробности и атрибуция — в `NOTICE.md`.
