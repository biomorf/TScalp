# Live test

Процедура проверки задачи на устройстве через `adb logcat`.
Применяется для задач с **deferred closure** (ROADMAP →
`**Подтверждение:**`): результат нельзя проверить unit-тестами,
он виден только в runtime (состояние стримов, поведение
транспортов, деградация, переключение флагов).

Deferred closure — правило 5g п.4 AGENTS.md. Процедура —
часть подтверждения, не отдельная задача.

---

## Предусловия

- Устройство или эмулятор подключены (`adb devices` — непустой
  вывод).
- Проект собран и установлен: `./gradlew :app:installDebug`.
- Сценарий live-теста известен из `**Подтверждение:**` в
  ROADMAP (какое поведение наблюдаем, какой критерий закрытия).
- Известны теги логов нужных компонентов (см. «Теги» ниже).

---

## Два режима захвата

Захват лога зависит от сценария: автоматический (приложение
работает само, действий пользователя нет) и ручной (действия
совершаются во время захвата). Разные режимы — разные
инструменты и разный тайминг разбора.

**Режим A — автоматический.** Приложение работает само,
действий пользователя не требуется (запуск, работа в фоне,
переходы по таймеру watchdog'а). Лог пишется в файл, разбор —
после завершения захвата. Пользователь в это время может
заниматься чем угодно.

**Режим B — ручной.** Сценарий требует действий пользователя
(открыть вкладку, включить тумблер, logout). Нужен видимый
live-вывод в терминал: пользователь смотрит, что происходит,
и решает, какое действие делать дальше. Вывод — с фильтрацией
в терминал, опционально дублируется в файл (`tee`).

### Режим A — автоматический (лог в файл)

- **Лимит захвата** — 10 минут (`CAPTURE_SECONDS=600`). Хватает
  на сценарий StreamState (три перехода × три менеджера +
  watchdog-пороги 15/30 сек).
- **Остановка** — Ctrl+C (штатно) или по истечении лимита.
  `Ctrl+C` **до** того, как все переходы появились в логе,
  обрежет хвост — сначала убедись, что сценарий прошёл, потом
  прерывай.
- **Захват в foreground**, не в фоне: ранние логи от запуска
  приложения уже в файле, PID помнить не нужно, Ctrl+C и timeout
  завершают чисто.

```bash
CAPTURE_SECONDS=600
LOG=/tmp/tscalp-live.log

./gradlew :app:installDebug
adb logcat -c

echo "==> Захват запущен (лимит ${CAPTURE_SECONDS}s, стоп — Ctrl+C)"
echo "==> Лог: ${LOG}"

adb shell am start -n com.gitlab.biomorf.tscalp/.MainActivity

timeout "${CAPTURE_SECONDS}" adb logcat > "${LOG}" || true

echo "==> Захват завершён: ${LOG}"
```

Разбор (после завершения):

```bash
less /tmp/tscalp-live.log

grep -E "FlowManager: state:|StreamStateTracker|TInvestOrchestrator|TInvestWS|TInvestRest|TInvestMarketData" /tmp/tscalp-live.log
```

### Режим B — ручной (live-вывод в терминал)

Пользователь держит устройство в руках и одновременно смотрит
в терминал (второй монитор / split screen / телефон + ноутбук).
Лог идёт с фильтром, только нужные теги; лишнее не отвлекает.

`--line-buffered` у `grep` обязателен: без него вывод
буферизуется блоками и приходит с задержкой — во время ручного
сценария это сбивает с толку.

```bash
LOG=/tmp/tscalp-live.log
FILTER="FlowManager: state:|StreamStateTracker|TInvestOrchestrator|TInvestWS|TInvestRest|TInvestMarketData"

./gradlew :app:installDebug
adb logcat -c

echo "==> Live-вывод (Ctrl+C для остановки)"
echo "==> Дублируется в ${LOG} (для разбора после)"
echo ""

adb shell am start -n com.gitlab.biomorf.tscalp/.MainActivity

adb logcat | grep --line-buffered -E "${FILTER}" | tee "${LOG}"
```

Остановка — Ctrl+C, когда сценарий завершён. Файл `${LOG}`
остаётся для повторного разбора и для отчёта.

**Почему без `-v time`.** Флаг `-v time` меняет формат строки:
`D/PositionFlowManager(19091): state: ...` — с PID в скобках
между тегом и сообщением. Подстрока `FlowManager: state:` в
таком формате **не совпадает** (между `FlowManager` и
`: state:` стоит `(pid): `), grep молча пропускает все
state-переходы. Без `-v time` формат `D PositionFlowManager:
state: ...` — подстрока совпадает. Если нужна точная метка
времени в файле — добавить `-v threadtime` и поправить
`FILTER` под формат `D(<tid>) PositionFlowManager:`.

Альтернатива без `grep` — теги через `-s` (silent mode):

```bash
adb logcat -v time -s PositionFlowManager PriceFlowManager OrderFlowManager TInvestOrchestrator
```

`-s` показывает только перечисленные теги. `StreamStateTracker`
пишет под тегом менеджера (`tag = "PositionFlowManager"` и т.д.),
отдельного тега у него нет.

**Не пайпить `adb logcat` в `grep -q` / `head -1`** —
AGENTS.md 4e, правило 5. В режиме B `grep --line-buffered` — ок,
он читает до конца, а не обрывает pipe.

---

## Теги логов

| Компонент | Тег в logcat |
|---|---|
| `PositionFlowManager` | `PositionFlowManager` |
| `PriceFlowManager` | `PriceFlowManager` |
| `OrderFlowManager` | `OrderFlowManager` |
| `StreamStateTracker` | tag параметра: `PositionFlowManager` / `PriceFlowManager` / `OrderFlowManager` |
| `TInvestOrchestrator` | `TInvestOrchestrator` |
| WS-транспорт | `TInvestWS` / `TInvestWS/prices` / `TInvestWS/orders` |
| REST-транспорт | `TInvestRest` |
| gRPC-стримы | `TInvestMarketData` |

---

## Тайминги (watchdog StreamState)

`StreamStateTracker`:
- tick — раз в **1 сек** (`STATE_TICK_MS`);
- стрим-молчание для `LIVE → POLLING` — **15 сек**
  (`streamSilenceMs`);
- polling-молчание для `→ STALE` — **30 сек** (`staleMs`).

Смена состояния появляется не мгновенно: от триггера до `tick` —
до 1 сек; до смены — до порога + 1 сек.

**Практика:** после триггера подождать **20–40 сек** перед
проверкой лога.

---

## Сценарии

Каждый сценарий указывает **режим** (A — автоматический,
B — ручной). Если сценарий требует действий пользователя
во время захвата, это режим B.

### StreamState (PR #38, задача `StreamState в data-слое`)

**Режим:** B (ручной) — нужны действия (открыть вкладку
«Заявки», включить тумблеры, logout).

**Критерий:** для каждого из трёх менеджеров — четыре перехода.

Position / Price / Order:
- `state: INITIALIZING → LIVE` — при старте приложения (стрим
  живой).
- `state: LIVE → INITIALIZING → POLLING` — при включении
  `disableGrpc && disableWs` в Настройках. Два перехода подряд:
  `INITIALIZING` — это `onStart()` в `reapplyDebugFlags`
  (рестарт источников), `POLLING` — после первого успешного
  polling.
- `state: → STALE` — через 30+ сек после включения авиарежима.
  Стримы и polling оба молчат → watchdog переводит в STALE
  (порог `staleMs` = 30 сек + tick 1 сек). Авиарежим включать
  **после** того, как все три менеджера уже в POLLING
  (шаг toggle выше) — иначе LIVE-стрим может дольше держаться
  за сокет, и тест затянется.
- `state: STALE → POLLING` или `STALE → LIVE` — при возврате
  сети. Восстановление идёт через первый успешный polling
  (`POLLING`) или первый stream-эмит (`LIVE`); порядок зависит
  от таймингов, **оба варианта валидны**.
- `state: POLLING → STOPPED` — при `stop()` (logout, смена
  счёта, `clearInterest` для Price при пустом union).

**Тайминг STALE-шага:** включить авиарежим → ждать 31–35 сек
(порог 30s + tick 1s) → три `→ STALE`. Вернуть сеть → ждать
5–15 сек (polling: Position/Order ~1s, Price ~5s; stream: до
1s после reconnect) → `STALE → POLLING` или `STALE → LIVE`.

### Debug-тумблеры (задача `Debug-тумблеры: применить ко всем менеджерам`)

**Режим:** B (ручной).

**Критерий:** 4 репрезентативные комбинации флагов (углы
матрицы — `docs/ROADMAP.md` → `### Debug-тумблеры` →
«Матрица флагов») + logout. Промежуточные 4 комбинации
(`disableGrpc` XOR `disableWs`) — по симметрии, отдельно
не прогоняются.

**Сценарий:**

1. **Старт (всё off).** Открыть вкладку «Заявки» (Order
   стартует по требованию UI).
   - Position: `STOPPED → INITIALIZING → LIVE`;
   - Price: `STOPPED → INITIALIZING → LIVE`;
   - Order: `STOPPED → INITIALIZING → LIVE` (если стрим
     прислал событие) или `→ POLLING` (если событий нет —
     event-only стрим, первый snapshot приходит из polling).
   Матрица: `gRPC + WS` / `gRPC unary`.

2. **`disableGrpc` + `disableWs` (forceRest off).**
   Настройки → включить оба тумблера.
   - Каждый менеджер получает `reapplyDebugFlags()`
     (`restart` → `onStart()` → `INITIALIZING`).
   - Первый успешный polling → `INITIALIZING → POLLING`.
     Тайминг: Position / Order — ~1s (polling без стартовой
     задержки); Price — ~5s (`runPolling` стартует с
     `delay(5_000)`).
   В логе: `gRPC and WS disabled: collectStream bypassed`.
   Матрица: `нет стримов` / `gRPC unary`.

3. **`forceRest` (toggleRest, при уже отключённых grpc/ws).**
   Настройки → включить `forceRest`.
   - Unary уходит на REST; стримы уже отключены.
   - Состояние без изменений (уже POLLING).
   В логе: `forceRest: bypassing gRPC for prices`,
   `forceRest: bypassing gRPC for orders`,
   `forceRest: bypassing gRPC for positions`,
   `[REST/ktor/TInvest] POST ...`.
   Матрица: `нет стримов` / `REST`.

4. **`disableGrpc` + `disableWs` обратно в off (forceRest on).**
   Настройки → выключить оба тумблера.
   - Стримы возвращаются, unary остаётся на REST.
   - Ожидаемые переходы:
     - Position: `POLLING → INITIALIZING → LIVE` (initial
       snapshot из `positionsStream` приходит быстро);
     - Price: `POLLING → INITIALIZING → LIVE` (первый
       `lastPrice` идёт в первые секунды);
     - Order: `POLLING → INITIALIZING → LIVE` если есть
       событие `orderState`, иначе остаётся `POLLING`
       (event-only стрим, событий нет — норма).
   Матрица: `gRPC + WS` / `REST`.

5. **Logout.** Настройки → T-Invest → Logout.
   - Position: `→ STOPPED`;
   - Price: `→ STOPPED`;
   - Order: `→ STOPPED` (B4-фикс: logout вызывает
     `orderFlowManager.stop()`).

**Проверенные 4 из 8:** `(off, off, off)`, `(on, on, off)`,
`(on, on, on)`, `(off, off, on)` — покрывают углы матрицы:
стрим × unary (gRPC/REST). Промежуточные `(on, off, *)` и
`(off, on, *)` — по симметрии.

**Если live-тест зелёный** → `docs(roadmap): mark
debug-toggles done` в `dev`. Дальше — live-тест StreamState
(полный цикл для трёх менеджеров).

### DebugConfig (задача `DebugConfig: только для debug-сборок`)

**Критерий:** в debug-сборке — тумблеры видны и работают;
в release — секция скрыта, флаги всегда `false` (никаких
`forceRest` / `disableGrpc` / `disableWs` эффектов).

Процедура уточняется при старте задачи.

---

## Известные ловушки

- **`adb logcat -c` обязателен** — иначе в разборе старые эмиты,
  ложно-положительные.
- **Ctrl+C раньше времени (режим A)** — обрежет хвост лога.
  Сначала убедись, что все нужные переходы прошли (watchdog:
  15/30 сек после триггера), потом прерывай.
- **`grep` без `--line-buffered` (режим B)** — вывод приходит
  блоками с задержкой. Кажется, что переход не сработал, а он
  просто не долетел до терминала. Всегда `--line-buffered` при
  live-выводе.
- **Порог 15/30 сек** — не паниковать, если через 5 сек после
  отключения флага смены нет. Ждать 20–40 сек.
- **`INITIALIZING` в середине лога** — не баг, а `onStart()` в
  `reapplyDebugFlags` (перезапуск с новыми флагами).
- **T-Invest sandbox WS не работает** — `docs/streams.md`,
  `docs/ISSUES.md`. На sandbox ожидаемо `INITIALIZING → POLLING`
  при старте (gRPC есть, WS молчит, polling — резерв).

---

## Связанные документы

- `AGENTS.md` 5g п.4 — deferred closure, `**Подтверждение:**`.
- `AGENTS.md` 4e — безопасность shell-команд.
- `docs/streams.md` — стримы, WS, polling.
- `docs/gradle-setup.md` — сборка, unit-тесты, `android.util.Log`.
- `docs/ROADMAP.md` — задачи с `**Подтверждение:**`.
