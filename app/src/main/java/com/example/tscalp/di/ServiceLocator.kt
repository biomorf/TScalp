package com.example.tscalp.di

import com.example.tscalp.BuildConfig
import android.content.Context
import android.content.SharedPreferences
import ru.ttech.piapi.core.InvestApi

/**
 * Глобальный синглтон для хранения клиента InvestApi.
 * Отвечает за создание, восстановление и очистку подключения.
 */
object ServiceLocator {

    @Volatile
    private var api: InvestApi? = null
    private lateinit var prefs: SharedPreferences

    /**
     * Инициализирует хранилище настроек. Вызвать один раз из Application.
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences("tinvest_prefs", Context.MODE_PRIVATE)
    }

    /**
     * Возвращает текущий клиент API, если он уже создан.
     */
    fun getApiOrNull(): InvestApi? = api

    /**
     * Создаёт новый клиент InvestApi с указанным токеном и режимом.
     * В релизных сборках принудительно сбрасывает режим песочницы.
     * Сохраняет токен и режим в SharedPreferences.
     */
    fun createApi(token: String, sandbox: Boolean): InvestApi {
        // В релизе всегда используем боевой режим, игнорируя переданный флаг
        val effectiveSandbox = if (BuildConfig.DEBUG) sandbox else false

        val target = if (effectiveSandbox) {
            "sandbox-invest-public-api.tbank.ru:443"
        } else {
            "invest-public-api.tbank.ru:443"
        }

        val newApi = InvestApi.createApi(InvestApi.defaultChannel(token = token, target = target))
        api = newApi

        // Сохраняем токен и фактический режим в настройках
        prefs.edit()
            .putString("api_token", token)
            .putBoolean("sandbox_mode", effectiveSandbox)
            .apply()

        return newApi
    }

    /**
     * Пытается восстановить подключение из сохранённого токена.
     * @return клиент API или null, если токен не найден
     */
    fun tryRestoreApi(): InvestApi? {
        val token = prefs.getString("api_token", null) ?: return null
        // Для релизных сборок всегда используем боевой режим
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
     * Удаляет сохранённый токен и сбрасывает клиент.
     */
    fun clear() {
        api = null
        prefs.edit().remove("api_token").remove("sandbox_mode").apply()
    }

    /**
     * Проверяет, сохранён ли токен в настройках.
     */
    fun hasSavedToken(): Boolean = prefs.contains("api_token")

    // Новые методы для работы с режимом
    fun isSandboxMode(): Boolean = prefs.getBoolean("sandbox_mode", true)

    fun getToken(): String? = prefs.getString("api_token", null)

    /**
     * Возвращает true, если подтверждение заявок включено (по умолчанию – включено).
     */
    fun isConfirmOrdersEnabled(): Boolean {
        return prefs.getBoolean("confirm_orders_enabled", true)
    }

    /**
     * Сохраняет флаг подтверждения заявок.
     */
    fun setConfirmOrdersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("confirm_orders_enabled", enabled).apply()
    }
}