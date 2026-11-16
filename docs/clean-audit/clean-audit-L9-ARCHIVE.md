<!-- SECTION_BEGIN: clean-audit-L9-ARCHIVE -->
## L9 — transport-ktor (отдельный модуль) (архив)










<!-- SUBSECTION_BEGIN: clean-audit-110 -->
___
### Исправленные (L9)
<!-- SUBSECTION_END: clean-audit-110 -->









<!-- SUBSECTION_BEGIN: Н88 -->
___
### Н88 ✅ Н88. RestGetPortfolioRequest — без @Serializable

**Статус:** исправлено (PR #75, `dev`, 2026-10-30).

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `TInvestRestTransport.kt`: `data class RestGetPortfolioRequest(@SerialName("accountId") val accountId: String)` — единственный DTO из 14 **без** `@Serializable`. Используется в `getBalance(accountId, isSandbox = true)` через `setBody(request)`. При попытке сериализации kotlinx.serialization выбросит `SerializationException` в runtime (сейчас не проявляется, если sandbox-ветка не вызывается).

**Нарушенное правило Clean:** не строго Clean — консистентность DTO + runtime failure.

**Clean-альтернатива:** добавить `@Serializable` к `RestGetPortfolioRequest`. Плюсы: единообразие, работает. Минусы: —.

**Выгоды:** —.

**Что сделано:** `@Serializable` добавлен к `RestGetPortfolioRequest` (`TInvestRestTransport.kt`); единственный DTO из 14 без аннотации; `setBody` в `getBalance` (sandbox) теперь сериализуется.

**Ссылка на задачу:** PR #75 (`dev`, 2026-10-30). Связано с закрытой `### Ktor REST для позиций/заявок/баланса` — баг в реализации.

---

<!-- SUBSECTION_END: Н88 -->

<!-- SECTION_END: clean-audit-L9-ARCHIVE -->
