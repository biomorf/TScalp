###################################################
# Настройка Gradle в Android Studio

Документ описывает нюансы запуска приложения из Android Studio
и работы с APK-файлами в TScalp.

## Автоматический `clean assembleDebug` перед запуском

**Проблема:** при нажатии Shift+F10 Android Studio запускает
`installDebug` без предварительной очистки. Из-за этого:
- задачи `renameDebugApk` / `copyDebugApk` не отрабатывают
  (Gradle считает `assembleDebug` up-to-date);
- APK остаётся старым, если не сделать чистую сборку;
- приходится вручную запускать
  `./gradlew :app:clean :app:assembleDebug` из терминала.

**Решение:** настроить в Android Studio запуск задачи
`clean assembleDebug` в секции **Before launch** конфигурации
запуска.

### Пошагово

1. **Run → Edit Configurations…**
2. Выбрать конфигурацию `app`.
3. В разделе **Before launch** нажать **+**.
4. Выбрать **Run Gradle Task**.
5. В поле **Gradle project** указать директорию проекта.
6. В поле **Tasks** задать: `clean assembleDebug`
7. Нажать **OK**.

### Результат

При каждом Shift+F10:
1. Выполнится `clean assembleDebug` — полная пересборка APK с нуля.
2. Установится свежий APK.
3. Приложение запустится.

Красивое имя APK (`tscalp-debug-v1.XXX.apk`) появляется
автоматически через `finalizedBy` на `assembleDebug`.

## Unit-тесты: android.util.Log в JVM

**Проблема:** production-код логирует через `AppLogger` →
`android.util.Log`. В обычном JVM unit-тесте (без Robolectric)
`android.util.Log.*` бросает `RuntimeException at Log.java:-1`.
Первый же вызов `AppLogger.d(...)` в тестируемом коде роняет
тест — без указания на реальную причину.

Воспроизведение (2026-10-22, `PositionFlowManagerTest`):
`PositionFlowManager.start()` логирует через `AppLogger.d`,
тест падает с `RuntimeException at Log.java:-1`. Старые тесты
(`enrichAndCache`, `ws currency`) не падали — они не идут через
`start()` и, соответственно, не логируют.

**Решение:** в `app/build.gradle.kts` в блоке `android {}`:

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

`android.util.Log` и другие методы Android-фреймворка возвращают
значения по умолчанию (`0`, `null`, `false`) вместо `RuntimeException`.

**Что это даёт:** тесты, проходящие через `start()` / `stop()` /
`reapplyDebugFlags()` менеджеров (и любой другой код с
`AppLogger`), работают без Robolectric. Стек-трейсы в логах
теста не выводятся, но для проверки логики это не нужно.

**Что это НЕ даёт:** не заменяет Robolectric. `AppLogger`
в тесте «молчит» (Log.d ничего не делает), не эмитит сообщения
в logcat. Если нужна проверка «был ли вызов Log» — это отдельная
задача (Robolectric, mockk static). Сейчас такой потребности нет:
проверяется поведение (`subscribePositions called once`), а не
логи.

**Связано:** `AGENTS.md` → «Unit-тесты: mockk для suspend-функций».


## Замечания

- **Configuration cache:** флаг `--no-configuration-cache` нельзя
  передать через Run Gradle Task. Альтернатива — отключить
  configuration cache глобально в `gradle.properties` строкой
  `org.gradle.configuration-cache=false`.
- **Время сборки:** `clean` замедляет запуск. Если мешает —
  уберите `clean`, положившись на инкрементальную сборку.
- **Release:** аналогично можно создать отдельную конфигурацию
  с задачей `clean assembleRelease`.