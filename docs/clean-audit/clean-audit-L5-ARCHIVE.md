<!-- SECTION_BEGIN: clean-audit-L5-ARCHIVE -->
## L5 — domain/managers (архив)










<!-- SUBSECTION_BEGIN: clean-audit-63 -->
___
### Исправленные (L5)
[пусто]

---
<!-- SUBSECTION_END: clean-audit-63 -->









<!-- SUBSECTION_BEGIN: Н38 -->
___
### Н38 ✅ Н38. PriceFlowManager → data.orchestrator.DebugConfig

**Статус:** исправлено (PR #59, `dev`, 2026-10-28).
**Серьёзность:** критично.

**Live тест:** ✓

**Описание:** `PriceFlowManager.kt` импортирует `data.orchestrator.DebugConfig`, использует в `collectStream` (`if (DebugConfig.disableGrpc && DebugConfig.disableWs) return`). Менеджер (domain) зависит от data.

**Нарушенное правило Clean:** dependency rule — domain зависит от data.

**Clean-альтернатива:** `DebugFlagsRepository` (domain-интерфейс, из Н23). Менеджер инжектит интерфейс, реализация — `DebugConfig` за `@Binds`. Плюсы: domain не видит data. Минусы: правка `PriceFlowManager` (1 callsite) + H23.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). **Связано с H23** (`DebugFlagsRepository`).

---

<!-- SUBSECTION_END: Н38 -->

<!-- SUBSECTION_BEGIN: Н40 -->
___
### Н40 ✅ Н40. Менеджеры → di.BrokerManager

**Статус:** исправлено (PR #60, `dev`, 2026-10-28).

**Серьёзность:** критично.

**Live тест:** -

**Описание:** все три менеджера (`PositionFlowManager`, `PriceFlowManager`, `OrderFlowManager`) инжектят `com.gitlab.biomorf.tscalp.di.BrokerManager` напрямую через конструктор. Domain-слой зависит от `di/`.

**Нарушенное правило Clean:** dependency rule — domain зависит от `di`.

**Clean-альтернатива:** `BrokerRegistry` (domain-интерфейс, дизайн в L7-Н22/Н29). Менеджеры инжектят `BrokerRegistry`. Плюсы: domain чист. Минусы: правка 3 менеджеров.

**Выгоды:** —.

**Ссылка на задачу:** `### H22. BrokerRegistry` (PR #60, `dev`).

<!-- SUBSECTION_END: Н40 -->

<!-- SUBSECTION_BEGIN: Н39 -->
___
### Н39 ✅ Н39. PriceFlowManager → util.toAppError

**Статус:** исправлено (0 вхождений `toAppError` в `PriceFlowManager`; ушло при рефакторинге, вероятно с Н47).

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `PriceFlowManager.kt` импортирует `util.toAppError`, использует в `runPolling` (`AppLogger.w(TAG, "REST poll failed: ${e.toAppError(BrokerName.TINVEST).message}", e)`). `toAppError` живёт в `util/AppResultExtensions.kt` и импортирует `io.grpc.StatusException`. Domain видит io.grpc транзитивно.

**Нарушенное правило Clean:** dependency rule — domain видит io.grpc через `util`.

**Clean-альтернатива:** `AppErrorMapper` в data (Н20). `PriceFlowManager` инжектит `AppErrorMapper` (или `Logger` с `AppError`-конвертацией). Плюсы: domain без gRPC. Минусы: инъекция в менеджер.

**Выгоды:** —.

**Что сделано:** `PriceFlowManager` больше не использует `util.toAppError` (0 вхождений); `grep` 2026-10-31. Связано с H20 (`AppErrorMapper` в data).

**Ссылка на задачу:** — (не заведена; закрыто фактически).

<!-- SUBSECTION_END: Н39 -->

<!-- SECTION_END: clean-audit-L5-ARCHIVE -->
