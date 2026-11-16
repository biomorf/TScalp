<!-- SECTION_BEGIN: clean-audit-L8-ARCHIVE -->
## L8 — app (архив)










<!-- SUBSECTION_BEGIN: clean-audit-101 -->
___
### Исправленные (L8)
<!-- SUBSECTION_END: clean-audit-101 -->





<!-- SUBSECTION_BEGIN: Н49 -->
___
### Н49 ✅ Н49. TScalpApplication → util.TInvestErrorMessages + util.AppLogger

**Статус:** исправлено (PR #70, `dev`, 2026-10-29).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `installGrpcExceptionGuard` использует `util.TInvestErrorMessages.messages[description]` (T-Invest-специфичный маппинг кодов) + `util.AppLogger` (платформа). App видит util.

**Нарушенное правило Clean:** dependency rule — `TInvestErrorMessages` — data-knowledge (T-Invest-специфика), не должен быть в `util` и не должен быть доступен в app напрямую.

**Clean-альтернатива:** `TInvestErrorMessages` → `data/error/TInvestErrorMapper` (часть H20). `AppLogger` → `Logger`-интерфейс (H19). Плюсы: app не видит T-Invest-специфику. Минусы: зависит от H19/H20.

**Выгоды:** —.

**Что сделано:** `TInvestErrorMessages` переехал в `data/api/error/TInvestErrorMessages.kt` (PR #70 — вместе с H20); `AppLogger` заменён на `Logger`-интерфейс (H19, PR #70); `TScalpApplication` импортирует `data.api.error.TInvestErrorMessages` — для composition root допустимо (2c, как Н48). Нарушения «app → util» нет.

**Ссылка на задачу:** PR #70 (H17–H20); H19, H20 (закрыты).

<!-- SUBSECTION_END: Н49 -->

<!-- SUBSECTION_BEGIN: Н50 -->
___
### Н50 ✅ Н50. restoreTInvest содержит domain-логику выбора счёта

**Статус:** исправлено (PR #77, `dev`, 2026-10-31).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `restoreTInvest` + `resolveTInvestAccountId` + `tryOpenSandboxAccount` — логика выбора счёта при старте (savedAccountId → accounts → sandbox-fallback). Это domain-логика (правила выбора счёта), в `TScalpApplication`.

**Нарушенное правило Clean:** business logic (выбор счёта) в composition root.

**Clean-альтернатива:** `BrokerLifecycleManager` (domain-сервис в `domain/api/managers/`): `suspend fun restoreTInvest(broker)`, `restoreBcs(broker)` (scope расширен — `restoreBcs` в `TScalpApplication` тоже содержит domain-логику), `private resolveTInvestAccountId(...)`, `private tryOpenSandboxAccount(...)`. Конструктор: `BrokerRegistry`, `SettingsRepository` (domain.api.repository), `PositionFlowManager`, `Logger`. `TScalpApplication` делегирует (`when (brokerName)`). Плюсы: domain-логика в domain; общий менеджер для TInvest/BCS. Минусы: новый сервис (~2–2.5 ч), тесты с mock.

**Выгоды:** —.

**Что сделано:** новый `domain/api/managers/BrokerLifecycleManager.kt` — `suspend restoreTInvest(broker)`, `restoreBcs(broker)`, `private resolveTInvestAccountId(...)`, `private tryOpenSandboxAccount(...)`; конструктор `BrokerRegistry` + `SettingsRepository` + `PositionFlowManager` + `Logger`; `TScalpApplication` делегирует через `when (brokerName)`, 4 метода удалены (−116 строк); `BrokerLifecycleManagerTest` — 6 тестов PASSED.

**Ссылка на задачу:** PR #77 (`dev`, 2026-10-31), коммит `e194fc8`.

---

<!-- SUBSECTION_END: Н50 -->

<!-- SECTION_END: clean-audit-L8-ARCHIVE -->
