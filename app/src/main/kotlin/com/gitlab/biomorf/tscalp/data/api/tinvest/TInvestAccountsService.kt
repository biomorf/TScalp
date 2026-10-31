package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccountType
import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.CloseSandboxAccountRequest
import ru.tinkoff.piapi.contract.v1.GetAccountsRequest
import ru.tinkoff.piapi.contract.v1.MoneyValue
import ru.tinkoff.piapi.contract.v1.OpenSandboxAccountRequest
import ru.tinkoff.piapi.contract.v1.SandboxPayInRequest

/**
 * Счёта и sandbox-операции T-Invest.
 *
 * Выделен из `TInvestMarketDataService` (clean-audit Н63, шаг 1).
 * Отвечает за: счета, песочницу (открытие/закрытие счёта, пополнение).
 */
internal class TInvestAccountsService(
    private val state: TInvestApiState,
    private val logger: Logger,
) {
    suspend fun getAccounts(sandboxMode: Boolean): List<BrokerAccount> =
        withContext(Dispatchers.IO) {
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val request = GetAccountsRequest.getDefaultInstance()
            val accounts =
                if (sandboxMode) {
                    currentApi.sandboxServiceSync.getSandboxAccounts(request).accountsList
                } else {
                    currentApi.usersServiceSync.getAccounts(request).accountsList
                }
            accounts.map { acc ->
                BrokerAccount(
                    id = acc.id,
                    name = acc.name.ifBlank { "Счёт ${acc.id.take(ACCOUNT_ID_PREVIEW_LENGTH)}…" },
                    type =
                        when (acc.typeValue) {
                            1 -> BrokerAccountType.BROKER
                            2 -> BrokerAccountType.IIS
                            3 -> BrokerAccountType.INVEST_BOX
                            else -> BrokerAccountType.OTHER
                        },
                )
            }
        }

    suspend fun openSandboxAccount(): String =
        withContext(Dispatchers.IO) {
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val request = OpenSandboxAccountRequest.newBuilder().build()
            currentApi.sandboxServiceSync.openSandboxAccount(request).accountId
        }

    suspend fun closeSandboxAccount(accountId: String) =
        withContext(Dispatchers.IO) {
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val request =
                CloseSandboxAccountRequest
                    .newBuilder()
                    .setAccountId(accountId)
                    .build()
            currentApi.sandboxServiceSync.closeSandboxAccount(request)
            logger.d(TAG, "Счёт песочницы $accountId закрыт")
        }

    suspend fun sandboxPayIn(
        accountId: String,
        amount: SandboxMoney,
    ) = withContext(Dispatchers.IO) {
        val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
        val money =
            MoneyValue
                .newBuilder()
                .setCurrency(amount.currency)
                .setUnits(amount.units)
                .setNano(amount.nano)
                .build()
        val request =
            SandboxPayInRequest
                .newBuilder()
                .setAccountId(accountId)
                .setAmount(money)
                .build()
        currentApi.sandboxServiceSync.sandboxPayIn(request)
    }

    companion object {
        private const val TAG = "TInvestAccounts"

        /** Сколько символов id оставлять в fallback-имени счёта. */
        private const val ACCOUNT_ID_PREVIEW_LENGTH = 8
    }
}
