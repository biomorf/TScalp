package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.flow.Flow

/**
 * Domain-контракт выбранного счёта для выставления заявки
 * (форма Orders, per-broker) — разбиение `OrdersViewModel` на
 * 4 VM, шаг 3.
 *
 * **Не путать с:**
 * - [AccountsRepository] — список всех счетов брокера (источник
 *   данных, не выбор);
 * - [AccountPreferencesRepository] — default-счёт из Настроек
 *   (для open/close sandbox), не выбор для формы.
 *
 * Портфель показывает все счета независимо от этого выбора.
 * Персистентность — DataStore, ключ `<brokerName.key>_selected_order_account`.
 */
interface AccountSelectionRepository {
    fun selected(brokerName: BrokerName): Flow<String?>

    suspend fun select(
        brokerName: BrokerName,
        accountId: String?,
    )

    suspend fun clear(brokerName: BrokerName)
}
