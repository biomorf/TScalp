package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.DebugFlagsRepository
import com.gitlab.biomorf.tscalp.domain.api.managers.OrderFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.log.Logger

/**
 * Диагностический флаг транспорта. Один кейс = один тумблер в UI.
 *
 * `REST` — «принудительный REST» (`forceRest`), не «отключить REST»:
 * флаг bypass'ит gRPC-unary в пользу REST. `GRPC` и `WS` — наоборот,
 * «отключить» (`disableGrpc`, `disableWs`). Семантика каждого флага —
 * в [DebugFlagsRepository].
 */
enum class DebugFlag {
    GRPC,
    WS,
    REST,
}

/**
 * Переключение debug-флага транспорта + применение к менеджерам
 * (clean-audit M006, M2).
 *
 * Вынесено из `SettingsViewModel.toggleGrpc/Ws/Rest`: каждый —
 * set flag + `reapplyDebugFlags()` на трёх менеджерах
 * (Price / Position / Order), ~6 строк orchestration. Не тонкий
 * (M2), поэтому — в usecase, симметрично [DisconnectBrokerUseCase].
 *
 * **Не suspend:** все операции синхронные (setter `var` + `fun
 * reapplyDebugFlags`). Fire-and-forget в VM (`viewModelScope.launch`
 * только для `_uiState.update` после возврата).
 *
 * UI-переключатели не растут: три (`gRPC` / `WS` / `REST`), три
 * тонких метода в `InfoSettingsViewModel` — фасады над
 * `execute(GRPC/WS/REST, enabled)`.
 */
class ToggleDebugFlagUseCase
    constructor(
        private val debugFlagsRepository: DebugFlagsRepository,
        private val priceFlowManager: PriceFlowManager,
        private val positionFlowManager: PositionFlowManager,
        private val orderFlowManager: OrderFlowManager,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "ToggleDebugFlagUseCase"
        }

        /**
         * Устанавливает [flag] в [enabled] и перезапускает стримы /
         * polling всех менеджеров с новыми флагами.
         */
        fun execute(
            flag: DebugFlag,
            enabled: Boolean,
        ) {
            logger.d(TAG, "execute: flag=$flag, enabled=$enabled")
            when (flag) {
                DebugFlag.GRPC -> debugFlagsRepository.disableGrpc = enabled
                DebugFlag.WS -> debugFlagsRepository.disableWs = enabled
                DebugFlag.REST -> debugFlagsRepository.forceRest = enabled
            }
            priceFlowManager.reapplyDebugFlags()
            positionFlowManager.reapplyDebugFlags()
            orderFlowManager.reapplyDebugFlags()
        }
    }
