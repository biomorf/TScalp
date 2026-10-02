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
## Унифицировать формулу profitPercent для PortfolioPosition

**Статус:** запланировано, не блокирует.

**Что:** profitPercent в PortfolioPosition вычисляется по-разному:
- PortfolioViewModel.updatePortfolioItem:
  (currentPrice − averagePrice) / averagePrice × 100
- OrdersViewModel.updatePositionPnl:
  expectedYield / (averagePrice × quantity) × 100

Для акций формулы эквивалентны. Для фьючерсов расходятся: currentPrice
в пунктах, yield в рублях, pointValue связывает их. Формула из
PortfolioViewModel не учитывает pointValue.

**Действия:**
1. Определить единую формулу, корректно работающую для всех типов.
2. Внести в extension toPortfolioPosition(), убрать параметр profitPercent.
3. Обновить тесты и UI, если процент изменится.

**Оценка:** ~40 минут + проверка на фьючерсных позициях.
**Когда:** после остальных задач чистки.




##################################################
## Переход на edge-to-edge (statusBarColor deprecated)

**Статус:** запланировано, требует отдельной подготовки.

**Что:** `window.statusBarColor` и `window.navigationBarColor` устарели.
С Android 15 система применяет edge-to-edge принудительно для приложений
с target SDK 35+.

**Действия:**
1. Убрать `window.statusBarColor = ...` из Theme.kt.
2. Убедиться, что в MainActivity вызывается `enableEdgeToEdge()`.
3. Проверить все 4 экрана (Заявки, Портфель, Настройки, MainScreen)
   на Android 12, 13, 14, 15.
4. При необходимости — добавить `WindowInsets` в Scaffold'ы.

**Оценка:** ~2 часа + прогон на устройствах разных версий.
**Когда:** при подготовке релиза под Android 15 или target SDK 36.




###################################################
