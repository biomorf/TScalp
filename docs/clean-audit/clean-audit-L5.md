<!-- SECTION_BEGIN: clean-audit-L5 -->
## L5 — domain/managers

<!-- SUBSECTION_BEGIN: Н41 -->
___
### Н41 Н41. Хардкод BrokerName.TINVEST в менеджерах

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `PositionFlowManager.doStart`, `OrderFlowManager.doStart` — `brokerManager.getBroker(BrokerName.TINVEST)`; `PriceFlowManager.collectStream` + `runPolling` — `BrokerName.TINVEST` (локальные val). «Работаем только с T-Invest» захардкожено в domain/managers. При появлении BCS/Finam позиций/цен/заявок — рефакторинг всех трёх.

**Нарушенное правило Clean:** domain-знание «единственный брокер = T-Invest» в менеджерах, не в конфигурации.

**Clean-альтернатива:** менеджер получает `BrokerName` из состояния (текущий счёт определяет брокера), либо мультиброкерная агрегация (несколько источников). Плюсы: готово к мультиброкерности. Минусы: правка всех трёх менеджеров (~3–4 ч).

**Выгоды:** KISS — до появления BCS/Finam работа только с T-Invest достаточна.

**Ссылка на задачу:** — (не заведена). Связано с `### Реализация BCS Broker API` / `### Реализация Finam Broker API`.

<!-- SUBSECTION_END: Н41 -->

<!-- SUBSECTION_BEGIN: Н58 -->
___
### Н58 Н58. PositionFlowManager.runPolling — string matching NOT_FOUND

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `PositionFlowManager.runPolling`: `if (e.message?.contains("NOT_FOUND") == true) return`. `e.message` у `StatusException` — человекочитаемый текст, не код. При изменении формата/локализации — проверка молча перестанет срабатывать, polling продолжит крутиться на удалённом счёте.

**Нарушенное правило Clean:** не строго Clean — string matching вместо structured API.

**Clean-альтернатива:** `Status.fromThrowable(e).code == Status.Code.NOT_FOUND`. Плюсы: не зависит от формата `message`. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `docs/ISSUES.md` → «gRPC retry: string matching NOT_FOUND вместо Status.Code» (расширено в L5-Н58: «касается также `PositionFlowManager.runPolling`»).

<!-- SUBSECTION_END: Н58 -->

<!-- SUBSECTION_BEGIN: Н59 -->
___
### Н59 Н59. PriceFlowManager.runPolling — swallows CancellationException

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `PriceFlowManager.runPolling`: единственный `catch (e: Exception)` без предшествующего `catch (e: CancellationException) { throw e }`. `CancellationException` перехватывается и логируется как «REST poll failed», сигнал отмены родителю не пробрасывается — менеджер останавливается «тихо».

**Нарушенное правило Clean:** не строго Clean — нарушен контракт structured concurrency.

**Clean-альтернатива:** `catch (e: CancellationException) { throw e }` перед общим `catch (e: Exception)` — как в `PositionFlowManager.runPolling` / `OrderFlowManager.runPolling`. Плюсы: консистентность, отмена работает. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `docs/ISSUES.md` → «PriceFlowManager.runPolling swallows CancellationException» (отдельная запись).

<!-- SUBSECTION_END: Н59 -->

<!-- SECTION_END: clean-audit-L5 -->
