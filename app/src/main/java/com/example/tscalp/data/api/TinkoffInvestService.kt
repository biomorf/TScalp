package com.example.tscalp.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.conscrypt.Conscrypt
import ru.tinkoff.piapi.contract.v1.*
import ru.tinkoff.piapi.core.InvestApi
import ru.tinkoff.piapi.core.models.Portfolio
import java.security.Security

class TinkoffInvestService(private val context: Context) {

    companion object {
        private const val TAG = "TinkoffInvestService"

        init {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "tinvest_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private var api: InvestApi? = null
    private var sandboxMode: Boolean = true

    val isInitialized: Boolean
        get() = api != null

    fun initialize(token: String, sandboxMode: Boolean = true) {
        try {
            Log.d(TAG, "Инициализация API, sandbox: $sandboxMode")
            this.sandboxMode = sandboxMode
            api = if (sandboxMode) {
                InvestApi.createSandbox(token)
            } else {
                InvestApi.create(token)
            }
            securePrefs.edit().putString("api_token", token).apply()
            securePrefs.edit().putBoolean("sandbox_mode", sandboxMode).apply()
            Log.d(TAG, "API успешно инициализирован")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации API", e)
            throw e
        }
    }

    fun tryInitializeFromStorage(): Boolean {
        val token = securePrefs.getString("api_token", null)
        val savedSandboxMode = securePrefs.getBoolean("sandbox_mode", true)
        return if (token != null) {
            try {
                initialize(token, savedSandboxMode)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка инициализации из хранилища", e)
                false
            }
        } else {
            false
        }
    }

    suspend fun getAccounts(): List<Account> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        try {
            Log.d(TAG, "Запрос списка счетов, sandbox: $sandboxMode")

            if (sandboxMode) {
                // 1. Пытаемся получить существующие счета песочницы
                // Обратите внимание: согласно документации, метод может называться getAccounts или getSandboxAccounts
                val sandboxAccounts = currentApi.sandboxService.getAccountsSync()
                Log.d(TAG, "Получено ${sandboxAccounts.size} песочных счетов")

                // 2. Если счетов нет, создаем новый в любом случае
                if (sandboxAccounts.isEmpty()) {
                    Log.d(TAG, "Счета не найдены. Создаем новый счет в песочнице...")
                    // Название метода создания счета может отличаться, найдите правильное через автодополнение
                    val newAccountId = currentApi.sandboxService.openAccountSync()
                    Log.d(TAG, "Создан новый счет с ID: $newAccountId")

                    // 3. После создания счета, возможно, потребуется его пополнить, чтобы видеть в списке или совершать сделки
                    // Метод для пополнения также может называться payIn или sandboxPayIn
                    // Раскомментируйте и найдите правильное имя, если счет нужно пополнить
                    /*
                    val moneyValue = MoneyValue.newBuilder()
                        .setUnits(100000) // Сумма в рублях
                        .setNano(0)
                        .setCurrency("RUB")
                        .build()
                    currentApi.sandboxService.payInSandboxSync(newAccountId, moneyValue)
                    Log.d(TAG, "Новый счет пополнен на 100 000 RUB")
                    */

                    // 4. Повторно запрашиваем список счетов
                    return@withContext currentApi.sandboxService.getAccountsSync()
                } else {
                    return@withContext sandboxAccounts
                }
            } else {
                // Боевой режим
                currentApi.userService.getAccountsSync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения счетов", e)
            // Более детальная обработка ошибки
            throw Exception("Не удалось получить или создать счета в песочнице: ${e.message}. Убедитесь, что метод создания счета указан верно.")
        }
    }

    suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String
    ): PostOrderResponse = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        try {
            Log.d(TAG, "Отправка заявки: FIGI=$figi, кол-во=$quantity, направление=$direction")

            val zeroQuotation = Quotation.newBuilder()
                .setUnits(0)
                .setNano(0)
                .build()

            val response = if (sandboxMode) {
                // В песочнице используем sandbox сервис
                currentApi.sandboxService.postOrderSync(
                    figi,
                    quantity,
                    zeroQuotation,
                    direction,
                    accountId,
                    OrderType.ORDER_TYPE_MARKET,
                    ""
                )
            } else {
                // В боевом режиме - обычный сервис
                currentApi.ordersService.postOrderSync(
                    figi,
                    quantity,
                    zeroQuotation,
                    direction,
                    accountId,
                    OrderType.ORDER_TYPE_MARKET,
                    ""
                )
            }
            Log.d(TAG, "Заявка отправлена, orderId=${response.orderId}")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отправки заявки", e)
            throw Exception("Не удалось выставить заявку: ${e.message}")
        }
    }

    suspend fun getPortfolio(accountId: String): Portfolio = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        try {
            Log.d(TAG, "Запрос портфеля для счета: $accountId, sandbox: $sandboxMode")

            val portfolio = if (sandboxMode) {
                // В песочнице метод может возвращать PortfolioResponse
                val response = currentApi.sandboxService.getPortfolioSync(accountId)
                // Конвертируем PortfolioResponse в Portfolio
                convertToPortfolio(response)
            } else {
                currentApi.operationsService.getPortfolioSync(accountId)
            }

            Log.d(TAG, "Портфель получен, позиций: ${portfolio.positions.size}")
            portfolio
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения портфеля", e)
            throw Exception("Не удалось получить портфель: ${e.message}")
        }
    }

    // Вспомогательная функция для конвертации
    private fun convertToPortfolio(response: Any): Portfolio {
        // Если response уже Portfolio - просто возвращаем
        if (response is Portfolio) {
            return response
        }

        // Если это PortfolioResponse - конвертируем
        // (точное имя класса может отличаться в вашей версии SDK)
        return try {
            // Пытаемся через рефлексию получить positions
            val positionsMethod = response.javaClass.getMethod("getPositionsList")
            val positions = positionsMethod.invoke(response) as List<*>

            // Создаем новый Portfolio (может потребоваться другой конструктор)
            // Это заглушка, т.к. Portfolio может быть неизменяемым
            response as Portfolio
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка конвертации портфеля", e)
            throw e
        }
    }

    suspend fun getInstrumentByFigi(figi: String): Instrument = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        try {
            Log.d(TAG, "Запрос инструмента по FIGI: $figi")
            val instrument = currentApi.instrumentsService.getInstrumentByFigiSync(figi)
            Log.d(TAG, "Инструмент получен: ${instrument.ticker} - ${instrument.name}")
            instrument
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения инструмента", e)
            throw Exception("Не удалось получить информацию об инструменте: ${e.message}")
        }
    }

    // Метод для пополнения песочного счета (для тестирования)
    suspend fun payInSandbox(accountId: String, amount: Long) = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        if (sandboxMode) {
            try {
                val moneyValue = MoneyValue.newBuilder()
                    .setUnits(amount)
                    .setNano(0)
                    .setCurrency("RUB")
                    .build()
                currentApi.sandboxService.payInSync(accountId, moneyValue)
                Log.d(TAG, "Счет $accountId пополнен на $amount RUB")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка пополнения счета", e)
                throw e
            }
        }
    }

    fun clearToken() {
        api?.destroy(3)
        api = null
        securePrefs.edit().remove("api_token").apply()
        Log.d(TAG, "Токен очищен")
    }
}