package com.example.tscalp.data.repository

import com.example.tscalp.data.api.TinkoffInvestService
import com.example.tscalp.domain.models.AccountUi
import com.example.tscalp.domain.models.AccountType
import com.example.tscalp.domain.models.OrderResult
import com.example.tscalp.domain.models.OrderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.OrderDirection

class InvestRepository(
    private val apiService: TinkoffInvestService
) {
    
    suspend fun getAccounts(): List<AccountUi> = withContext(Dispatchers.IO) {
        apiService.getAccounts().map { account ->
            AccountUi(
                id = account.id,
                name = account.name,
                type = when (account.type) {
                    ru.tinkoff.piapi.contract.v1.AccountType.ACCOUNT_TYPE_BROKER -> AccountType.BROKER
                    ru.tinkoff.piapi.contract.v1.AccountType.ACCOUNT_TYPE_IIS -> AccountType.IIS
                    ru.tinkoff.piapi.contract.v1.AccountType.ACCOUNT_TYPE_INVEST_BOX -> AccountType.INVEST_BOX
                    else -> AccountType.BROKER
                }
            )
        }
    }
    
    suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String
    ): OrderResult = withContext(Dispatchers.IO) {
        val response = apiService.postMarketOrder(figi, quantity, direction, accountId)
        OrderResult(
            orderId = response.orderId,
            executedLots = response.executedOrderLots,
            totalLots = response.totalOrderLots,
            status = when (response.executionReportStatus) {
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_NEW -> OrderStatus.NEW
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_PARTIALLYFILL -> OrderStatus.PARTIALLY_FILLED
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_FILL -> OrderStatus.FILLED
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_REJECTED -> OrderStatus.REJECTED
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_CANCELLED -> OrderStatus.CANCELLED
                else -> OrderStatus.NEW
            }
        )
    }
}