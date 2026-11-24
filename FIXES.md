# Исправления после первого запуска CI

## Причина падения (`:app:testDebugUnitTest`)

Задача не дошла до тестов: на этапе сборки ресурсов `aapt2` не нашёл цвета
`brand_bg` и `brand_accent`, на которые ссылались `drawable/ic_shortcut_start.xml`
и `drawable/ic_shortcut_stop.xml`. Цвета были удалены при переписывании `colors.xml`.

## Все найденные ошибки

1. **Ресурсы:** иконки ярлыков используют заданные цвета (`#FF0D121A`, `#FF5BF0C4`, `#FFFF6F7D`).
2. **Компиляция:** `TelegramConnector` передавал `Array<String>` в `setItems`, который требует
   `Array<CharSequence>` (массивы в Kotlin инвариантны).
3. **Компиляция:** `ProxyShortcutActivity.applyAction` принимал `String`, а получал `String?`
   из `intent.action`.
4. **CI:** на раннере не было нужных пакетов Android SDK. Добавлена установка
   `platforms;android-35`, `build-tools;36.0.0`, `platform-tools`.
5. **CI:** `gradlew` не имел бита исполнения после загрузки через веб-интерфейс.
   Добавлен `chmod +x gradlew`.
6. **Пустая папка** `res/values-v31` удалена.

## Что проверено

- Все ссылки `@type/name` в XML и манифесте разрешаются.
- Все классы из манифеста существуют.
- Все строки (90 ключей) есть в `values` и `values-ru`; апострофы экранированы.
- Все 23 файла Kotlin разбираются парсером Kotlin (синтаксис).
- Логика `ProxyLink` скомпилирована настоящим компилятором Kotlin 2.2.10 и проходит все проверки.
- Версии Gradle 9.6.0, AGP 9.4.1, Chaquopy 17.2.0, Compose-плагин 2.2.10 и блок Chaquopy
  совпадают с исходным проектом, который уже собирался.
- Python-ядро Flowseal не изменялось: `sync_upstream.py --check` проходит, все Python-тесты проходят.

## Что ещё может упасть

Android-часть компилируется только в CI, так как Android и Compose-библиотеки
недоступны из моего окружения. Если появятся новые ошибки компиляции, пришлите первые
30 строк из лога шага `JVM unit tests`.

## Вторая ошибка CI (шаг `Run android-actions/setup-android@v3`)

Внешний action `android-actions/setup-android@v3` сам вызывал `sdkmanager tools`,
а пакета `tools` больше нет, поэтому шаг падал до Gradle. Action удалён: SDK уже установлен
на раннере, теперь лицензии принимаются и ставятся только нужные пакеты собственным шагом.
Также обновлены `actions/checkout` и `actions/setup-java` до v5 (предупреждения о Node 20 / устаревшем setup-java).
