package com.example.tscalp.data.repository

import com.example.tscalp.data.api.TinkoffInvestService
import com.example.tscalp.domain.models.AccountType
import com.example.tscalp.domain.models.AccountUi
import com.example.tscalp.domain.models.OrderResult
import com.example.tscalp.domain.models.OrderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.OrderDirection
import ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus

class InvestRepository(
    private val apiService: TinkoffInvestService
) {

    suspend fun getAccounts(): List<AccountUi> = withContext(Dispatchers.IO) {
        apiService.getAccounts().map { account ->
            AccountUi(
                id = account.id,
                name = account.name,
                type = when (account.typeValue) {
                    1 -> AccountType.BROKER      // ACCOUNT_TYPE_BROKER = 1
                    2 -> AccountType.IIS          // ACCOUNT_TYPE_IIS = 2
                    3 -> AccountType.INVEST_BOX   // ACCOUNT_TYPE_INVEST_BOX = 3
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
            status = when (response.executionReportStatusValue) {
                1 -> OrderStatus.NEW               // EXECUTION_REPORT_STATUS_NEW = 1
                2 -> OrderStatus.PARTIALLY_FILLED  // EXECUTION_REPORT_STATUS_PARTIALLYFILL = 2
                3 -> OrderStatus.FILLED            // EXECUTION_REPORT_STATUS_FILL = 3
                4 -> OrderStatus.REJECTED          // EXECUTION_REPORT_STATUS_REJECTED = 4
                5 -> OrderStatus.CANCELLED         // EXECUTION_REPORT_STATUS_CANCELLED = 5
                else -> OrderStatus.NEW
            }
        )
    }
}