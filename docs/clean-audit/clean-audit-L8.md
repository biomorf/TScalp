<!-- SECTION_BEGIN: clean-audit-L8 -->
## L8 — app

<!-- SUBSECTION_BEGIN: Н48 -->
___
### Н48 Н48. TScalpApplication → data.repository.SettingsRepository

**Статус:** принято 2c.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `TScalpApplication` инжектит `data.repository.SettingsRepository` напрямую. Composition root видит data — норм. Но инжектит конкретный класс, не интерфейс — после H21b (`domain.api.repository.SettingsRepository`) мигрирует.

**Нарушенное правило Clean:** dependency rule — но для composition root это допустимо (2c).

**Clean-альтернатива:** `domain.api.repository.SettingsRepository` (H21b). Плюсы: app → domain. Минусы: ждёт H21b.

**Выгоды (по 2c):** composition root по определению видит все слои; `TScalpApplication` — единственное место, где это необходимо; миграция — после H21b.

**Ссылка на задачу:** `H21b` (ROADMAP).

<!-- SUBSECTION_END: Н48 -->

<!-- SUBSECTION_BEGIN: Н51 -->
___
### Н51 Н51. refreshInitializationState — 2 callsites в app

**Статус:** открыто.

**Серьёзность:** средне (в рамках H22).

**Live тест:** ✓

**Описание:** `restoreTInvest` и `restoreBcs` вызывают `brokerManager.refreshInitializationState()`. Side-effect после `lifecycle.initialize`. Всего 6 callsites (`TScalpApplication` 2 + `SettingsViewModel` 4).

**Нарушенное правило Clean:** не строго Clean — side-effect в app после lifecycle-вызова.

**Clean-альтернатива:** обсуждается в H22 (internal / interface / auto через `BrokerLifecycleApi.initialize`).

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). **Обогащает открытый вопрос H22**.

<!-- SUBSECTION_END: Н51 -->

<!-- SUBSECTION_BEGIN: Н52 -->
___
### Н52 Н52. Finam → Unit в restoreBrokerConnection

**Статус:** принято 2c.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `restoreBrokerConnection` — `BrokerName.FINAM → Unit` (Finam не восстанавливается из app, только через UI). Осознанное решение — Finam сейчас заглушка.

**Нарушенное правило Clean:** не строго Clean — константа «Finam не восстанавливается» в app.

**Clean-альтернатива:** `BrokerLifecycleApi.supportsRestore: Boolean` в роли. Или отдельный `restoreBrokerConnection` per-broker без `when`. Плюсы: расширяемо. Минусы: доп. метод в роли.

**Выгоды (по 2c):** KISS — Finam — заглушка, при её реализации пересмотреть.

**Ссылка на задачу:** — (не заведена). **Связано с `### Реализация Finam Broker API`**.

<!-- SUBSECTION_END: Н52 -->

<!-- SECTION_END: clean-audit-L8 -->
