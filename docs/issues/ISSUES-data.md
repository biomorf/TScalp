<!-- SECTION_BEGIN: issues-data -->
## Data










<!-- SUBSECTION_BEGIN: i-26 -->
___
### [DONE] I-26 Currency format: lowercase iso-code в `Currency.getInstance`
**Статус:** решён (PR #143, merge `10b138f6`).

**Симптом:** `formatCurrency(1234.5, "rub")` бросает `java.lang.IllegalArgumentException` из `java.util.Currency.getInstance("rub")` — ISO 4217 требует uppercase (`"RUB"`). Латентно: срабатывает, только если `Instrument.currency` приходит lowercase. Сейчас в проекте `DOMESTIC_CURRENCY = "RUB"` (uppercase) — fallback работает; для USD / EUR / BCS / Finam регистр не проверен live. На T-Invest RUB — uppercase (по логам, live не подтверждён).

**Причина:** `presentation/util/CurrencyFormat.kt:19` — `format.currency = Currency.getInstance(currency)` без `uppercase(Locale.ROOT)`. `Instrument.currency` — «как пришло от брокера», регистр не гарантирован контрактом. Три callsite в presentation передают `Instrument.currency` напрямую: `OrdersViewModel:1101` (`selectedInstrument.currency`), `OrdersViewModel:1115` (`pairedInstrument.currency`), `AssetPositionCard:215` (`position.instrument.currency`). Pre-existing: старый код тоже передавал `position.currency` как есть (до PR #90 — в `PortfolioPosition.currency`).

**Решение:** `formatCurrency` — нормализовать `currency.uppercase(Locale.ROOT)` перед `Currency.getInstance(...)`. `Locale.ROOT` — чтобы не зависеть от `Locale.getDefault()` (турецкая `i` → `İ` — не наш кейс, но правило). Сигнатура `formatCurrency(value: Double, currency: String)` не меняется. Плюс unit-тесты (`CurrencyFormatTest`): lowercase → не падает; результат совпадает с uppercase.

**Файлы:**
- `presentation/util/CurrencyFormat.kt` — нормализация в `formatCurrency`.
- `presentation/util/CurrencyFormatTest.kt` (новый) — 4 теста.

**Связано:** `docs/ROADMAP.md` → `### Currency format: normalize ISO 4217 case`; контекст PR #90 (H5) — при миграции `AssetPositionCard` / `PositionFormatter` на `Position` callsite не изменился.


**Что сделано:** `formatCurrency` теперь нормализует `currency.uppercase(Locale.ROOT)` перед `Currency.getInstance(...)`. 4 unit-теста (`CurrencyFormatTest`): lowercase == uppercase, mixed case == uppercase, uppercase non-empty, USD lowercase == uppercase. Сигнатура не изменилась; `formatPrice` проксирует `formatCurrency` для non-FUTURES.

**Когда вернуться:** при выполнении задачи `### Currency format: normalize ISO 4217 case` (ROADMAP). Или — при первом live-кейсе с USD / EUR / BCS / Finam, где `Instrument.currency` придёт lowercase.

---
<!-- SUBSECTION_END: i-26 -->









<!-- SECTION_END: issues-data -->
