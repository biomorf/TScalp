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

## Процедура

```bash
# 1. Установка (свежая сборка)
./gradlew :app:installDebug

# 2. Очистка старого logcat (иначе увидишь эмиты прошлой сессии)
adb logcat -c

# 3. Захват в файл, в фоне — не терять ранние логи
adb logcat > /tmp/tscalp-live.log &
LOGCAT_PID=$!

# 4. Запуск приложения
adb shell am start -n com.gitlab.biomorf.tscalp/.MainActivity

# 5. Прогон сценария (см. «Сценарии» ниже)

# 6. Остановка захвата
kill $LOGCAT_PID

# 7. Разбор
grep -E "FlowManager|StreamStateTracker|TInvestOrchestrator|TInvestWS|TInvestRest|TInvestMarketData" /tmp/tscalp-live.log
```

**Не пайпить `adb logcat` в `grep -q` / `head -1`** —
AGENTS.md 4e, правило 5. Читать до конца, разбирать после `kill`.

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

### StreamState (PR #38, задача `StreamState в data-слое`)

**Критерий:** для каждого из трёх менеджеров — три перехода.

Position / Price / Order:
- `state: INITIALIZING → LIVE` — при старте приложения (стрим
  живой).
- `state: LIVE → INITIALIZING → POLLING` — при включении
  `disableGrpc && disableWs` в Настройках. Два перехода подряд:
  `INITIALIZING` — это `onStart()` в `reapplyDebugFlags`
  (рестарт источников), `POLLING` — после первого успешного
  polling.
- `state: POLLING → STOPPED` — при `stop()` (logout, смена
  счёта, `clearInterest` для Price при пустом union).

### DebugConfig (задача `DebugConfig: только для debug-сборок`)

**Критерий:** в debug-сборке — тумблеры видны и работают;
в release — секция скрыта, флаги всегда `false` (никаких
`forceRest` / `disableGrpc` / `disableWs` эффектов).

Процедура уточняется при старте задачи.

---

## Известные ловушки

- **`adb logcat -c` обязателен** — иначе в разборе старые эмиты,
  ложно-положительные.
- **`adb logcat` в фоне** (`> /tmp/file &`) — иначе ранние логи
  теряются.
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
