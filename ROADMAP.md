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

## CI/CD улучшения

**Статус:** частично сделано, остальное в плане.

**Уже работает (инфраструктура):**
- `.gitlab-ci.yml`: стадии `test → build → release`.
- Три job'а в стадии `test`: `unit-tests`, `lint`, `assemble-debug`.
- `build-release` на тегах `vX.Y.Z`: проверка
  `git merge-base`, подпись из `KEYSTORE_BASE64` +
  `keystore.properties`, сборка
  `assembleRelease -PreleaseVersionName=X.Y.Z`.
- `publish-release`: GitLab Release с APK в assets и release
  notes из git log (только `feat`/`fix`).
- Кэш Gradle: стабильный ключ `gradle-cache-v1`, `.gradle/jdks`
  в paths.
- master защищён; MR через fast-forward merge
  (`Settings → Merge requests → Merge method`).
- Protected Tags `v*`.

**Сделано:**
- Lint job в стадии `test`. Baseline `app/lint-baseline.xml`,
  `warningsAsErrors = true`, `disable` для динамических
  детекторов (`AndroidGradlePluginVersion`, `GradleDependency`,
  `NewerVersionAvailable`) — несовместимы с baseline, так как
  сообщение зависит от последней версии в Maven Central.
  HTML/XML-отчёты в артефактах CI.
- Smoke-сборка `assembleDebug` в стадии `test`, параллельно
  `unit-tests` и `lint`. Ловит ошибки, которые не покрывают
  тесты и lint: битые ресурсы, конфликты в `packaging {}`,
  `AndroidManifest.xml`, а также `renameDebugApk`.
- Release notes из git log между предыдущим и текущим тегом.
  Фильтр по типу коммита: только `feat` и `fix`. Fallback
  «No user-facing changes».
- Build cache: стабильный ключ вместо хэша конфигурационных
  файлов.
- `versionName` в имени release APK — через задачу
  `renameReleaseApk` в `build.gradle.kts`.

**Запланировано** (в порядке приоритета):

### ktlint

**Что:** плагин `org.jlleitschuh.gradle.ktlint`, job
`:app:ktlintCheck`, baseline через `.editorconfig`.
**Зачем:** стиль Kotlin в CI.
**Риск:** совместимость плагина с Kotlin 2.4.20 / AGP 9.0.0.
**Оценка:** ~30 мин + первый прогон.

### detekt

**Что:** плагин `io.gitlab.arturbosch.detekt`, job
`:app:detekt`, baseline.
**Зачем:** статический анализ сложности/запахов кода.
**Риск:** из коробки много замечаний, нужен baseline.
**Оценка:** ~30 мин + первый прогон.

### versionName в GitLab Release description

**Статус:** в работе.

**Что:** добавить строку `Release vX.Y.Z` перед списком
release notes. Сейчас description содержит только список
`feat`/`fix`, без явного указания версии.

**Как:** в `publish-release` формируется `DESCRIPTION` через
`printf -v DESCRIPTION 'Release %s\n\n%s' "$CI_COMMIT_TAG" "$NOTES"`.

**Оценка:** ~5 мин.

### Coverage в unit-tests

**Что:** JaCoCo/Kover coverage, публикация отчёта в MR.
**Зачем:** видно, какие строки не покрыты.
**Когда:** когда тестов станет заметно больше 63.
**Оценка:** ~1–2 часа.

**Отложено (без триггера):**
- Google Play публикация из CI (fastlane / play-console-cli) —
  при первом релизе в Play.
- AAB как артефакт релиза — отменено. При необходимости
  публикации в Play вернуться отдельно.
---

## Удаление legacy `.webp`-иконок

**Статус:** в работе.

**Что:** удалить `ic_launcher.webp` и `ic_launcher_round.webp`
из `mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/`. При `minSdk=30`
они не используются: adaptive-иконки из `mipmap-anydpi`
покрывают все поддерживаемые устройства.

**Важно:** файлы `ic_launcher_foreground.webp` в тех же папках
**остаются**. На них ссылается adaptive XML:
`mipmap-anydpi/ic_launcher.xml` → `@mipmap/ic_launcher_foreground`.
Удаление папок целиком сломает иконку.

**Проверить:** установка на реальном устройстве — иконка в
лаунчере и в списке недавних приложений отображается корректно.

**Возможное улучшение в будущем:** перенести
`ic_launcher_foreground` в `drawable-{density}/` или заменить
векторным foreground. Отдельная задача, не в этой.

**Оценка:** ~10 минут.

---

## Смена лицензии GPL v3 → Apache 2.0

**Статус:** в работе.

**Что:** заменить лицензию проекта с GNU GPL v3 на Apache
License 2.0.

**Зачем:** Apache 2.0 — более разрешительная лицензия, совместима
с большинством корпоративных политик, содержит явный патентный
грант. GPL v3 требует распространения производных работ под той
же лицензией, что ограничивает переиспользование.

**Проверки (выполнены):**
- Автор кода один — внешних контрибьюторов нет. Согласие
  третьих лиц не требуется.
- Сторонний GPL-код в проекте отсутствует.
- SPDX-заголовков и копирайт-блоков в исходниках нет.

**Действия:**
- Заменить содержимое `LICENSE` на текст Apache 2.0.
- Обновить секцию License в `README.md`.
- Обновить метаданные проекта на GitLab / GitHub.

**Юридическая оговорка:** уже распространённые копии под GPL v3
остаются под GPL v3 — лицензия не отзывается задним числом.
Коммиты и теги до смены — GPL v3, после — Apache 2.0. Стоит
явно указать это в README.

**Оценка:** ~30 минут.

---

## Обновление compileSdk до 37

**Статус:** запланировано.

**Что:** обновить `compileSdk` с 36 до 37, оставив
`targetSdk = 36`.

**Зачем:** доступ к новым API Android 17 без активации
изменений поведения. Google Play пока требует только
`targetSdk 36`[reference:5], обновление `targetSdk` до 37
не обязательно.

**Риски `targetSdk 37`:**
- Игнорирование ограничений ориентации и изменения размера
  на больших экранах (sw > 600dp)[reference:6].
- Новые лимиты памяти для Bitmap/Icons в RemoteViews[reference:7].
- Возможные баги эмулятора для API 37[reference:8].

**План:**
1. Обновить `compileSdk` до 37 в `app/build.gradle.kts`.
2. Локальная сборка: `./gradlew :app:clean :app:testDebugUnitTest --rerun-tasks`.
3. Если зелено — коммит.
4. `targetSdk` оставить 36 до отдельного решения.

**Оценка:** ~10 минут.

---

## Self-hosted GitLab Runner через Ansible + Podman

**Статус:** запланировано.

**Что:** развернуть GitLab Runner на домашней машине (i9 13980HX,
64 ГБ RAM, Bluefin 44) через Ansible, используя Podman как
executor. Предусмотреть возможность переключения на удалённый
сервер через inventory.

**Зачем:** квота GitLab Free Tier (400 минут/мес) исчерпана.
Self-hosted даёт бесплатные сборки, полный контроль над
окружением и лучшую производительность.

**Технические детали:**
- Podman как drop-in замена Docker в GitLab Runner 15.1+.
  В `config.toml`: `executor = "docker"`,
  `host = "unix:///run/user/<uid>/podman/podman.sock"`.
- Rootless Podman: без root, без демона, безопаснее.
- Ansible роль: создание пользователя `gitlab-runner`,
  `loginctl enable-linger`, `systemctl --user enable podman.socket`,
  регистрация раннера, настройка `config.toml`.
- Ограничения ресурсов: `--memory=16g`, `--cpus=8` (с запасом,
  начать с 8g/4).
- Кэш Gradle монтируется с хоста для ускорения сборок.

**Бесшовное переключение:**
- Inventory с двумя хостами: `local` и `remote`.
- Раннеры регистрируются с разными тегами (`home`, `remote`).
- Переключение — смена тега в `.gitlab-ci.yml` или через
  переменную CI/CD.

**План:**
1. Написать Ansible роль для GitLab Runner + Podman.
2. Протестировать на домашней машине (`ansible-playbook -i inventory.yml playbook.yml`).
3. Зарегистрировать раннер в проекте.
4. Перевести job'ы `unit-tests`, `lint`, `assemble-debug` на self-hosted.
5. `build-release` и `publish-release` оставить на GitLab SaaS
   или перенести после проверки.

**Оценка:** ~2–3 часа на написание роли и отладку.
**Когда:** после решения вопроса с Gradle upgrade.

---

## Переход на GitHub Environment secrets

**Статус:** запланировано. Отложено до появления триггера.

**Что:** перенести 4 секрета release-сборки
(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`) из **Repository secrets** в
**Environment secrets**.

**Чем отличаются Environment secrets:**

- **Required reviewers** — job не стартует, пока человек не
  одобрит. Для одиночного проекта бесполезно: GitHub, как и
  GitLab, не даёт approve свой же workflow. Работает только
  при наличии второго Maintainer'а.
- **Wait timer** — принудительная задержка перед запуском.
  Защита от случайного нажатия.
- **Deployment branches and tags** — секрет доступен только
  для указанных веток или тегов (например, `v*`).
  Repository secrets, наоборот, доступны любому workflow в
  любой момент.
- **Разделение окружений** — если появится staging/production,
  разные значения для каждого.

**Что Repository secrets НЕ умеют:** ограничение по веткам,
approval, per-environment значения. Всё остальное совпадает —
оба варианта хранят значения в зашифрованном виде, оба
доступны в `${{ secrets.NAME }}` без изменений в синтаксисе.

**Как перейти (когда наступит триггер):**

1. `Settings → Environments` → создать environment
   `release`.
2. Добавить 4 секрета туда.
3. Удалить их из Repository secrets.
4. В `release.yml` добавить `environment: release` в job
   `build-and-release`.

**Когда вернуться:**
- при появлении второго Maintainer'а;
- при первом релизе, требующем approval-гейта;
- при разделении staging/production окружений.

**Оценка:** ~15 минут.

---





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

### ✅ Версионирование debug-сборок по последнему релизу

`versionName` debug-сборок получает префикс последнего релизного
тега: `1.0.2-dev.26.10.08.1430` вместо `1.26.10.08.1430`. Видно,
от какого релиза отпочковалась сборка.

Реализовано функцией `lastReleaseVersion()` в
`app/build.gradle.kts` — вызывает
`git tag --sort=-v:refname --list 'v*'`, fallback `0.0.0` при
отсутствии тегов. В `.gitlab-ci.yml` добавлен глобальный
`GIT_DEPTH: 0`, чтобы теги были видны в job'ах `test`-стадии.

Release-сборки не затронуты: при наличии
`-PreleaseVersionName=X.Y.Z` git не вызывается.

### ✅ Обновление Gradle 9.3.1 → 9.8.1

Wrapper обновлён до 9.8.1. Локальная сборка зелёная,
предупреждений об устаревании Kotlin DSL не осталось.

Попутно устранены устаревшие делегаты `by tasks.registering`
(удалены в Gradle 9.6) — заменены на
`tasks.register<Copy>("name") { ... }` в `build.gradle.kts`.

Kotlin 2.4.20 официально поддерживает Gradle до 9.7.0;
9.8.1 вне заявленного диапазона, но работает. Вернуться при
апгрейде Kotlin, если появятся проблемы.

### ✅ Обновление AGP 9.0.0 → 9.4.0

AGP обновлён до 9.4.0. Требования: Gradle 9.6.0+ (у нас 9.8.1),
JDK 17+ (у нас 25). Kotlin 2.4.20 и KSP 2.3.12 совместимы.

Плагин `org.jetbrains.kotlin.android` остался объявленным в root
build-скрипте. AGP 9.x его не требует, но и не конфликтует с ним —
сборка проходит с обоими. Удаление плагина — отдельная задача
по чистке, не часть этого апгрейда.

### ✅ Обновление зависимостей до актуальных версий

Обновлены:

- `androidx.core:core` 1.18.0 → 1.19.1
- `androidx.lifecycle:lifecycle-runtime-ktx` 2.9.4 → 2.11.0
- Compose BOM 2025.12.00 → 2026.09.00
- `androidx.navigation:navigation-compose` 2.9.8 → 2.10.2
- `kotlinx-coroutines-android` 1.10.2 → 1.11.0
- `kotlinx-coroutines-test` 1.10.2 → 1.11.0
- `io.grpc:*` 1.80.0 → 1.84.1
- `com.squareup.okhttp3:okhttp` 5.3.2 → 5.4.0
- `com.google.code.gson:gson` 2.13.2 → 2.14.0

Попутно:

- Убраны явные версии у `activity-compose`,
  `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` —
  управляются Compose BOM.
- Удалён дублирующий `grpc-stub` из блока «ManagedChannel»;
  он и так приходит транзитивно через `grpc-kotlin-stub` и
  `grpc-protobuf-lite`.

**Не трогали:** `protobuf-java` и `protobuf-kotlin` остаются
`3.25.8` — намеренный pin для совместимости с gRPC и
T-Invest SDK.

### ✅ Обновление GitHub Actions до Node.js 24

- `actions/checkout`: v4 → v6
- `actions/upload-artifact`: v4 → v7

GitHub форсит Node.js 24 для actions с 2 июня 2026;
Node.js 20 будет удалён с раннеров 16 сентября 2026.
`actions/setup-java@v5` и `gradle/actions/setup-gradle@v5`
уже были на Node.js 24.

### ✅ Переезд CI на GitHub Actions

CI/CD перенесён с GitLab на GitHub Actions.

- `ci.yml` — `testDebugUnitTest`, `lintDebug`,
  `assembleDebug` на push и PR в `dev` / `master`.
- `release.yml` — сборка и публикация release APK на тег
  `v*`, плюс ручной запуск через `workflow_dispatch` с
  input `tag` для повторных релизов существующих тегов.
- Repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS`, `KEY_PASSWORD`.
- Rulesets: `Protect master` (PR, status checks, restrict
  deletions, block force pushes), `Protect release tags`
  (restrict updates, restrict deletions; для создания —
  bypass для Repository admin).
- Стратегия merge для PR `dev → master` — **Merge commit**
  (не squash, не rebase: они переписывают SHA и вызывают
  расхождение веток).

Первый релиз: `v1.0.3` — успешно собран и опубликован
в GitHub Releases.

`.gitlab-ci.yml` сохранён для будущего восстановления
пайплайна через self-hosted runner (см. ROADMAP →
«Self-hosted GitLab Runner через Ansible + Podman»).

### ✅ Renovate: self-hosted через GitHub Actions

Автоматическое отслеживание обновлений Gradle-зависимостей
через self-hosted Renovate в GitHub Actions.

- `renovate.json` — группировка Kotlin/KSP/Compose Compiler,
  Hilt, AndroidX Compose, gRPC. `protobuf-*` отключены
  (намеренный pin 3.25.8 для GeneratedMessageV3).
- `.github/workflows/renovate.yml` — запуск раз в неделю по
  расписанию (суббота 00:00 UTC) + `workflow_dispatch` для
  ручного теста. Расписание только в cron; в `renovate.json`
  блоки `schedule` и `timezone` отсутствуют.
- PAT бот-аккаунта `tscalp-renovate-bot` (scope `repo` +
  `workflow`), хранится как Secret `RENOVATE_TOKEN`.
  Срок действия 90 дней — обновлять по уведомлению.
- Default branch в GitHub переключён с `master` на `dev`:
  workflows индексируются только из default branch.

### ✅ Coverage в unit-tests

Инструмент — **Kover** (не JaCoCo). Миграция выполнена.

- `kover {}` в `app/build.gradle.kts`: exclusions для Hilt,
  Compose, BuildConfig, Dagger; порог `minBound(3)` по инструкциям.
- CI: `koverXmlReportDebug`, `koverHtmlReportDebug`,
  `koverVerifyDebug` в `ci.yml`.
- Итоговое покрытие: **4.53%** инструкций, 7.86% веток.
- Отчёт сохраняется в артефактах CI, HTML доступен в браузере.

**Отложено:** per-class пороги (`PositionFormatter` 70%,
`CurrencyUtils` 70%, `CalculateTradeDetailsUseCase` 50% и т.д.).
Синтаксис per-class правил в Kover требует проверки — сначала
работает общий порог. Вернуться при следующей итерации по
покрытию.

**Итог:** JaCoCo полностью удалён из проекта, Kover единственный
инструмент покрытия.

---
