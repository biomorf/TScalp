<!-- SECTION_BEGIN: issues-di -->
## Di










<!-- SUBSECTION_BEGIN: i-28 -->
___
### [OPEN] I-28 BrokerRegistry.anyInitialized не сбрасывается после DisconnectBrokerUseCase
**Статус:** открыт (pre-existing, не блокер).

**Симптом:** после logout T-Invest (`DisconnectBrokerUseCase: success`) и перехода на Портфель:
- `CheckBrokerInitializationUseCase: execute: consumer=PORTFOLIO`
- `PortfolioViewModel: checkApiInitialization: anyApiInitialized=true` — **должно быть false**
- `PortfolioViewModel: loadPortfolio failed: API не инициализирован` (`IllegalStateException` из `TInvestAccountsService.getAccounts`)

**Причина:** `BrokerRegistry.anyInitialized` (StateFlow) не пересчитывается на `false` в момент `DisconnectBrokerUseCase.refreshInitializationState()`. Следующая VM получает cached `true` и идёт в `AnyInitialized`-ветку → `loadPortfolio` → `IllegalStateException`. Порядок в `DisconnectBrokerUseCase`: `deinitialize()` → `refreshInitializationState()`; `refresh` вызывается, но `anyInitialized` в этот момент уже проэмитил `true` в предыдущей сессии (токен был). После disconnect — не переэмитит `false`, потому что `_anyInitialized` StateFlow не обновился (или эмитит с задержкой, а VM уже прочитал `true`).

**Наблюдение (2026-11-07, live-test M013):** force-stop + старт приложения с уже удалённым токеном; `CheckBrokerInitializationUseCase` вызывается, `anyApiInitialized=true` — не соответствует реальному состоянию. После logout через UI — то же.

**Решение:** проверить `BrokerRegistryImpl.refreshInitializationState()` — пересчитывает ли `anyInitialized` синхронно; и `_anyInitialized` StateFlow — эмитит ли `false` при сбросе. Возможно:
- `distinctUntilChanged` + принудительный re-emit на VM-стороне;
- или `refreshInitializationState()` в `DisconnectBrokerUseCase` — до `deinitialize()`, не после;
- или `anyInitialized` из reactive-источника (lifecycle.isInitialized каждого брокера), не из snapshot.

**Файлы:** `domain/api/BrokerRegistry.kt` (интерфейс), `data/registry/BrokerRegistryImpl.kt` (реализация), `domain/usecases/DisconnectBrokerUseCase.kt` (порядок вызовов).

**Связано:** `### ✅ M006` (DisconnectBrokerUseCase), `CheckBrokerInitializationUseCase` (M013, usecase), `docs/ROADMAP.md` → M013 `**Подтверждение:**`.

**Когда вернуться:** при следующем касании `BrokerRegistry` / `DisconnectBrokerUseCase`, или при закрытии M013 deferred closure.

---
<!-- SUBSECTION_END: i-28 -->





<!-- SECTION_END: issues-di -->
