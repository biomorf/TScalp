package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import javax.inject.Inject

/**
 * Входные данные для расчёта параметров парной заявки.
 *
 * Presentation-поля (сырые строки TextField, UI-состояние) сюда не входят:
 * VM резолвит их в domain-значения до вызова UseCase.
 */
data class PairExecInput(
    val pairTradingEnabled: Boolean,
    val hasPairedInstrument: Boolean,
    val orderType: OrderTypeSelection,
    val limitPrice: String?,
    val stopPrice: String?,
    val pendingDirection: OrderDirection?,
    val executionPrice: Double,
    val pairCurrentPrice: Double?,
)

/** Результат расчёта: цена исполнения парной заявки + её тип. */
data class PairExecResult(
    val pairExecPrice: Double,
    val pairedOrderType: OrderTypeSelection?,
)

/**
 * Расчёт параметров парной заявки на основе основной.
 *
 * Правило: при `pairTradingEnabled` и наличии парного инструмента
 * тип парной заявки определяется `PairOrderMapper` по типу основной
 * и её направлению; цена исполнения — `executionPrice` для лимитных,
 * иначе `pairCurrentPrice`.
 */
class CalculatePairExecUseCase
    @Inject
    constructor() {
        fun execute(input: PairExecInput): PairExecResult {
            if (!input.pairTradingEnabled || !input.hasPairedInstrument) {
                return PairExecResult(pairExecPrice = 0.0, pairedOrderType = null)
            }
            val primaryPrice =
                when (input.orderType) {
                    OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> input.limitPrice?.toDoubleOrNull()
                    OrderTypeSelection.StopLoss, OrderTypeSelection.TakeProfit -> input.stopPrice?.toDoubleOrNull()
                    else -> null
                }
            val direction = input.pendingDirection ?: OrderDirection.BUY
            val pairedSpec = PairOrderMapper.map(input.orderType, direction, primaryPrice)
            val pairExecPrice =
                when (input.orderType) {
                    OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> input.executionPrice
                    else -> input.pairCurrentPrice ?: 0.0
                }
            return PairExecResult(pairExecPrice = pairExecPrice, pairedOrderType = pairedSpec.orderType)
        }
    }
