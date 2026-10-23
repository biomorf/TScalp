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

## Процедура: две фазы

Захват лога и разбор разделены намеренно: во время сценария
пользователь работает с телефоном в руках, ему не до терминала.
Разбор — спокойный этап после завершения захвата.

### Фаза 1 — захват

Перед запуском — переключить внимание на устройство. Скрипт
запускает приложение и начинает захват; с этого момента сценарий
выполняется на телефоне, терминал не трогаем.

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
# Лимит захвата, сек. 10 минут хватает на StreamState.
CAPTURE_SECONDS=600
LOG=/tmp/tscalp-live.log

# 1. Сборка и установка (свежий APK)
./gradlew :app:installDebug

# 2. Очистка старого logcat — иначе в разборе эмиты прошлой сессии
adb logcat -c

echo ""
echo "=========================================================="
echo "==> Захват запущен (лимит ${CAPTURE_SECONDS}s, стоп — Ctrl+C)"
echo "==> Лог: ${LOG}"
echo "==> НАЧИНАЙ ТЕСТОВЫЕ ДЕЙСТВИЯ НА УСТРОЙСТВЕ"
echo "=========================================================="
echo ""

# 3. Запуск приложения
adb shell am start -n com.gitlab.biomorf.tscalp/.MainActivity

# 4. Захват в foreground: Ctrl+C или timeout
#    (SIGINT/timeout дают ненулевой exit-код — это норма)
timeout "${CAPTURE_SECONDS}" adb logcat > "${LOG}" || true

echo ""
echo "=========================================================="
echo "==> Захват завершён: ${LOG}"
echo "=========================================================="
```

### Фаза 2 — разбор

```bash
# Полный лог (прокрутка, поиск)
less /tmp/tscalp-live.log

# Или отфильтровано по компонентам
grep -E "FlowManager: state:|StreamStateTracker|TInvestOrchestrator|TInvestWS|TInvestRest|TInvestMarketData" /tmp/tscalp-live.log
```

**Не пайпить `adb logcat` в `grep -q` / `head -1`** —
AGENTS.md 4e, правило 5. Читать до конца, разбирать после
завершения захвата.

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
- **Ctrl+C раньше времени** — обрежет хвост лога. Сначала
  убедись, что все нужные переходы прошли (watchdog: 15/30 сек
  после триггера), потом прерывай.
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
