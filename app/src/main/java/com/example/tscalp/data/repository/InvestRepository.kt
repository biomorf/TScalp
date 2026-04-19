package com.example.tscalp.data.repository

import com.example.tscalp.data.api.TinkoffInvestService
import com.example.tscalp.domain.models.AccountType
import com.example.tscalp.domain.models.AccountUi
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
                type = AccountType.BROKER  // Упрощаем для начала
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
            executedLots = response.lotsExecuted,  // ✅ Правильное название поля
            totalLots = response.lotsRequested,     // ✅ Правильное название поля
            status = OrderStatus.NEW  // Упрощаем для начала
        )
    }
}