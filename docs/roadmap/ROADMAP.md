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

**Next ID:** T-47

| TOKEN | ID | Title | File | Anchor |
|---|---|---|---|---|
| WIP | T-44 | SSoT стиля документации (docs/docs-structure.md) + миграция всех файлов и инструментов | `docs/roadmap/ROADMAP-wip.md` | [#t-44](docs/roadmap/ROADMAP-wip.md#t-44) |
| WIP | T-3 | Rate-limit umbrella: cold start + search RESOURCE_EXHAUSTED | `docs/roadmap/ROADMAP-wip.md` | [#t-3](docs/roadmap/ROADMAP-wip.md#t-3) |
| WIP | T-33 | Установка GrpcExceptionGuard | `docs/roadmap/ROADMAP-wip.md` | [#t-33](docs/roadmap/ROADMAP-wip.md#t-33) |
| PLAN | T-2 | AGENTS: консолидация скриптов в AGENTS/scripts + инструкции отдельно | `docs/roadmap/ROADMAP-plan.md` | [#t-2](docs/roadmap/ROADMAP-plan.md#t-2) |
| PLAN | T-4 | Rate-limiter: GetUserTariff (dynamic limits) | `docs/roadmap/ROADMAP-plan.md` | [#t-4](docs/roadmap/ROADMAP-plan.md#t-4) |
| PLAN | T-40 | PortfolioUiState: passthrough tradingStatuses StateFlow | `docs/roadmap/ROADMAP-plan.md` | [#t-40](docs/roadmap/ROADMAP-plan.md#t-40) |
| PLAN | T-41 | Orders: миграция tradingStatuses на TradingStatusFlowManager | `docs/roadmap/ROADMAP-plan.md` | [#t-41](docs/roadmap/ROADMAP-plan.md#t-41) |
| PLAN | T-42 | TradingStatus: stream API (subscribeTradingStatuses) | `docs/roadmap/ROADMAP-plan.md` | [#t-42](docs/roadmap/ROADMAP-plan.md#t-42) |
| PLAN | T-9 | PortfolioScreen: double-load + TradingStatusFlowManager | `docs/roadmap/ROADMAP-plan.md` | [#t-9](docs/roadmap/ROADMAP-plan.md#t-9) |
| PLAN | T-10 | TInvest*Service + *Api: unary → AppResult | `docs/roadmap/ROADMAP-plan.md` | [#t-10](docs/roadmap/ROADMAP-plan.md#t-10) |
| PLAN | T-11 | C2: SaveFormStateUseCase primitives (M4/M7 fix, pre-existing) | `docs/roadmap/ROADMAP-plan.md` | [#t-11](docs/roadmap/ROADMAP-plan.md#t-11) |
| PLAN | T-12 | Архитектурный документ (docs/architecture.md) | `docs/roadmap/ROADMAP-plan.md` | [#t-12](docs/roadmap/ROADMAP-plan.md#t-12) |
| PLAN | T-37 | docs/live-test.md: mobile-ограничения (Robolectric vs device) | `docs/roadmap/ROADMAP-plan.md` | [#t-37](docs/roadmap/ROADMAP-plan.md#t-37) |
| PLAN | T-38 | Live-test: C2 Orders form 7 сценариев | `docs/roadmap/ROADMAP-plan.md` | [#t-38](docs/roadmap/ROADMAP-plan.md#t-38) |
| PLAN | T-45 | Миграция docs/*.md под канон | `docs/roadmap/ROADMAP-plan.md` | [#t-45](docs/roadmap/ROADMAP-plan.md#t-45) |
| DEFER | T-21 | KMP: domain → commonMain | `docs/roadmap/ROADMAP-defer.md` | [#t-21](docs/roadmap/ROADMAP-defer.md#t-21) |
| DEFER | T-22 | Плавный переход Hilt → Koin | `docs/roadmap/ROADMAP-defer.md` | [#t-22](docs/roadmap/ROADMAP-defer.md#t-22) |
| DEFER | T-34 | Расшифровка кодов ошибок брокеров | `docs/roadmap/ROADMAP-defer.md` | [#t-34](docs/roadmap/ROADMAP-defer.md#t-34) |
| DEFER | T-14 | UX-flow Variant D: broker + account до поиска | `docs/roadmap/ROADMAP-defer.md` | [#t-14](docs/roadmap/ROADMAP-defer.md#t-14) |
| DEFER | T-15 | Рефакторинг архитектурного долга (detekt-группа 3) | `docs/roadmap/ROADMAP-defer.md` | [#t-15](docs/roadmap/ROADMAP-defer.md#t-15) |
| DEFER | T-16 | Orders: P&L в песочнице (expectedYield) | `docs/roadmap/ROADMAP-defer.md` | [#t-16](docs/roadmap/ROADMAP-defer.md#t-16) |
| DEFER | T-17 | Margin/risk → TradeCheckResult | `docs/roadmap/ROADMAP-defer.md` | [#t-17](docs/roadmap/ROADMAP-defer.md#t-17) |
| DEFER | T-18 | T-Invest: subtype-specific поля в mapShare / mapFuture / mapBond / mapOption / mapEtf / mapCurrency | `docs/roadmap/ROADMAP-defer.md` | [#t-18](docs/roadmap/ROADMAP-defer.md#t-18) |
| DEFER | T-19 | Position: источники priceChangePercent и lockedQuantity | `docs/roadmap/ROADMAP-defer.md` | [#t-19](docs/roadmap/ROADMAP-defer.md#t-19) |
| DEFER | T-32 | Live-test Broker lifecycle API: BCS/Finam | `docs/roadmap/ROADMAP-defer.md` | [#t-32](docs/roadmap/ROADMAP-defer.md#t-32) |
| DEFER | T-39 | Search: drop statuses (variant D for T-7) | `docs/roadmap/ROADMAP-defer.md` | [#t-39](docs/roadmap/ROADMAP-defer.md#t-39) |
| DEFER | T-23 | UI: показать executedPrice / executedQuantity на карточке | `docs/roadmap/ROADMAP-defer.md` | [#t-23](docs/roadmap/ROADMAP-defer.md#t-23) |
| DEFER | T-24 | UI-индикатор состояния стримов | `docs/roadmap/ROADMAP-defer.md` | [#t-24](docs/roadmap/ROADMAP-defer.md#t-24) |
| DEFER | T-25 | UI: разбивка AccountBalance.total на money/blocked в шапке счёта | `docs/roadmap/ROADMAP-defer.md` | [#t-25](docs/roadmap/ROADMAP-defer.md#t-25) |
| DEFER | T-26 | UI выбора даты истечения стоп-заявки (GOOD_TILL_DATE) | `docs/roadmap/ROADMAP-defer.md` | [#t-26](docs/roadmap/ROADMAP-defer.md#t-26) |
| DEFER | T-27 | Индикатор доступности инструмента в карточке позиции | `docs/roadmap/ROADMAP-defer.md` | [#t-27](docs/roadmap/ROADMAP-defer.md#t-27) |
| DEFER | T-35 | OrdersListDialog: empty-state | `docs/roadmap/ROADMAP-defer.md` | [#t-35](docs/roadmap/ROADMAP-defer.md#t-35) |
| DEFER | T-36 | Settings: inline status card vs snackbar | `docs/roadmap/ROADMAP-defer.md` | [#t-36](docs/roadmap/ROADMAP-defer.md#t-36) |
| DEFER | T-46 | Общий RateLimitedException для всех брокеров | `docs/roadmap/ROADMAP-defer.md` | [#t-46](docs/roadmap/ROADMAP-defer.md#t-46) |
| DEFER | T-28 | P&L: sandbox client-side fallback для Position (низкий приоритет) | `docs/roadmap/ROADMAP-defer.md` | [#t-28](docs/roadmap/ROADMAP-defer.md#t-28) |
| DONE | T-13 | Compose UI: test infra + 3 экрана — ✅ done (U0–U4) | `docs/roadmap/ROADMAP-done.md` | [#t-13](docs/roadmap/ROADMAP-done.md#t-13) |
| DONE | T-1 | docs/: рефакторинг журналов (ROADMAP, ci-roadmap, ISSUES, clean-audit) | `docs/roadmap/ROADMAP-done.md` | [#t-1](docs/roadmap/ROADMAP-done.md#t-1) |

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
