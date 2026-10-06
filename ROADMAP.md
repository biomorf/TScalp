
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
###################################################
## ✅ Единый источник цен (PriceStreamManager) — закрыто

Реализовано в серии из 6 подкоммитов:
1. PriceConsumer enum + скелет PriceStreamManager
2. Union + gRPC-стрим с пересозданием при смене union
3. REST-fallback раз в 5 секунд
4. Миграция OrdersViewModel
5. Миграция PortfolioViewModel (+ фикс карточек в OrdersScreen:
   цена берётся из uiState.currentPrice, а не из медленного
   portfolioPositions)
6. Переименование SharedPositionStreamManager → PositionStreamManager

Результат: единый SoT цен, обе ViewModel получают обновления
из одного потока, gRPC для мгновенных тиков, REST-fallback для
пауз биржи. Устраняет расхождение «Портфель живой, Заявки спят».

Осталось на потом (ISSUES.md):
- брокер в логах PriceStreamManager
- рефкаунт и lifecycle-политика при сворачивании




---
##################################################
