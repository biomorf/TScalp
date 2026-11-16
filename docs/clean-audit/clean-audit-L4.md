<!-- SECTION_BEGIN: clean-audit-L4 -->
## L4 — domain/api










<!-- SUBSECTION_BEGIN: clean-audit-48 -->
___
### Принятые (2c)
<!-- SUBSECTION_END: clean-audit-48 -->





<!-- SUBSECTION_BEGIN: Н27 -->
___
### Н27 Н27. StreamsApi.subscribeOrderState — suspend

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `StreamsApi.subscribePositions` / `subscribeLastPrices` — non-suspend; `subscribeOrderState` — suspend. KDoc: «как в `BrokerApi.subscribeOrderState` (контракт совпадает с fat-интерфейсом)».

**Нарушенное правило Clean:** не Clean-инвариант, а консистентность роли — 2 метода non-suspend, 1 suspend, без технической необходимости.

**Clean-альтернатива:** убрать `suspend` у `subscribeOrderState` (обернуть в `flow { }` внутри реализации). Плюсы: консистентность. Минусы: мелочь.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Может быть частью Н25.

<!-- SUBSECTION_END: Н27 -->

<!-- SUBSECTION_BEGIN: Н28 -->
___
### Н28 Н28. AccountsApi.getAccounts(sandboxMode) (принято 2c)

**Статус:** принято 2c.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** общий `AccountsApi` принимает `sandboxMode: Boolean`. KDoc: «У брокеров без sandbox (BCS/Finam) параметр игнорируется». Domain-контракт роли несёт специфику T-Invest.

**Нарушенное правило Clean:** domain-контракт роли несёт специфику T-Invest (sandbox); BCS/Finam игнорируют — параметр маскирует «не применимо» под «применимо, но неважно».

**Clean-альтернатива:** вынести `sandboxMode` в `TInvestAccountsApi : AccountsApi` (T-Invest-специфика). Или `AccountsApi` без параметра, sandbox-специфика — в реализации. Плюсы: чистый общий контракт. Минусы: переделка TInvest + `InvestRepository`.

**Выгоды (по 2c):** 1 callsite (`AccountsRepositoryImpl:49`), 2 impl (`TInvestBrokerApi`, `BcsBrokerApi`/`FinamBrokerApi` — через `accounts = null`). Refactor (`TInvestAccountsApi : AccountsApi`) → правка 2 impl + 1 callsite + новый интерфейс — overhead > текущей выгоды. Параметр `sandboxMode` отражает реальность: у T-Invest sandbox есть, у BCS/Finam — нет (игнорируется). Domain-контракт терпит это как «общий параметр, иногда не применим».

**Ссылка на задачу:** `### ✅ 2c-фиксация: Н28 (AccountsApi sandboxMode) + Н15 (TradingStateSnapshot)` (`docs/roadmap/ROADMAP-ARCHIVE.md`, merge `eb2db6a8`, 2026-11-11).

<!-- SUBSECTION_END: Н28 -->

<!-- SECTION_END: clean-audit-L4 -->
