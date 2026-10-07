# ROADMAP

План задач проекта TScalp: что в работе, что запланировано, что
отложено. Формат — секция на задачу, со статусом и оценкой.

---

## Расшифровка кодов ошибок брокеров

**Статус:** частично закрыто.

**Сделано:**
- `util/BrokerErrorMessages.kt` — центральный реестр
  `Map<BrokerName, Map<code, msg>>`.
- `util/TInvestErrorMessages.kt` — ~130 кодов T-Invest по
  официальной документации.
- `AppError.Api` расширен полями `brokerName`, `brokerCode`.
- `toAppError(brokerName)` и `runCatchingAppResult(brokerName, block)`.
- `InvestRepository` передаёт `brokerName` во все вызовы.

**Осталось:**
- `BcsErrorMessages.kt` — заполнить по мере нахождения кодов BCS.
- `FinamErrorMessages.kt` — то же.
- Дополнять T-Invest таблицу новыми кодами по мере появления в логах.

**Оценка:** 15 минут на нового брокера + 5 минут на новый код.

---

## Переход на edge-to-edge (statusBarColor deprecated)

**Статус:** запланировано, требует отдельной подготовки.

**Что:** `window.statusBarColor` и `window.navigationBarColor` устарели.
С Android 15 система применяет edge-to-edge принудительно для приложений
с target SDK 35+.

**Действия:**
1. Убрать `window.statusBarColor = ...` из `Theme.kt`.
2. Убедиться, что в `MainActivity` вызывается `enableEdgeToEdge()`.
3. Проверить все 4 экрана (Заявки, Портфель, Настройки, MainScreen)
   на Android 12, 13, 14, 15.
4. При необходимости — добавить `WindowInsets` в `Scaffold`-ы.

**Оценка:** ~2 часа + прогон на устройствах разных версий.
**Когда:** при подготовке релиза под Android 15 или target SDK 36.

---

## CI/CD для релизов через GitLab

**Статус:** запланировано.

**Что:** настроить GitLab CI для сборки и публикации APK/AAB
в GitLab Releases при пуше тега (`v1.0.0` и т.п.).

**Зачем:** сейчас APK собирается локально и вручную; release-канал
для пользователей не автоматизирован.

**Объём:**
- `.gitlab-ci.yml`: build job для `assembleRelease`.
- Подпись APK через CI variables (keystore, пароли).
- Загрузка артефакта в GitLab Releases через `release-cli`.
- Опционально: unit-тесты как отдельный job.
- Опционально: `versionName` из тега.

**Оценка:** ~2 часа (при наличии keystore).
**Когда:** перед первым публичным релизом.

---

## CI/CD улучшения

**Статус:** частично сделано, остальное в плане.

**Уже работает:**
- `.gitlab-ci.yml`: стадии `test → build → release`.
- `unit-tests` на MR, `dev`, `master`, тегах; `interruptible: true`.
- `build-release` на тегах `vX.Y.Z`: проверка `git merge-base`,
  подпись из `KEYSTORE_BASE64` + `keystore.properties`, сборка
  `assembleRelease -PreleaseVersionName=X.Y.Z`.
- `publish-release`: GitLab Release с APK в assets.
- Кэш Gradle по хэшу конфигурационных файлов.
- master защищён (см. «Закрытые задачи»).

**Запланировано** (в порядке приоритета):

### 1. Отдельный lint job

**Что:** параллельный job в стадии `test` — `:app:lintDebug`,
ktlint, detekt. Отдельно от unit-тестов, чтобы падения не
смешивались.
**Зачем:** ловить проблемы стиля и потенциальные баги до review;
пайплайн падает раньше unit-тестов (они дольше).
**Подводный камень:** первый прогон выдаст много warning'ов —
зафиксировать baseline (`lint.xml`, `detekt-baseline.xml`),
запретить новые.
**Оценка:** ~40 мин.
**Сделано:**
- Lint job в стадии `test` (только Android Lint).
  Baseline `app/lint-baseline.xml`, `warningsAsErrors = true`,
  `disable += "AndroidGradlePluginVersion"` (динамический
  detector, несовместим с baseline). HTML/XML-отчёты в
  артефактах CI.
- Build cache: стабильный ключ `gradle-cache-v1`, `.gradle/jdks`
  в paths.

**Запланировано** (в порядке приоритета):

### 1b. ktlint

**Что:** плагин `org.jlleitschuh.gradle.ktlint`, job
`:app:ktlintCheck`, baseline через `.editorconfig`.
**Зачем:** стиль Kotlin в CI.
**Риск:** совместимость плагина с Kotlin 2.4.20 / AGP 9.0.0.
**Оценка:** ~30 мин + первый прогон.

### 1c. detekt

**Что:** плагин `io.gitlab.arturbosch.detekt`, job
`:app:detekt`, baseline.
**Зачем:** статический анализ сложности/запахов кода.
**Риск:** из коробки много замечаний, нужен baseline.
**Оценка:** ~30 мин + первый прогон.

### 2. Build cache для Gradle

**Что:** включить `--build-cache` в CI-вызовах, добавить
`$GRADLE_USER_HOME/jdks` в кэш.
**Зачем:** переиспользование результатов задач между джобами
(`compileDebugKotlin` из `unit-tests` → `lint`). Экономия
30–50% на инкрементальных пайплайнах.
**Оценка:** ~10 мин.
**Риск:** проверить, что кастомные задачи `rename*Apk` не дают
warning'ов cacheability.

### 3. Smoke-сборка assembleDebug

**Что:** job, собирающий debug APK на MR/push.
**Зачем:** ловить ошибки компиляции/ресурсов до локального запуска.
`unit-tests` компилирует test-сорцы, но не обязательно полный
`assembleDebug` (например, ошибки в `renameDebugApk` или в
ресурсах проскочат).
**Опция:** `rules: changes` на `.kt`/`.xml`/gradle-файлы, чтобы
не гонять на чистой документации.
**Оценка:** ~15 мин (+2–4 мин к пайплайну).

### 4. Release notes из git log

**Что:** генерировать описание релиза из коммитов между предыдущим
и текущим тегом, вместо «Релиз vX.Y.Z. APK: ...».
**Фильтрация:** в notes включаются только `feat` и `fix`.
Инфраструктурные изменения (`chore`, `docs`, `refactor`, `test`)
исключаются по типу коммита — это надёжнее фильтра по путям,
т.к. не зависит от того, какие файлы тронул коммит.
**Команда (черновик):**
```bash
git log <prev>..HEAD --pretty=format:"%s" --no-merges \
  | grep -E '^(feat|fix)(\([^)]+\))?:'
```
**Зачем:** пользователи видят только то, что изменилось в
приложении, без шума про документацию и CI.
**Оценка:** ~1 час.

### 5. versionName в имени артефакта и release description

**Что:** `tscalp-release-<VERSION>.apk` вместо wildcard; версия
в описании релиза.
**Зачем:** при скачивании нескольких релизов не путаешься.
**Когда:** когда релизов станет больше 3–4.
**Оценка:** ~10 мин.

### 6. Coverage в unit-tests

**Что:** JaCoCo/Kover coverage, публикация отчёта в MR.
**Зачем:** видно, какие строки не покрыты.
**Когда:** когда тестов станет заметно больше 63 и появятся
сомнения в покрытии.
**Оценка:** ~1–2 часа.

**Отложено (без триггера):**
- Google Play публикация из CI (fastlane / play-console-cli) —
  при первом релизе в Play.
- AAB как артефакт релиза — отменено. При необходимости
  публикации в Play вернуться к вопросу отдельно.

---

## Удаление legacy mipmap-папок

**Статус:** запланировано.

**Что:** удалить
`app/src/main/res/mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/`.
При `minSdk=30` они не используются: адаптивные иконки из
`mipmap-anydpi` покрывают все поддерживаемые устройства.

**Проверить:** установка на реальном устройстве — иконка в
лаунчере и в списке недавних приложений отображается корректно.

**Оценка:** ~10 минут.
**Когда:** после завершения CI-задач (3, 4, 5, 6).

---

## PositionsStreamServiceGrpc — вернуться к стриму позиций

**Статус:** запланировано. Связано с ISSUES.md → «PositionsStream
не доставляет данные».

**Контекст:** сейчас P&L через polling `PositionStreamManager`
раз в 10 секунд. Официальный стрим не работал на
`kotlin-sdk-grpc-core:1.48.1`; были подозрения, что
`PositionsStreamServiceGrpc` отсутствует в SDK или требует
отдельных прав.

**Что изменилось:** SDK обновлён 1.48.1 → 1.51.0. Есть шанс,
что нужный сервис появился.

**Шаги при возврате:**
1. Проверить наличие `PositionsStreamServiceGrpc` в артефакте
   `kotlin-sdk-grpc-core:1.51.0` (распаковать jar/AAR,
   `grep -r PositionsStreamServiceGrpc`).
2. Если есть — написать минимальный тест-подписки вне
   приложения (отдельный `main`, не трогая `PositionStreamManager`).
3. Песочница: проверить `UNAUTHENTICATED: 40003` — воспроизводится
   ли на 1.51.0.
4. Боевой: проверить, приходит ли `hasPosition()`.
5. Если работает — перевести `PositionStreamManager` на стрим,
   polling оставить как fallback на случай разрыва.
6. Если не работает — обновить ISSUES.md с новыми деталями и
   оставить polling.

**Оценка:** ~2 часа на проверку + ~2 часа на миграцию, если
сервис рабочий.

**Когда вернуться:** сразу после CI-задач 1–3 (быстрые победы),
до задач 4, 5, 6.

---

## Закрытые задачи

### ✅ Единый источник цен (PriceStreamManager)

Реализовано в серии из 6 подкоммитов:
1. `PriceConsumer` enum + скелет `PriceStreamManager`.
2. Union + gRPC-стрим с пересозданием при смене union.
3. REST-fallback раз в 5 секунд.
4. Миграция `OrdersViewModel`.
5. Миграция `PortfolioViewModel` (+ фикс карточек в `OrdersScreen`:
   цена берётся из `uiState.currentPrice`, а не из медленного
   `portfolioPositions`).
6. Переименование `SharedPositionStreamManager` → `PositionStreamManager`.

### ✅ Унификация profitPercent для PortfolioPosition

Введён `calculateProfitPercent(yield, avgPrice, quantity, pointValue)` —
единая формула для всех типов инструментов. Для акций совпадает
с прежними формулами, для фьючерсов корректно учитывает
`pointValue`. Покрыто 11 тестами.

### ✅ Переход на DataStore

`SettingsRepository` и `TradingStateRepository` работают на
`DataStore<Preferences>`. `SharedPreferences` удалён из проекта
полностью. Все методы стали `suspend`, ViewModel'и обновляют
кэшированное состояние в `uiState` реактивно.

### ✅ Защита ветки master

Настроено в GitLab (Settings → Repository → Protected branches):
- `master` защищён; прямой push запрещён — только через MR;
- force push выключен;
- `Allowed to merge: Maintainers`;
- `Allowed to push and merge: No one`.

Require approval намеренно не включён: проект одиночный,
approve своего же MR GitLab не разрешает. Вернуться при появлении
второго Maintainer'а.

CI-проверка в `build-release` (`git merge-base --is-ancestor`)
остаётся как вторая линия обороны — тег обязан стоять на коммите
из master.

