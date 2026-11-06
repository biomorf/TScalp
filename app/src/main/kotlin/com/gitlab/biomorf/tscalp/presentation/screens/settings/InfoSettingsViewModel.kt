package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.lifecycle.ViewModel
import com.gitlab.biomorf.tscalp.domain.api.DebugFlagsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.usecases.DebugFlag
import com.gitlab.biomorf.tscalp.domain.usecases.ToggleDebugFlagUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Состояние панели «О приложении» (debug switches section).
 */
data class InfoUiState(
    val disableGrpc: Boolean = false,
    val disableWs: Boolean = false,
    val forceRest: Boolean = false,
)

/**
 * ViewModel панели «О приложении» (clean-audit M006, A2).
 *
 * Тонкая: state + delegation в [ToggleDebugFlagUseCase]. Три фасада
 * (`toggleGrpc` / `toggleWs` / `toggleRest`) сохраняют UI-сигнатуру
 * (`onCheckedChange = { vm.toggleGrpc(it) }`), но оркестрация
 * (`set flag + reapply × 3 managers`) — в usecase, не в VM.
 *
 * `init` читает стартовые значения из [DebugFlagsRepository] (in-memory
 * `var`; внешних writers нет, реактивность не нужна — после `toggle`
 * state обновляется в VM).
 */
@HiltViewModel
class InfoSettingsViewModel
    @Inject
    constructor(
        private val debugFlagsRepository: DebugFlagsRepository,
        private val toggleDebugFlagUseCase: ToggleDebugFlagUseCase,
        private val logger: Logger,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                InfoUiState(
                    disableGrpc = debugFlagsRepository.disableGrpc,
                    disableWs = debugFlagsRepository.disableWs,
                    forceRest = debugFlagsRepository.forceRest,
                ),
            )
        val uiState: StateFlow<InfoUiState> = _uiState.asStateFlow()

        companion object {
            private const val TAG = "InfoSettingsViewModel"
        }

        /** Fire-and-forget: смена флага gRPC + применение на менеджерах. */
        fun toggleGrpc(enabled: Boolean) {
            logger.d(TAG, "toggleGrpc: enabled=$enabled")
            toggleDebugFlagUseCase.execute(DebugFlag.GRPC, enabled)
            _uiState.update { it.copy(disableGrpc = debugFlagsRepository.disableGrpc) }
        }

        /** Fire-and-forget: смена флага WS + применение на менеджерах. */
        fun toggleWs(enabled: Boolean) {
            logger.d(TAG, "toggleWs: enabled=$enabled")
            toggleDebugFlagUseCase.execute(DebugFlag.WS, enabled)
            _uiState.update { it.copy(disableWs = debugFlagsRepository.disableWs) }
        }

        /** Fire-and-forget: смена флага REST + применение на менеджерах. */
        fun toggleRest(enabled: Boolean) {
            logger.d(TAG, "toggleRest: enabled=$enabled")
            toggleDebugFlagUseCase.execute(DebugFlag.REST, enabled)
            _uiState.update { it.copy(forceRest = debugFlagsRepository.forceRest) }
        }
    }
