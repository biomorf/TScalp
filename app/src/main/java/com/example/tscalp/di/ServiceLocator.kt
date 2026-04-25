package com.example.tscalp.di

import android.content.Context
import android.content.SharedPreferences
import ru.ttech.piapi.core.InvestApi
import com.example.tscalp.data.api.TinkoffInvestService
import com.example.tscalp.BuildConfig
import com.example.tscalp.data.api.MockBrokerApi
import com.example.tscalp.domain.api.BrokerApi
import com.example.tscalp.data.api.BcsBrokerApi

/**
 * ///Глобальный синглтон для хранения клиента InvestApi, брокер‑менеджера и настроек.
 * ///Все экраны используют его, чтобы получать единое состояние подключения.
 */
object ServiceLocator {

    @Volatile
    private var api: InvestApi? = null

    private lateinit var prefs: SharedPreferences

    @Volatile
    private var brokerManager: BrokerManager? = null

    /**
     * ///Инициализирует SharedPreferences. Вызвать один раз из Application.
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences("tinvest_prefs", Context.MODE_PRIVATE)
    }

    /**
     * ///Возвращает текущий экземпляр InvestApi или null, если ещё не создан.
     */
    fun getApiOrNull(): InvestApi? = api

    /**
     * ///Создаёт новый клиент InvestApi с указанным токеном и режимом.
     * ///В релизных сборках принудительно сбрасывает режим песочницы.
     */
    fun createApi(token: String, sandbox: Boolean): InvestApi {
        val effectiveSandbox = if (BuildConfig.DEBUG) sandbox else false

        val target = if (effectiveSandbox) {
            "sandbox-invest-public-api.tbank.ru:443"
        } else {
            "invest-public-api.tbank.ru:443"
        }

        val newApi = InvestApi.createApi(InvestApi.defaultChannel(token = token, target = target))
        api = newApi

        prefs.edit()
            .putString("api_token", token)
            .putBoolean("sandbox_mode", effectiveSandbox)
            .apply()

        // После успешного создания api пересоздаём брокер‑менеджер (чтобы использовать новый api)
        brokerManager = createBrokerManager()

        return newApi
    }

    /**
     * ///Пытается восстановить подключение из сохранённого токена.
     * ///В релизных сборках всегда используется боевой режим.
     */
    fun tryRestoreApi(): InvestApi? {
        val token = prefs.getString("api_token", null) ?: return null
        val sandbox = if (BuildConfig.DEBUG) {
            prefs.getBoolean("sandbox_mode", true)
        } else {
            false
        }
        return try {
            createApi(token, sandbox)
        } catch (e: Exception) {
            clear()
            null
        }
    }

    /**
     * ///Очищает сохранённый токен, API и брокер‑менеджер.
     */
    fun clear() {
        api = null
        brokerManager = null
        prefs.edit()
            .remove("api_token")
            .remove("sandbox_mode")
            .apply()
    }

    /**
     * ///Возвращает true, если в настройках сохранён токен.
     */
    fun hasSavedToken(): Boolean = prefs.contains("api_token")

    /**
     * ///Возвращает текущий режим песочницы (из сохранённых настроек).
     */
    fun isSandboxMode(): Boolean = prefs.getBoolean("sandbox_mode", true)

    /**
     * ///Возвращает сохранённый токен или null.
     */
    fun getToken(): String? = prefs.getString("api_token", null)

    private fun createBrokerManager(): BrokerManager {
        val brokers: Map<String, BrokerApi> = mapOf(
            "tinkoff" to TinkoffInvestService(),
            "mock" to MockBrokerApi(),
            "bcs" to BcsBrokerApi()
        )
        return BrokerManager(brokers)
    }

    /**
     * ///Возвращает глобальный брокер‑менеджер.
     * ///При первом вызове создаёт его с брокером по умолчанию (Tinkoff).
     */
    fun getBrokerManager(): BrokerManager {
        return brokerManager ?: synchronized(this) {
            brokerManager ?: createBrokerManager().also { brokerManager = it }
        }
    }

    /// --- Управление флагом подтверждения заявок ---

    /**
     * ///Возвращает true, если диалог подтверждения заявок включён (по умолчанию – включён).
     */
    fun isConfirmOrdersEnabled(): Boolean = prefs.getBoolean("confirm_orders_enabled", true)

    /**
     * ///Сохраняет флаг подтверждения заявок.
     */
    fun setConfirmOrdersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("confirm_orders_enabled", enabled).apply()
    }

    /**
     * Сохраняет токен и режим песочницы для указанного брокера.
     */
    fun saveBrokerCredentials(brokerName: String, token: String, sandbox: Boolean) {
        prefs.edit()
            .putString("${brokerName}_token", token)
            .putBoolean("${brokerName}_sandbox", sandbox)
            .apply()
    }

    /**
     * Загружает токен и режим песочницы для указанного брокера.
     * @return Pair(token, sandbox) или null, если настройки не найдены.
     */
    fun loadBrokerCredentials(brokerName: String): Pair<String, Boolean>? {
        val token = prefs.getString("${brokerName}_token", null) ?: return null
        val sandbox = prefs.getBoolean("${brokerName}_sandbox", true)
        return Pair(token, sandbox)
    }

    /**
     * Удаляет сохранённые настройки для указанного брокера.
     */
    fun clearBrokerCredentials(brokerName: String) {
        prefs.edit()
            .remove("${brokerName}_token")
            .remove("${brokerName}_sandbox")
            .apply()
    }
}