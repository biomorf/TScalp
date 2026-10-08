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

### Renovate для обновлений зависимостей

**Что:** подключить Renovate (или Dependabot) для
автоматического отслеживания обновлений Gradle-зависимостей.

**Зачем:** динамические lint-детекторы `GradleDependency` и
`NewerVersionAvailable` отключены — несовместимы с baseline.
Renovate делает то же самое, но правильно: открывает MR
с обновлением и не блокирует чужие сборки.

**Оценка:** ~1 час на настройку.
**Когда:** при следующем апгрейде зависимостей.

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

## Переезд CI на GitHub Actions

**Статус:** запланировано. Блокер: квота GitLab Free Tier
исчерпана.

**Ситуация:** GitLab Free Tier даёт 400 compute-минут в месяц.
Квота обновляется ежемесячно, но текущий объём пайплайнов
(4 job'а на каждый push, 2–4 минуты каждый) упирается в лимит
задолго до конца месяца. Пайплайны зависают в очереди.

**Решение:** перенести CI/CD на GitHub Actions.

- GitHub Actions для **публичных** репозиториев — бесплатно,
  без лимита минут на стандартных раннерах.
- Репозиторий уже зеркалируется на GitHub (`github` remote).
- Деплой релизов остаётся на GitLab Releases (там Protected
  Tags, переменные keystore).

**Объём:**
1. Создать `.github/workflows/ci.yml` — три job'а:
   `unit-tests`, `lint`, `assemble-debug` на push/MR.
2. Создать `.github/workflows/release.yml` — сборка и подпись
   release APK на тег `v*`.
3. Переменные: keystore и пароли через GitHub Secrets.
4. Публикация релиза: либо в GitHub Releases, либо триггерить
   GitLab pipeline через API (сложнее).
5. Решить, что делать с `.gitlab-ci.yml`: удалить, оставить
   как backup, или отключить через `workflow: rules`.

**Альтернативы:**
- **Self-hosted runner на GitLab** — бесплатно, но нужна
  машина и настройка.
- **Купить минуты GitLab** — ~$10 за 1000 минут.
- **Сделать GitLab-репозиторий публичным** — снимает лимит,
  но код становится открытым.

**Оценка:** ~2–3 часа на настройку GitHub Actions
(без учёта переписывания publish-release).

**Когда:** при первой необходимости в CI
(текущие локальные проверки покрывают основное).

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

---

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

---
