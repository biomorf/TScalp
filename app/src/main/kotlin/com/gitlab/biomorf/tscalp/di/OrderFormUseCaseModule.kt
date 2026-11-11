package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountSelectionRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.InstrumentRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.MarketDataRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.usecases.LoadCardAccountsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.OpenCardDialogUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.RestoreOrderFormUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SaveCardSettingsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SelectInstrumentUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SelectPairedInstrumentUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OrderFormUseCaseModule {
    /**
     * 2c-фиксация: `LongParameterList` (7 > 6, detekt default). 7 зависимостей —
     * DI-wiring одного usecase. Альтернатива: `data class SelectInstrumentDeps`
     * (grouping) — overengineering для одной функции. Выгоды: одна функция
     * DI-wiring — стандартный паттерн Hilt.
     */
    @Suppress("LongParameterList")
    @Provides
    @Singleton
    fun provideSelectInstrumentUseCase(
        instrumentRepository: InstrumentRepository,
        marketDataRepository: MarketDataRepository,
        positionFlowManager: PositionFlowManager,
        cardsRepository: CardsRepository,
        accountSelectionRepository: AccountSelectionRepository,
        tradingStateRepository: TradingStateRepository,
        logger: Logger,
    ): SelectInstrumentUseCase =
        SelectInstrumentUseCase(
            instrumentRepository = instrumentRepository,
            marketDataRepository = marketDataRepository,
            positionFlowManager = positionFlowManager,
            cardsRepository = cardsRepository,
            accountSelectionRepository = accountSelectionRepository,
            tradingStateRepository = tradingStateRepository,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideSelectPairedInstrumentUseCase(
        marketDataRepository: MarketDataRepository,
        logger: Logger,
    ): SelectPairedInstrumentUseCase =
        SelectPairedInstrumentUseCase(
            marketDataRepository = marketDataRepository,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideRestoreOrderFormUseCase(
        tradingStateRepository: TradingStateRepository,
        instrumentRepository: InstrumentRepository,
        brokerRegistry: BrokerRegistry,
        logger: Logger,
    ): RestoreOrderFormUseCase =
        RestoreOrderFormUseCase(
            tradingStateRepository = tradingStateRepository,
            instrumentRepository = instrumentRepository,
            brokerRegistry = brokerRegistry,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideSaveCardSettingsUseCase(
        cardsRepository: CardsRepository,
        logger: Logger,
    ): SaveCardSettingsUseCase =
        SaveCardSettingsUseCase(
            cardsRepository = cardsRepository,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideOpenCardDialogUseCase(
        cardsRepository: CardsRepository,
        accountSelectionRepository: AccountSelectionRepository,
        loadCardAccountsUseCase: LoadCardAccountsUseCase,
        sandboxPreferencesRepository: SandboxPreferencesRepository,
        logger: Logger,
    ): OpenCardDialogUseCase =
        OpenCardDialogUseCase(
            cardsRepository = cardsRepository,
            accountSelectionRepository = accountSelectionRepository,
            loadCardAccountsUseCase = loadCardAccountsUseCase,
            sandboxPreferencesRepository = sandboxPreferencesRepository,
            logger = logger,
        )
}
