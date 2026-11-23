# TG Pulse: сборка и установка

Пошаговая инструкция от пустого компьютера до APK на телефоне.

## 1. Что понадобится

| Компонент | Версия | Зачем |
|---|---|---|
| JDK | 21 | Gradle и компиляция |
| Android SDK | platform 35, build-tools 36.0.0 | сборка APK |
| Python | 3.13 | Chaquopy упаковывает Python-ядро |
| Git (необязательно) | любая | клонирование |

Проверка, что инструменты на месте:

```
java -version          # openjdk version "21..."
python3.13 --version   # Python 3.13.x
```

Если Python 3.13 не установлен: на Windows скачайте с python.org, на macOS `brew install python@3.13`,
на Ubuntu `sudo apt install python3.13 python3.13-venv`.

## 2. Android SDK

**Вариант A: Android Studio (проще).** Установите Android Studio, откройте
`SDK Manager → SDK Platforms → Android 15.0 (API 35)` и
`SDK Tools → Android SDK Build-Tools 36`. Путь к SDK обычно:
- Windows: `C:\Users\<имя>\AppData\Local\Android\Sdk`
- macOS: `~/Library/Android/sdk`
- Linux: `~/Android/Sdk`

**Вариант B: командная строка.** Скачайте command-line tools с developer.android.com и выполните:

```
sdkmanager "platforms;android-35" "build-tools;36.0.0" "platform-tools"
```

## 3. Распаковка проекта

```
tar -xzf tgws-pulse-src.tar.gz
cd tgws-pulse
```

Или распакуйте папку `tgws-pulse` из архива через проводник.

## 4. Файл local.properties

Создайте в корне проекта файл `local.properties` (он не попадает в git). Это ключевой шаг:

```
sdk.dir=C\:\\Users\\ВАШЕ_ИМЯ\\AppData\\Local\\Android\\Sdk
buildPython=C:\\Python313\\python.exe
```

Пример для macOS/Linux:

```
sdk.dir=/Users/ваше_имя/Library/Android/sdk
buildPython=/usr/local/bin/python3.13
```

Узнать путь к Python 3.13: `which python3.13` (macOS/Linux) или `where python` (Windows).
Chaquopy требует именно эту версию, иначе сборка остановится с ошибкой про `buildPython`.

## 5. Проверка Python-ядра (по желанию, но рекомендуется)

```
python -m pip install -r scripts/requirements-test.txt
python scripts/run_python_tests.py
```

Ожидаемый результат: `227 passed, 8 skipped`. Это проверяет ядро Flowseal и адаптер
до сборки APK. Если тест падает, пришлите вывод.

## 6. Сборка APK

Из корня проекта:

**Debug (для личного теста, подписывается отладочным ключом):**

```
./gradlew :app:testDebugUnitTest     # JVM-тесты (ProxyLinkTest)
./gradlew :app:assembleDebug         # APK
```

Windows: используйте `gradlew.bat` вместо `./gradlew`.

Результат: `app/build/outputs/apk/debug/app-debug.apk`.

Первая сборка долгая (10–30 минут): Gradle скачивает зависимости, Chaquopy ставит
Python-пакеты из `app/requirements-android.txt`. Последующие сборки быстрые.

## 7. Release-сборка (подписанная)

Сначала создайте ключ (один раз, сохраните файл и пароли):

```
keytool -genkeypair -v -keystore tgws-release.jks -alias tgws -keyalg RSA -keysize 4096 -validity 10000
```

Затем задайте переменные окружения или добавьте в `local.properties`:

```
KEYSTORE_FILE=/полный/путь/tgws-release.jks
KEYSTORE_PASSWORD=ваш_пароль
KEY_ALIAS=tgws
KEY_PASSWORD=ваш_пароль
```

Сборка:

```
./gradlew :app:assembleRelease
```

Результат: `app/build/outputs/apk/release/app-release.apk`.

**Важно:** ключ нельзя терять. Обновления будут устанавливаться поверх старой версии
только при той же подписи.

## 8. Установка на телефон

1. На телефоне: Настройки → О телефоне → семь раз нажмите «Номер сборки», затем
   Настройки → Для разработчиков → включите «Отладка по USB».
2. Подключите телефон кабелем. Разрешите отладку на экране телефона.
3. Установка:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Без `adb`: скопируйте APK на телефон и откройте его файловым менеджером. Разрешите
установку из этого источника.

Телефон должен быть **arm64** (практически все современные Android-телефоны) и с Android 7.0 или новее.

## 9. Первый запуск

1. Откройте TG Pulse. Разрешите уведомления: они нужны, чтобы видеть состояние прокси.
2. Нажмите большую кнопку. Ожидайте статус «Подключено».
3. Нажмите «Открыть в Telegram», выберите Telegram, подтвердите добавление прокси.
4. Telegram начнёт подключаться через локальный прокси.

Если статус «Ошибка»: откройте вкладку «Диагностика». Там записаны причины.

## 10. Проверка работы

Чек-лист перед тем как считать сборку рабочей:

- [ ] Статус сменяется: Выключено → Подключение → Подключено.
- [ ] «Подключено» появляется только когда порт отвечает (вкладка «Диагностика» → «Локальный порт»: «Отвечает»).
- [ ] Ссылка открывает Telegram и предлагает добавить прокси.
- [ ] Остановка работает, повторный запуск работает.
- [ ] Выключите Wi-Fi, включите мобильную сеть: прокси восстанавливается без перезапуска.
- [ ] Уведомление показывает трафик и кнопку «Остановить».
- [ ] Настройки → «Запуск после перезагрузки» работает после ребута (на Android 14+ может не сработать, сообщите результат).

## 11. Типичные ошибки сборки

| Ошибка | Что делать |
|---|---|
| `SDK location not found` | Проверьте `sdk.dir` в `local.properties` |
| `buildPython ... not found` | Укажите полный путь к Python 3.13 в `buildPython` |
| `Unsupported class file major version` | Нужна JDK 21: в Gradle выставьте JAVA_HOME на JDK 21 |
| `Could not resolve ...` | Проверьте интернет и прокси; повторите сборку |
| `pip install ... failed` (Chaquopy) | Проверьте, что `buildPython` указывает на 3.13, а не 3.12 или 3.14 |
| `Unresolved reference` в Kotlin | Пришлите первые 30 строк ошибки: это ожидаемо для непроверенного кода |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Старая версия подписана другим ключом. Удалите приложение и установите заново |
| `./gradlew: Permission denied` | Выполните `chmod +x gradlew` |

## 12. Если что-то не собирается

Сохраните полный вывод Gradle, например:

```
./gradlew :app:assembleDebug --stacktrace > build-log.txt 2>&1
```

и пришлите `build-log.txt`. Проект ещё не компилировался, поэтому первые ошибки
компиляции Kotlin и Compose ожидаемы. Я исправлю их по логу.
