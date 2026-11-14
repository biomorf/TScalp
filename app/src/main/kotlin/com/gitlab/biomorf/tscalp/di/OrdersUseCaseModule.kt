package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountSelectionRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.OrdersRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.PortfolioRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.usecases.CalculatePairExecUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.CalculateTradeDetailsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.CancelOrderUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PostOrderUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PrepareOrderRequestUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SaveFormStateUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SyncPriceInterestUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OrdersUseCaseModule {
    @Provides
    @Singleton
    fun provideCalculateTradeDetailsUseCase(): CalculateTradeDetailsUseCase = CalculateTradeDetailsUseCase()

    @Provides
    @Singleton
    fun providePrepareOrderRequestUseCase(): PrepareOrderRequestUseCase = PrepareOrderRequestUseCase()

    @Provides
    @Singleton
    fun provideSaveFormStateUseCase(
        tradingStateRepository: TradingStateRepository,
    ): SaveFormStateUseCase = SaveFormStateUseCase(tradingStateRepository)

    @Provides
    @Singleton
    fun provideSyncPriceInterestUseCase(
        priceFlowManager: PriceFlowManager,
    ): SyncPriceInterestUseCase = SyncPriceInterestUseCase(priceFlowManager)

    @Provides
    @Singleton
    fun provideCalculatePairExecUseCase(): CalculatePairExecUseCase = CalculatePairExecUseCase()

    @Provides
    @Singleton
    fun provideCancelOrderUseCase(
        accountsRepository: AccountsRepository,
        ordersRepository: OrdersRepository,
        logger: Logger,
    ): CancelOrderUseCase =
        CancelOrderUseCase(
            accountsRepository = accountsRepository,
            ordersRepository = ordersRepository,
            logger = logger,
        )

    @Provides
    @Singleton
    fun providePostOrderUseCase(
        ordersRepository: OrdersRepository,
        prepareOrderRequest: PrepareOrderRequestUseCase,
    ): PostOrderUseCase =
        PostOrderUseCase(
            ordersRepository = ordersRepository,
            prepareOrderRequest = prepareOrderRequest,
        )

    @Provides
    @Singleton
    fun providePlaceOrderUseCase(
        postOrderUseCase: PostOrderUseCase,
        cardsRepository: CardsRepository,
        accountSelectionRepository: AccountSelectionRepository,
        portfolioRepository: PortfolioRepository,
        logger: Logger,
    ): PlaceOrderUseCase =
        PlaceOrderUseCase(
            postOrderUseCase = postOrderUseCase,
            cardsRepository = cardsRepository,
            accountSelectionRepository = accountSelectionRepository,
            portfolioRepository = portfolioRepository,
            logger = logger,
        )
}
