
###################################################
## Расшифровка кодов ошибок брокеров

**Статус:** начато, T-Invest таблица заведена.

**Что:** числовые коды ошибок от брокеров (например, T-Invest 30079)
приходят из gRPC как INVALID_ARGUMENT: <code>. UI показывает их как есть.

**Сделано:**
- `util/BrokerErrorMessages.kt` — центральный реестр
  Map<BrokerName, Map<code, msg>>.
- `util/TInvestErrorMessages.kt` — ~130 кодов T-Invest по
  официальной документации.
- `AppError.Api` расширен полями `brokerName`, `brokerCode`.
- `toAppError(brokerName)` и `runCatchingAppResult(brokerName, block)`.
- `InvestRepository` передаёт `brokerName` во все вызовы.

**Осталось:**
- `BcsErrorMessages.kt` — заполнить по мере нахождения кодов BCS.
- `FinamErrorMessages.kt` — то же.
- Дополнять T-Invest таблицу новыми кодами по мере появления в логах.

**Оценка:** 15 минут на нового брокера + 5 минут на новый код.



---
###################################################
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


---
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



---
###################################################
## CI/CD для релизов через GitLab

**Статус:** запланировано.

**Что:** настроить GitLab CI для сборки и публикации APK/AAB
в GitLab Releases при пуше тега (v1.0.0 и т.п.).

**Зачем:** сейчас APK собирается локально и вручную; release-канал
для пользователей не автоматизирован.

**Объём:**
- .gitlab-ci.yml: build job для assembleRelease
- Подпись APK через CI variables (keystore, пароли)
- Загрузка артефакта в GitLab Releases через release-cli
- Опционально: unit-тесты как отдельный job
- Опционально: versionName из тега

**Оценка:** ~2 часа (при наличии keystore).
**Когда:** перед первым публичным релизом.




---
##################################################
###################################################
## Единый источник цен (PriceStreamManager)

**Статус:** в работе. Разбито на 6 подкоммитов.

**Проблема:**
Цена одного и того же инструмента обновляется на разных экранах
по-разному:
- Портфель: REST-polling `updatePrices` каждые 5 сек.
- Заявки (выбранный инструмент): gRPC-стрим `subscribeLastPrices`
  только при тиках биржи; в паузах замирает.
- Оба экрана: `SharedPositionStreamManager` раз в 10 сек (позиции).

Три источника, три частоты, дублирование логики в двух ViewModel.
Визуально расходится: Портфель живой, Заявки спят.

**Цель:** единый SoT цен. Один менеджер, одна частота, одна политика.
Все ViewModel получают цены из `PriceStreamManager`.

**Дизайн (утверждён):**

Два независимых менеджера в `domain/api/`:
- `PositionStreamManager` (текущий `SharedPositionStreamManager`)
  — поток позиций по счёту.
- `PriceStreamManager` (новый) — поток цен по набору инструментов.

`PriceStreamManager` — модель потребителей:
- `enum class PriceConsumer { PORTFOLIO, ORDERS }`
- `Map<PriceConsumer, Set<uid>>` — интересы подписчиков
- union пересчитывается при каждом `setInterest` / `clearInterest`
- при фактическом изменении union — пересоздание gRPC-стрима
  (не дельта-подписки: проще и работает у всех брокеров)
- API: `prices: SharedFlow<Pair<uid, price>>` (replay=0),
  `setInterest(consumer, uids)`, `clearInterest(consumer)`

Два канала данных от брокера, оба эмитят в один `_prices`:
1. gRPC-стрим `broker.subscribeLastPrices(union.toList())` — мгновенно.
2. REST-fallback `delay(5_000) + repository.getLastPricesResult(union)`
   — работает всегда, закрывает паузы биржи.

**Что удаляется:**
- `PortfolioViewModel`: поле `priceUpdateJob`, методы `updatePrices` /
  `startPriceUpdates`, цикл `while (isActive) { delay(5_000); ... }`.
- `OrdersViewModel`: поле `priceStreamJob`, методы `startPriceUpdates` /
  `stopPriceUpdates`, прямой вызов `repository.subscribeLastPrices`.

**Что добавляется в ViewModel:**
- `init` — подписка на `priceStreamManager.prices.collect { (uid, price)
  -> updatePrice(...) }`.
- `syncPriceInterest()` — вызывает `setInterest` при изменении
  релевантного набора uid.
- `onCleared()` — `clearInterest`.

**Что не делаем сейчас:**
- Дельта-подписки (subscribe/unsubscribe без пересоздания стрима).
- Дедупликация REST + gRPC (если цена совпала — не проблема).
- Управление фоном (Doze сам справляется).

**План подкоммитов:**

| # | Что | Файлы |
|---|---|---|
| 1 | `PriceConsumer` enum + скелет `PriceStreamManager` (заглушки) | 2 новых файла |
| 2 | Union + gRPC-стрим, пересоздание при смене union | PriceStreamManager |
| 3 | REST-fallback в параллельном цикле | PriceStreamManager |
| 4 | Миграция `OrdersViewModel` | OrdersViewModel |
| 5 | Миграция `PortfolioViewModel` | PortfolioViewModel |
| 6 | Переименовать `SharedPositionStreamManager` → `PositionStreamManager` | 1 файл |

Каждый подкоммит — зелёная сборка, 74 теста.

**Когда:** до релиза. После завершения — обновить эту запись
(удалить или пометить «закрыто»).

**Причина приоритета:** устраняет архитектурное расхождение и утечку
логики обновления по двум ViewModel.




---
##################################################
