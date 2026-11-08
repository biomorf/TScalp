package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.models.Instrument

/**
 * Презентационное отображение инструмента: `<ticker> - <name>`.
 *
 * Вынесено из VM (`onSearchResultSelected` / `onInstrumentSelected`),
 * чтобы VM не строила UI-строки (clean-audit M1/M7, P9 ROADMAP).
 */
fun Instrument.displayName(): String = "$ticker - $name"
