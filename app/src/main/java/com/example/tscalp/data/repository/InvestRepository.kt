package com.example.tscalp.data.repository

import android.util.Log
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

    companion object {
        private const val TAG = "InvestRepository"
    }

    suspend fun getAccounts(): List<AccountUi> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Получение списка счетов")
            val accounts = apiService.getAccounts()
            Log.d(TAG, "Получено ${accounts.size} счетов от API")

            val uiAccounts = accounts.map { account ->
                Log.d(TAG, "Счет: id=${account.id}, name=${account.name}, type=${account.type}")
                AccountUi(
                    id = account.id,
                    name = account.name,
                    type = AccountType.BROKER
                )
            }
            Log.d(TAG, "Преобразовано ${uiAccounts.size} счетов")
            uiAccounts
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения счетов", e)
            throw e
        }
    }

    suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String
    ): OrderResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Отправка рыночной заявки: $figi, $quantity")
            val response = apiService.postMarketOrder(figi, quantity, direction, accountId)
            OrderResult(
                orderId = response.orderId,
                executedLots = 0L,
                totalLots = quantity,
                status = OrderStatus.NEW
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отправки заявки", e)
            throw e
        }
    }
}