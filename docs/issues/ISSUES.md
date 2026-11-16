# ISSUES — индекс

Известные проблемы и отложенные решения проекта TScalp.
Актуальное — в файлах по компонентам. Архив —
`ISSUES-ARCHIVE.md`. Шаблон — `ISSUES-task-template.md`.

## Компоненты

- `ISSUES-streams.md` — стримы, FlowManager, WS/gRPC transport.
- `ISSUES-brokers.md` — брокеры (T-Invest, BCS, Finam), gRPC, REST.
- `ISSUES-domain.md` — domain, ViewModel, UseCase, lifecycle.
- `ISSUES-data.md` — data-слой, репозитории, мапперы.
- `ISSUES-di.md` — Hilt, DI-модули.
- `ISSUES-ci.md` — CI/CD, сборка, Gradle.
- `ISSUES-ui.md` — UI, Compose (пока пуст).
- `ISSUES-misc.md` — документация, системные находки.

## Проблемы

| TOKEN | ID | Title | Component | File |
|---|---|---|---|---|
| DONE | I-1 | OrderSearchViewModel: selectedBroker не влияет на API поиска (решён) | domain | `docs/issues/ISSUES-domain.md` |
| OPEN | I-2 | Race в start/stop *FlowManager — дублирование стримов | streams | `docs/issues/ISSUES-streams.md` |
| OPEN | I-3 | Separator между секциями ROADMAP не везде по шаблону | misc | `docs/issues/ISSUES-misc.md` |
| OPEN | I-4 | Trailing whitespace в docs/*.md | misc | `docs/issues/ISSUES-misc.md` |
| DEFER | I-5 | Gradle 10: устаревшие API в сторонних плагинах | ci | `docs/issues/ISSUES-ci.md` |
| OPEN | I-6 | INVALID_ARGUMENT: 30052 при торговле акциями | brokers | `docs/issues/ISSUES-brokers.md` |
| DONE | I-7 | Позиции не обновляются при смене счёта | streams | `docs/issues/ISSUES-streams.md` |
| WIP | I-8 | GrpcExceptionGuard не установлен | brokers | `docs/issues/ISSUES-brokers.md` |
| DONE | I-9 | PositionsStream не доставляет данные | streams | `docs/issues/ISSUES-streams.md` |
| DONE | I-10 | T-Invest WebSocket: решён — канал должен быть в URL path | streams | `docs/issues/ISSUES-streams.md` |
| DONE | I-11 | restoreTInvest не использует сохранённый accountId при падении getAccounts | domain | `docs/issues/ISSUES-domain.md` |
| OPEN | I-12 | gRPC retry: string matching NOT_FOUND вместо Status.Code | brokers | `docs/issues/ISSUES-brokers.md` |
| OPEN | I-13 | gRPC retry: бесконечный retry на не-recoverable ошибках | brokers | `docs/issues/ISSUES-brokers.md` |
| DONE | I-14 | TInvestBrokerAPI.initialize() не закрывает предыдущий positionsWebSocket | streams | `docs/issues/ISSUES-streams.md` |
| DONE | I-15 | BcsBrokerApi логирует refreshToken (решён) | brokers | `docs/issues/ISSUES-brokers.md` |
| OPEN | I-16 | BcsBrokerApi создаёт OkHttpClient без CustomTrustManager | brokers | `docs/issues/ISSUES-brokers.md` |
| OPEN | I-17 | PriceFlowManager.collectStream: потенциальный busy loop | streams | `docs/issues/ISSUES-streams.md` |
| OPEN | I-18 | PriceFlowManager.runPolling swallows CancellationException | streams | `docs/issues/ISSUES-streams.md` |
| OPEN | I-19 | PositionsStream over WS не отдаёт данные в песочнице | streams | `docs/issues/ISSUES-streams.md` |
| OPEN | I-20 | INTERNAL: 70001 при параллельных unary-запросах | brokers | `docs/issues/ISSUES-brokers.md` |
| DONE | I-21 | Дублирующий init {} в SettingsViewModel | domain | `docs/issues/ISSUES-domain.md` |
| DEFER | I-22 | Strip warnings в debug APK | ci | `docs/issues/ISSUES-ci.md` |
| OPEN | I-23 | Асимметрия lifecycle: старт менеджеров после login vs restore | domain | `docs/issues/ISSUES-domain.md` |
| OPEN | I-24 | T-Invest REST findInstruments fallback (N+1 unary) | brokers | `docs/issues/ISSUES-brokers.md` |
| DONE | I-25 | AGENTS.md ссылался на удалённое `InstrumentUi.ttech_uid` | misc | `docs/issues/ISSUES-misc.md` |
| DONE | I-26 | Currency format: lowercase iso-code в `Currency.getInstance` | data | `docs/issues/ISSUES-data.md` |
| OPEN | I-27 | REST poll failed: REST transport not initialized (W при disconnect) | streams | `docs/issues/ISSUES-streams.md` |
| OPEN | I-28 | BrokerRegistry.anyInitialized не сбрасывается после DisconnectBrokerUseCase | di | `docs/issues/ISSUES-di.md` |
| OPEN | I-29 | PriceFlowManager retry loop после logout при неактивных VM | streams | `docs/issues/ISSUES-streams.md` |
| OPEN | I-30 | System: A resource failed to call release (lifecycle-находка) | misc | `docs/issues/ISSUES-misc.md` |
| DONE | I-31 | ClassCastException в PostOrderUseCase при StopLoss без stopPrice | domain | `docs/issues/ISSUES-domain.md` |
| OPEN | I-32 | T-Invest rate-limit umbrella: cold start + search RESOURCE_EXHAUSTED | brokers | `docs/issues/ISSUES-brokers.md` |
