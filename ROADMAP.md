
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
### Переименование проекта

**Статус:** запланировано.

**Часть 1 — `java` → `kotlin`:**
Переместить директории исходников:
- app/src/main/java → app/src/main/kotlin
- app/src/test/java → app/src/test/kotlin
- app/src/androidTest/java → app/src/androidTest/kotlin

Ноль влияния на компиляцию. AGP и Kotlin-плагин ищут .kt
в обеих директориях.

**Часть 2 — `com.example.tscalp` → осмысленное имя:**
Плейсхолдер `com.example` от шаблона Android Studio.

Кандидат: `io.github.biomorf.tscalp` (конвенция open-source
на GitHub).

Замены:
1. `app/build.gradle.kts`: `applicationId` и `namespace`.
2. IDE Refactor → Rename пакета `com.example.tscalp`.
3. Проверить `AndroidManifest.xml`, тесты, строковые упоминания.
4. Удалить старое приложение с устройства.
5. Прогнать сборку, тесты, сценарий на устройстве.

**Последствия:**
- `applicationId` меняется → на устройстве появится как новое
  приложение, старое надо удалить.
- SharedPreferences станут недоступны → сброс настроек,
  повторный ввод токена.

**Когда:** совместить с переходом на DataStore. Оба события
вызывают сброс настроек — логично сделать в один заход:
пользователь переустанавливает приложение один раз, вводит
токен заново один раз.

**Оценка:** ~40 минут + проверка.


---
##################################################
