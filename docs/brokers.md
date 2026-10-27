# Brokers

Краткая справка по трём брокерам, с которыми работает проект:
T-Invest, BCS, Finam. Матрица транспортов, авторизация,
ссылки на официальную документацию.

Детали по стримам T-Invest — в `docs/streams.md`. Задачи
реализации — в `docs/ROADMAP.md`.

---

## Матрица транспортов

| Брокер    | gRPC-stream | gRPC-unary | WebSocket | REST |
|-----------|-------------|------------|-----------|------|
| T-Invest  | да          | да         | да        | да   |
| BCS       | нет         | нет        | да        | да   |
| Finam     | да          | да         | да        | да   |

Транспорты описаны в задаче «Оркестратор стримов и Ktor WS
transport» (`docs/ROADMAP.md`).

**Стадия реализации WebSocket:** все три канала T-Invest
(`PositionsStream`, `MarketDataStream`, `OrdersStream`)
реализованы в `:transport-ktor` (`TInvestPositionsWebSocket`,
`TInvestPricesWebSocket`, `TInvestOrderStateWebSocket`),
работают на проде (live-tested 2026-10-21). Sandbox WS не
отдаёт данные (серверное ограничение, см. `docs/streams.md`).
Клиенты создаются только при `!sandbox`.

У BCS и Finam WS указан как поддерживаемый протокол по
документации, но в проекте **не реализован** — этап
планирования.

---

## T-Invest

**URL:** `invest-public-api.tbank.ru`

**Транспорты:**

- gRPC — основной протокол (stream + unary).
- WebSocket — `wss://invest-public-api.tbank.ru/ws/` (prod),
  `wss://sandbox-invest-public-api.tbank.ru/ws/` (sandbox).
  Канал — часть URL path. Реализован для `PositionsStream`
  (`:transport-ktor`). Детали, коды закрытия, ограничение
  sandbox — `docs/streams.md` → «WebSocket (T-Invest)».
- REST — прокси Swagger над gRPC unary-методами:
  `POST /rest/tinkoff.public.invest.api.contract.v1.<Service>/<Method>`.

**Авторизация:** Bearer-токен (read/write зависит от типа
токена, выпускается в личном кабинете).

**Документация:**

- Общая: `https://developer.tbank.ru/invest/intro/developer/`
- Протоколы: `https://developer.tbank.ru/invest/intro/developer/protocols/restapi`
- Stream-соединения: раздел «Stream-соединения».
- AsyncAPI-спецификация WS: репозиторий
  `RussianInvestments/investAPI`, файл `src/docs/ws/asyncapi.yaml`.

**Особенности:**

- Требует российские корневые сертификаты
  (`CustomTrustManager` в проекте).
- SDK: `ru.t-technologies.invest.piapi.kotlin:kotlin-sdk-grpc-core`.
- Лимиты: gRPC unary — 6 запросов в секунду на пользователя.

---

## BCS

**URL:** `https://trade-api.bcs.ru/`

**Транспорты:**

- REST — HTTP endpoints вида `/api/v1/...`. Заявки,
  инструменты, портфель, сделки, расписание торгов.
- WebSocket — `wss://ws.broker.ru/trade-api-*/api/v1/.../ws`.
  Котировки, статус заявок, лимиты.
- gRPC — нет.

**Авторизация:** OAuth2 через Keycloak.

- Endpoint: `POST /trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token`.
- `grant_type=refresh_token`.
- `client_id`:
  - `trade-api-read` — только чтение;
  - `trade-api-write` — торговля.
- Токены: refresh — 90 дней (выдаётся в личном кабинете),
  access — 24 часа.

**Документация:**

- `https://trade-api.bcs.ru/`
- Полная документация в одном файле: `llms-full.txt`.

**MCP-сервер:** `https://be.broker.ru/trade-api-mcp-server/mcp`.

**Особенности:**

- Лимиты: 429 Too Many Requests.
- Названия полей и формат ответов — JSON.
- В проекте: `BcsBrokerApi` (сейчас на OkHttp, миграция на
  Ktor — в задаче «Ktor REST transport для polling»).

---

## Finam

**URL:** `https://api.finam.ru/`

**Транспорты:**

- gRPC: `https://api.finam.ru/docs/grpc/`
- WebSocket (AsyncAPI): `https://api.finam.ru/docs/async-api/`
- REST: `https://api.finam.ru/docs/rest/`

**Авторизация:** уточнить при реализации (в задаче
«Реализация Finam Broker API»).

**Документация:** `https://api.finam.ru/docs/`

**Особенности:**

- В проекте: `FinamBrokerApi` — заглушка, будет реализована
  через `FinamOrchestrator` по образцу `TInvestOrchestrator`.

---

## Связанные документы

- `docs/streams.md` — работа со стримами T-Invest API.
- `docs/ROADMAP.md` — задачи реализации, включая
  «Оркестратор стримов и Ktor WS transport», «Ktor REST
  transport для polling», «Реализация Finam Broker API».
- `docs/ISSUES.md` — известные проблемы (например,
  `INVALID_ARGUMENT: 30052` для BCS).
