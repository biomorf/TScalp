package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.DebugFlagsRepository
import com.gitlab.biomorf.tscalp.domain.api.managers.OrderFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.usecases.CheckBrokerInitializationUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.DisconnectBrokerUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.InitializeBrokerUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.ToggleDebugFlagUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BrokerUseCaseModule {
    @Provides
    @Singleton
    fun provideCheckBrokerInitializationUseCase(
        brokerRegistry: BrokerRegistry,
        priceFlowManager: PriceFlowManager,
        positionFlowManager: PositionFlowManager,
        logger: Logger,
    ): CheckBrokerInitializationUseCase =
        CheckBrokerInitializationUseCase(
            brokerRegistry = brokerRegistry,
            priceFlowManager = priceFlowManager,
            positionFlowManager = positionFlowManager,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideInitializeBrokerUseCase(
        brokerRegistry: BrokerRegistry,
        credentialsRepository: BrokerCredentialsRepository,
        logger: Logger,
    ): InitializeBrokerUseCase =
        InitializeBrokerUseCase(
            brokerRegistry = brokerRegistry,
            credentialsRepository = credentialsRepository,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideDisconnectBrokerUseCase(
        brokerCredentialsRepository: BrokerCredentialsRepository,
        tradingStateRepository: TradingStateRepository,
        brokerRegistry: BrokerRegistry,
        positionFlowManager: PositionFlowManager,
        orderFlowManager: OrderFlowManager,
        logger: Logger,
    ): DisconnectBrokerUseCase =
        DisconnectBrokerUseCase(
            brokerCredentialsRepository = brokerCredentialsRepository,
            tradingStateRepository = tradingStateRepository,
            brokerRegistry = brokerRegistry,
            positionFlowManager = positionFlowManager,
            orderFlowManager = orderFlowManager,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideToggleDebugFlagUseCase(
        debugFlagsRepository: DebugFlagsRepository,
        priceFlowManager: PriceFlowManager,
        positionFlowManager: PositionFlowManager,
        orderFlowManager: OrderFlowManager,
        logger: Logger,
    ): ToggleDebugFlagUseCase =
        ToggleDebugFlagUseCase(
            debugFlagsRepository = debugFlagsRepository,
            priceFlowManager = priceFlowManager,
            positionFlowManager = positionFlowManager,
            orderFlowManager = orderFlowManager,
            logger = logger,
        )
}
