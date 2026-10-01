###################################################
## Мигрировать SettingsRepository/SettingsViewModel на BrokerName

**Статус:** запланировано, не блокирует.

**Что:** методы `SettingsRepository` и `SettingsViewModel` принимают
`brokerName: String`. Это последний блок строковых параметров
брокеров в проекте после удаления `BrokerManager.getBroker(String)`.

**Где:**
- `SettingsRepository`: `saveBrokerCredentials`, `loadBrokerCredentials`,
  `clearBrokerCredentials`, `clearTradingState`, `saveToken`,
  `hasSavedToken`, `getToken`, `saveDefaultAccountId`, `loadDefaultAccountId`
- `SettingsViewModel`: тонкие обёртки над репозиторием
- `TScalpApplication`: вызовы `settingsRepository.*(brokerName.key)`
- `SettingsScreen`: вызовы `settingsViewModel.*("TInvest")`

**Действия:**
1. Заменить сигнатуры методов на `brokerName: BrokerName`.
2. Внутри репозитория оставить `brokerName.key` только для построения
   ключей SharedPreferences — совместимо с существующими данными.
3. Обновить вызывающих, убрать строковые литералы.

**Оценка:** ~40 минут.
**Когда:** после текущего блока (BrokerManager / SearchCache / Orders).



####################################################
## Довести BrokerManager до типизированного API

**Статус:** запланировано, низкая срочность.

**Что:** `BrokerManager.getBroker(name: String)` — последний строковый
доступ к брокерам из ядра. Используется только в `SearchCache.kt:31`.

**Действия:**
1. `SearchCache.search(brokerName: BrokerName, query: String)` —
   принимать enum вместо строки.
2. `SearchCache.invalidate(brokerName: BrokerName, query: String)` — то же.
3. `OrdersViewModel`: убрать `BrokerName.fromKey(...)` и строковые
   `searchBroker`/`pairSearchBroker` из `OrdersUiState` — заменить на enum.
4. Удалить `BrokerManager.getBroker(String)` целиком.
5. Удалить `BrokerManager.getAvailableBrokers(): List<String>`
   (после того как `OrdersViewModel.getAvailableBrokers` будет удалён
   или переведён на enum).

**Оценка:** ~30 минут.
**Когда:** после текущего блока тикетов (портфель, settings, чистка OrderModels).



##################################################
## Прямые вызовы брокера из ViewModel в обход InvestRepository

**Статус:** известно, отложено.

**Где:**
- `PortfolioViewModel.loadPortfolio` — `broker.fetchPositionsRest(accountId, sandbox)`
- `PortfolioViewModel.updateTradingStatuses` — `broker.getTradingStatuses(ids)`
- `OrdersViewModel.startPriceUpdates` — `broker.subscribeLastPrices(ids)`

**Проблема:** нарушает консистентность слоя данных. Часть методов идёт через `InvestRepository` (мигрированы на `AppResult`), часть — напрямую к брокеру (try/catch или без обработки). Это затрудняет тестирование, создаёт два пути получения данных, и мешает единой политике обработки ошибок.

**Решение:** перенести `fetchPositionsRest`, `getTradingStatuses`, `subscribeLastPrices` в `InvestRepository` с `*Result`-вариантами на `AppResult`, переписать ViewModel на них. После этого `BrokerManager` не должен быть виден из ViewModel.

**Когда вернуться:** отдельной задачей после завершения миграции 3.5, до релиза.



#####################################################
**Где:**
- `PortfolioViewModel.loadPortfolio` — `broker.fetchPositionsRest(accountId, sandbox)`
- `PortfolioViewModel.updateTradingStatuses` — `broker.getTradingStatuses(ids)`
- `OrdersViewModel.startPriceUpdates` — `broker.subscribeLastPrices(ids)`
- `OrdersViewModel.startPositionUpdates` — `broker.fetchPositionsRest(accountId, sandbox)`
- `OrdersViewModel.updateTradingStatuses` — `broker.getTradingStatuses(ids)`
- `OrdersListViewModel.loadOrders` — `broker.getOrders`, `broker.getStopOrders`
- `OrdersListViewModel.cancelOrder` — `broker.cancelOrder`



######################################################
