<!-- SECTION_BEGIN: clean-audit-L1 -->
## L1 — presentation










<!-- SUBSECTION_BEGIN: clean-audit-3 -->
___
### Принятые (2c)
<!-- SUBSECTION_END: clean-audit-3 -->










<!-- SUBSECTION_BEGIN: M018 -->
___
### M018. Mediator orchestration + snapshot cross-state (PR-4, принято 2c)
**Статус:** принято 2c.

**Серьёзность:** средне.

**Live тест:** -.

**Описание:** PR-4 (C2 big-bang) вводит `OrderFormMediator` (`@ActivityRetainedScoped`): собирает `TradingStateSnapshot` из `OrderInstrumentState` + `OrderInputState` (cross-state), вызывает `SaveFormStateUseCase` (debounce 500ms), сигнализирует sub-VM о recalc/sync. **M2** (orchestration в presentation) + **M4/M7** (domain-модель `TradingStateSnapshot` конструируется в presentation, наследие `OrderFormViewModel.saveState` до PR-3).

**Нарушенное правило:** M2 — business-orchestration в Mediator (не UseCase); M4/M7 — presentation конструирует domain-модель.

**Clean-альтернатива:** `BuildSnapshotUseCase` в domain (плюсы: snapshot в domain; минусы: 1-строчная обёртка, лишний слой). `OrchestrateRecalcUseCase` в domain (плюсы: M2 чисто; минусы: usecase принимает sub-state → presentation в domain, ещё хуже).

**Выгоды (по 2c):** `TradingStateSnapshot` — простая domain-модель без платформы; Mediator — единственная точка сбора (SSoT); альтернатива через usecase — лишняя обёртка без выгоды для Clean. Thin orchestration в Mediator — индустриальный паттерн (droidcon 2025), не business-logic.

**MVVM-альтернатива:** — (M2 — допустимая orchestration по образцу M006/M013; зафиксировано).

**Ссылка на задачу:** `### OrderFormViewModel: split на sub-VM (C2, снятие @Suppress) + 2 test infra prereqs` → «PR-4 (big-bang) — план для отдельной сессии» (ROADMAP).

**Связано:** M006 (orchestration в UI — принято 2c), M013 (orchestration в VM — закрыт), Н95 (@Suppress accepted — C2 target).

**Сводный отчёт, 2026-11-14:** нет (создана в этой сессии).
<!-- SUBSECTION_END: M018 -->









<!-- SECTION_END: clean-audit-L1 -->
