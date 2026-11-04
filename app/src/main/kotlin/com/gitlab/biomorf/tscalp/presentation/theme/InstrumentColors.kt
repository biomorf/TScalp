package com.gitlab.biomorf.tscalp.presentation.theme

import androidx.compose.ui.graphics.Color
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType

// Палитра цветов по типу инструмента (5 цветов, общих для всего UI).
val InstrumentShare = Color(0xFF1565C0)
val InstrumentBond = Color(0xFFE65100)
val InstrumentEtf = Color(0xFF2E7D32)
val InstrumentCurrency = Color(0xFF6A1B9A)
val InstrumentOther = Color(0xFF757575)

internal fun getInstrumentTypeColor(instrumentType: InstrumentType): Color =
    when (instrumentType) {
        InstrumentType.SHARE -> InstrumentShare
        InstrumentType.BOND -> InstrumentBond
        InstrumentType.ETF -> InstrumentEtf
        InstrumentType.CURRENCY -> InstrumentCurrency
        else -> InstrumentOther
    }
