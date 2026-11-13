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


## Compose UI tests (Robolectric)

Инфраструктура для **Compose UI** тестов на JVM (без эмулятора), гоняются
в `testDebugUnitTest` → CI `fast-check`.

### Зависимости (app/build.gradle.kts)

- `testImplementation("androidx.compose.ui:ui-test-junit4")` (BOM уже в проекте).
- `testImplementation("org.robolectric:robolectric:4.14.1")`.
- `testImplementation("androidx.test:runner:1.6.2")`.
- `testImplementation("androidx.test.ext:junit:1.2.1")`.
- `debugImplementation("androidx.compose.ui:ui-test-manifest")` — уже есть.
- `testOptions { unitTests.isIncludeAndroidResources = true }` — обязательно для Robolectric.

### Как писать тест

~~~kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class XTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `assert`() {
        rule.setContent { TScalpTheme { XComposable(...) } }
        rule.onNodeWithText("...").assertIsDisplayed()
        rule.onNodeWithContentDescription("...").performClick()
    }
}
~~~

### Ограничения Robolectric

- нет RenderThread, bitmap, accessibility-дерева;
- нет реального IME, insets, edge-to-edge, WebView, камеры/биометрии;
- для таких — instrumented (`androidTest` + эмулятор), у нас пока не требуется.

### Полезные API

- `onNodeWithText / onNodeWithContentDescription / onNode(hasTestTag(...))`;
- `performClick / performTextInput / performScrollToIndex`;
- `waitForIdle()` — дожать recomposition/animations;
- `mainClock.autoAdvance = false` + `mainClock.advanceTimeBy(ms)` — кадровый контроль (`LaunchedEffect`-задержки).

### Screenshot tests (Roborazzi)

Rendering-regression guard для UI: PNG-baseline в git, pixel-diff в CI.
Плагин `io.github.takahirom.roborazzi:1.79.0` (root + app), baseline в
`app/src/test/screenshots/`, `outputDir` в `app/build.gradle.kts`.

**Запись/обновление baseline** (только на desktop, после осознанного
UI-изменения):

    ./gradlew :app:recordRoborazziDebug

PNG кладутся в `app/src/test/screenshots/` (FQN-префикс класса + имя
теста + `.png`). Проверить `git status`, закоммитить PNG вместе с
правкой.

**Проверка** (локально или в CI):

    ./gradlew :app:verifyRoborazziDebug

Падение = pixel-diff > порога. Diff-изображения в
`app/build/outputs/roborazzi/`; в CI job `screenshot-test` уходят в
artifact `roborazzi-results`.

**v2 API.** Новые screenshot-тесты используют
`androidx.compose.ui.test.junit4.v2.createComposeRule` (v1 deprecated).
Полная миграция Behaviour-тестов на v2 — отдельная задача
(`docs/ci-roadmap.md` → «Compose UI tests: миграция на v2»).

**Связано:** `docs/ROADMAP.md` → `### OrderFormViewModel: split на sub-VM`
(предзадача «Roborazzi screenshot tests»).

### Desktop

На desktop `./gradlew :app:testDebugUnitTest` работает локально; на mobile
(proot) — только CI (`fast-check`), локальный Gradle не поднимается.


## Замечания

- **Configuration cache:** флаг `--no-configuration-cache` нельзя
  передать через Run Gradle Task. Альтернатива — отключить
  configuration cache глобально в `gradle.properties` строкой
  `org.gradle.configuration-cache=false`.
- **Время сборки:** `clean` замедляет запуск. Если мешает —
  уберите `clean`, положившись на инкрементальную сборку.
- **Release:** аналогично можно создать отдельную конфигурацию
  с задачей `clean assembleRelease`.