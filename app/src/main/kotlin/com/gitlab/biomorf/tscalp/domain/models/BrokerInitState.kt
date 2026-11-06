package com.gitlab.biomorf.tscalp.domain.models

/**
 * Состояние инициализации реестра брокеров.
 *
 * Возврат `CheckBrokerInitializationUseCase` (clean-audit M013):
 * проверка «есть ли хотя бы один инициализированный брокер»
 * (`BrokerRegistry.getAllBrokers().any { it.isInitialized }`).
 * При [NoneInitialized] usecase уже снял интерес к ценам
 * (`clearInterest`) и остановил `PositionFlowManager`; VM
 * остаётся очистить `UiState` и lifecycle-задачи
 * (`stopPositionUpdates()`).
 */
sealed interface BrokerInitState {
    /** Хотя бы один брокер инициализирован. */
    data object AnyInitialized : BrokerInitState

    /** Ни один брокер не инициализирован (logout / первый запуск). */
    data object NoneInitialized : BrokerInitState
}
