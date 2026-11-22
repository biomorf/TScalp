<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: gate -->
## Гейт

- `-wip` — в работе (ближайшее к выполнению).
- `-plan` — запланировано (по приоритету).
- `-defer` — отложено (до появления условий).
- `-done` — сделано, ожидает `mark done` → архив.
- `ROADMAP-ARCHIVE.md` — закрытые (не трогать без mark done).

---










<!-- SECTION_END: gate -->










<!-- SECTION_BEGIN: tasks -->
## Задачи

**Next ID:** T-48

| TOKEN | ID | Title | File | Anchor |
|---|---|---|---|---|
| WIP | T-3 | Rate-limit umbrella: cold start + search RESOURCE_EXHAUSTED | `docs/roadmap/ROADMAP-wip.md` | [#t-3](ROADMAP-wip.md#t-3) |
| WIP | T-33 | Установка GrpcExceptionGuard | `docs/roadmap/ROADMAP-wip.md` | [#t-33](ROADMAP-wip.md#t-33) |
| WIP | T-45 | Миграция docs/*.md под канон | `docs/roadmap/ROADMAP-wip.md` | [#t-45](ROADMAP-wip.md#t-45) |
| PLAN | T-4 | Rate-limiter: GetUserTariff (dynamic limits) | `docs/roadmap/ROADMAP-plan.md` | [#t-4](ROADMAP-plan.md#t-4) |
| PLAN | T-40 | PortfolioUiState: passthrough tradingStatuses StateFlow | `docs/roadmap/ROADMAP-plan.md` | [#t-40](ROADMAP-plan.md#t-40) |
| PLAN | T-41 | Orders: миграция tradingStatuses на TradingStatusFlowManager | `docs/roadmap/ROADMAP-plan.md` | [#t-41](ROADMAP-plan.md#t-41) |
| PLAN | T-42 | TradingStatus: stream API (subscribeTradingStatuses) | `docs/roadmap/ROADMAP-plan.md` | [#t-42](ROADMAP-plan.md#t-42) |
| PLAN | T-9 | PortfolioScreen: double-load + TradingStatusFlowManager | `docs/roadmap/ROADMAP-plan.md` | [#t-9](ROADMAP-plan.md#t-9) |
| PLAN | T-10 | TInvest*Service + *Api: unary → AppResult | `docs/roadmap/ROADMAP-plan.md` | [#t-10](ROADMAP-plan.md#t-10) |
| PLAN | T-11 | C2: SaveFormStateUseCase primitives (M4/M7 fix, pre-existing) | `docs/roadmap/ROADMAP-plan.md` | [#t-11](ROADMAP-plan.md#t-11) |
| PLAN | T-12 | Архитектурный документ (docs/architecture.md) | `docs/roadmap/ROADMAP-plan.md` | [#t-12](ROADMAP-plan.md#t-12) |
| PLAN | T-37 | docs/live-test.md: mobile-ограничения (Robolectric vs device) | `docs/roadmap/ROADMAP-plan.md` | [#t-37](ROADMAP-plan.md#t-37) |
| PLAN | T-38 | Live-test: C2 Orders form 7 сценариев | `docs/roadmap/ROADMAP-plan.md` | [#t-38](ROADMAP-plan.md#t-38) |
| DEFER | T-21 | KMP: domain → commonMain | `docs/roadmap/ROADMAP-defer.md` | [#t-21](ROADMAP-defer.md#t-21) |
| DEFER | T-22 | Плавный переход Hilt → Koin | `docs/roadmap/ROADMAP-defer.md` | [#t-22](ROADMAP-defer.md#t-22) |
| DEFER | T-34 | Расшифровка кодов ошибок брокеров | `docs/roadmap/ROADMAP-defer.md` | [#t-34](ROADMAP-defer.md#t-34) |
| DEFER | T-14 | UX-flow Variant D: broker + account до поиска | `docs/roadmap/ROADMAP-defer.md` | [#t-14](ROADMAP-defer.md#t-14) |
| DEFER | T-15 | Рефакторинг архитектурного долга (detekt-группа 3) | `docs/roadmap/ROADMAP-defer.md` | [#t-15](ROADMAP-defer.md#t-15) |
| DEFER | T-16 | Orders: P&L в песочнице (expectedYield) | `docs/roadmap/ROADMAP-defer.md` | [#t-16](ROADMAP-defer.md#t-16) |
| DEFER | T-17 | Margin/risk → TradeCheckResult | `docs/roadmap/ROADMAP-defer.md` | [#t-17](ROADMAP-defer.md#t-17) |
| DEFER | T-18 | T-Invest: subtype-specific поля в mapShare / mapFuture / mapBond / mapOption / mapEtf / mapCurrency | `docs/roadmap/ROADMAP-defer.md` | [#t-18](ROADMAP-defer.md#t-18) |
| DEFER | T-19 | Position: источники priceChangePercent и lockedQuantity | `docs/roadmap/ROADMAP-defer.md` | [#t-19](ROADMAP-defer.md#t-19) |
| DEFER | T-32 | Live-test Broker lifecycle API: BCS/Finam | `docs/roadmap/ROADMAP-defer.md` | [#t-32](ROADMAP-defer.md#t-32) |
| DEFER | T-39 | Search: drop statuses (variant D for T-7) | `docs/roadmap/ROADMAP-defer.md` | [#t-39](ROADMAP-defer.md#t-39) |
| DEFER | T-23 | UI: показать executedPrice / executedQuantity на карточке | `docs/roadmap/ROADMAP-defer.md` | [#t-23](ROADMAP-defer.md#t-23) |
| DEFER | T-24 | UI-индикатор состояния стримов | `docs/roadmap/ROADMAP-defer.md` | [#t-24](ROADMAP-defer.md#t-24) |
| DEFER | T-25 | UI: разбивка AccountBalance.total на money/blocked в шапке счёта | `docs/roadmap/ROADMAP-defer.md` | [#t-25](ROADMAP-defer.md#t-25) |
| DEFER | T-26 | UI выбора даты истечения стоп-заявки (GOOD_TILL_DATE) | `docs/roadmap/ROADMAP-defer.md` | [#t-26](ROADMAP-defer.md#t-26) |
| DEFER | T-27 | Индикатор доступности инструмента в карточке позиции | `docs/roadmap/ROADMAP-defer.md` | [#t-27](ROADMAP-defer.md#t-27) |
| DEFER | T-35 | OrdersListDialog: empty-state | `docs/roadmap/ROADMAP-defer.md` | [#t-35](ROADMAP-defer.md#t-35) |
| DEFER | T-36 | Settings: inline status card vs snackbar | `docs/roadmap/ROADMAP-defer.md` | [#t-36](ROADMAP-defer.md#t-36) |
| DEFER | T-46 | Общий RateLimitedException для всех брокеров | `docs/roadmap/ROADMAP-defer.md` | [#t-46](ROADMAP-defer.md#t-46) |
| DEFER | T-28 | P&L: sandbox client-side fallback для Position (низкий приоритет) | `docs/roadmap/ROADMAP-defer.md` | [#t-28](ROADMAP-defer.md#t-28) |
| DONE | T-13 | Compose UI: test infra + 3 экрана — ✅ done (U0–U4) | `docs/roadmap/ROADMAP-done.md` | [#t-13](ROADMAP-done.md#t-13) |
| DONE | T-1 | docs/: рефакторинг журналов (ROADMAP, ci-roadmap, ISSUES, clean-audit) | `docs/roadmap/ROADMAP-done.md` | [#t-1](ROADMAP-done.md#t-1) |
| DONE | T-47 | docs_cut: тело блока и обёртка inline.sh | `docs/roadmap/ROADMAP-done.md` | [#t-47](ROADMAP-done.md#t-47) |

| PLAN | T-40 | PortfolioUiState: passthrough tradingStatuses StateFlow | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-41 | Orders: миграция tradingStatuses на TradingStatusFlowManager | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-42 | TradingStatus: stream API (subscribeTradingStatuses) | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-10 | TInvest*Service + *Api: unary → AppResult | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-11 | C2: SaveFormStateUseCase primitives (M4/M7 fix, pre-existing) | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-12 | Архитектурный документ (docs/architecture.md) | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-14 | UX-flow Variant D: broker + account до поиска | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-15 | Рефакторинг архитектурного долга (detekt-группа 3) | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-16 | Orders: P&L в песочнице (expectedYield) | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-17 | Margin/risk → TradeCheckResult | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-18 | T-Invest: subtype-specific поля в mapShare / mapFuture / mapBond / mapOption / mapEtf / mapCurrency | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-19 | Position: источники priceChangePercent и lockedQuantity | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-20 | UI: кнопка swap слотов (primary ↔ pair) | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-21 | KMP: domain → commonMain | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-23 | UI: показать executedPrice / executedQuantity на карточке | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-24 | UI-индикатор состояния стримов | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-25 | UI: разбивка AccountBalance.total на money/blocked в шапке счёта | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-26 | UI выбора даты истечения стоп-заявки (GOOD_TILL_DATE) | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-27 | Индикатор доступности инструмента в карточке позиции | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-28 | P&L: sandbox client-side fallback для Position (низкий приоритет) | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-29 | Полировка debug-переключателей | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-30 | Реализация BCS Broker API | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-31 | Реализация Finam Broker API | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-35 | OrdersListDialog: empty-state | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-36 | Settings: inline status card vs snackbar | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-37 | docs/live-test.md: mobile-ограничения (Robolectric vs device) | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-38 | Live-test: C2 Orders form 7 сценариев | `docs/roadmap/ROADMAP-plan.md` |
| DEFER | T-22 | Плавный переход Hilt → Koin | `docs/roadmap/ROADMAP-defer.md` |
| DEFER | T-32 | Live-test Broker lifecycle API: BCS/Finam | `docs/roadmap/ROADMAP-defer.md` |
| DONE | T-13 | Compose UI: test infra + 3 экрана — ✅ done (U0–U4) | `docs/roadmap/ROADMAP-done.md` |











---










<!-- SECTION_END: tasks -->










<!-- FILE_END -->
