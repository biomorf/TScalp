# ROADMAP — индекс

План задач проекта TScalp. Актуальное — в файлах по статусу.
Архив — `ROADMAP-ARCHIVE.md`. Шаблон задачи —
`ROADMAP-task-template.md`.

## Гейт

- `-wip` — в работе (ближайшее к выполнению).
- `-plan` — запланировано (по приоритету).
- `-defer` — отложено (до появления условий).
- `-done` — сделано, ожидает `mark done` → архив.
- `ROADMAP-ARCHIVE.md` — закрытые (не трогать без mark done).

## Задачи

| TOKEN | ID | Title | File |
|---|---|---|---|
| WIP | T-1 | docs/: рефакторинг журналов (ROADMAP, ci-roadmap, ISSUES, clean-audit) | `docs/roadmap/ROADMAP-wip.md` |
| WIP | T-3 | Rate-limit umbrella: cold start + search RESOURCE_EXHAUSTED | `docs/roadmap/ROADMAP-wip.md` |
| WIP | T-33 | Установка GrpcExceptionGuard | `docs/roadmap/ROADMAP-wip.md` |
| WIP | T-34 | Расшифровка кодов ошибок брокеров | `docs/roadmap/ROADMAP-wip.md` |
| PLAN | T-2 | AGENTS: консолидация скриптов в AGENTS/scripts + инструкции отдельно | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-4 | Rate-limiter: GetUserTariff (dynamic limits) | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-5 | Stream connection limiter: WS + gRPC-streaming | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-6 | Search: сократить N+1 unary в findInstruments | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-7 | updateStatuses: skip on cache hit / lazy on select | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-8 | getTradingStatuses: батчинг / concurrency | `docs/roadmap/ROADMAP-plan.md` |
| PLAN | T-9 | PortfolioScreen: retry с backoff | `docs/roadmap/ROADMAP-plan.md` |
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
