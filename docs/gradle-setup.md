<!-- FILE_BEGIN -->
###################################################
# Настройка Gradle в Android Studio

Документ описывает нюансы запуска приложения из Android Studio
и работы с APK-файлами в TScalp.










<!-- SECTION_BEGIN: gradle-setup -->
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










<!-- SUBSECTION_BEGIN: gradle-setup-1 -->
<a id="gradle-setup-1"></a>
### gradle-setup-1 Пошагово
1. **Run → Edit Configurations…**
2. Выбрать конфигурацию `app`.
3. В разделе **Before launch** нажать **+**.
4. Выбрать **Run Gradle Task**.
5. В поле **Gradle project** указать директорию проекта.
6. В поле **Tasks** задать: `clean assembleDebug`
7. Нажать **OK**.

---
<!-- SUBSECTION_END: gradle-setup-1 -->










<!-- SUBSECTION_BEGIN: gradle-setup-2 -->
<a id="gradle-setup-2"></a>
### gradle-setup-2 Результат
При каждом Shift+F10:
1. Выполнится `clean assembleDebug` — полная пересборка APK с нуля.
2. Установится свежий APK.
3. Приложение запустится.

Красивое имя APK (`tscalp-debug-v1.XXX.apk`) появляется
автоматически через `finalizedBy` на `assembleDebug`.

---
<!-- SUBSECTION_END: gradle-setup-2 -->










<!-- SUBSECTION_BEGIN: gradle-setup-3 -->
<a id="gradle-setup-3"></a>
### gradle-setup-3 Зависимости (app/build.gradle.kts)
- `testImplementation("androidx.compose.ui:ui-test-junit4")` (BOM уже в проекте).
- `testImplementation("org.robolectric:robolectric:4.14.1")`.
- `testImplementation("androidx.test:runner:1.6.2")`.
- `testImplementation("androidx.test.ext:junit:1.2.1")`.
- `debugImplementation("androidx.compose.ui:ui-test-manifest")` — уже есть.
- `testOptions { unitTests.isIncludeAndroidResources = true }` — обязательно для Robolectric.

---
<!-- SUBSECTION_END: gradle-setup-3 -->










<!-- SUBSECTION_BEGIN: gradle-setup-4 -->
<a id="gradle-setup-4"></a>
### gradle-setup-4 Как писать тест
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

---
<!-- SUBSECTION_END: gradle-setup-4 -->










<!-- SUBSECTION_BEGIN: gradle-setup-5 -->
<a id="gradle-setup-5"></a>
### gradle-setup-5 Ограничения Robolectric
- нет RenderThread, bitmap, accessibility-дерева;
- нет реального IME, insets, edge-to-edge, WebView, камеры/биометрии;
- для таких — instrumented (`androidTest` + эмулятор), у нас пока не требуется.

---
<!-- SUBSECTION_END: gradle-setup-5 -->










<!-- SUBSECTION_BEGIN: gradle-setup-6 -->
<a id="gradle-setup-6"></a>
### gradle-setup-6 Полезные API
- `onNodeWithText / onNodeWithContentDescription / onNode(hasTestTag(...))`;
- `performClick / performTextInput / performScrollToIndex`;
- `waitForIdle()` — дожать recomposition/animations;
- `mainClock.autoAdvance = false` + `mainClock.advanceTimeBy(ms)` — кадровый контроль (`LaunchedEffect`-задержки).

---
<!-- SUBSECTION_END: gradle-setup-6 -->










<!-- SUBSECTION_BEGIN: gradle-setup-7 -->
<a id="gradle-setup-7"></a>
### gradle-setup-7 Screenshot tests (Roborazzi)
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

---
<!-- SUBSECTION_END: gradle-setup-7 -->










<!-- SUBSECTION_BEGIN: gradle-setup-8 -->
<a id="gradle-setup-8"></a>
### gradle-setup-8 Desktop
На desktop `./gradlew :app:testDebugUnitTest` работает локально; на mobile
(proot) — только CI (`fast-check`), локальный Gradle не поднимается.

---
<!-- SUBSECTION_END: gradle-setup-8 -->










<!-- SECTION_END: gradle-setup -->










<!-- SECTION_BEGIN: gradle-setup -->
## Замечания

- **Configuration cache:** флаг `--no-configuration-cache` нельзя
  передать через Run Gradle Task. Альтернатива — отключить
  configuration cache глобально в `gradle.properties` строкой
  `org.gradle.configuration-cache=false`.
- **Время сборки:** `clean` замедляет запуск. Если мешает —
  уберите `clean`, положившись на инкрементальную сборку.
- **Release:** аналогично можно создать отдельную конфигурацию
  с задачей `clean assembleRelease`.










<!-- SECTION_END: gradle-setup -->










<!-- FILE_END -->
