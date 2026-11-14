package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderInstrumentState

/**
 * Boundary-маппер `OrderInstrumentState` -> `CardIdentity`
 * (Phase 5f-a, C2, PR-4b).
 *
 * **2c / M4 (fixation, pre-existing):** presentation конструирует
 * domain-модель `CardIdentity` - то же, что делал legacy Coord
 * `saveState` (удалён Phase 6.1) и
 * `OrderFormMediator.toSnapshot` (M018, 2c принято). Альтернатива -
 * usecase с 7 primitives - это задача `### C2: SaveFormStateUseCase
 * primitives (M4/M7 fix, pre-existing)` (в начале ROADMAP Pending).
 * До неё M4 остаётся - вынесено в helper, чтобы 5f-b не собирал
 * `CardIdentity` инлайн в UI.
 *
 * **2d (временное решение, 2026-11-14):** helper'ы переедут в usecase
 * вместе с закрытием M4/M7 (SaveFormStateUseCase primitives). Удаление
 * helper'ов - отдельная задача после неё.
 *
 * `accountId` - nullable (карточка может быть `(uid, broker, null)` до
 * выбора счёта, см. `CardIdentity` KDoc и `docs/architecture.md`).
 */
fun OrderInstrumentState.toPrimaryCardIdentity(): CardIdentity? =
    selectedInstrument?.let {
        CardIdentity(
            tscalpInstrumentId = it.tscalpInstrumentId,
            brokerName = selectedBroker,
            accountId = selectedAccountId,
        )
    }

fun OrderInstrumentState.toPairedCardIdentity(): CardIdentity? =
    pairedInstrument?.let {
        CardIdentity(
            tscalpInstrumentId = it.tscalpInstrumentId,
            brokerName = pairedBroker,
            accountId = pairedAccountId,
        )
    }
