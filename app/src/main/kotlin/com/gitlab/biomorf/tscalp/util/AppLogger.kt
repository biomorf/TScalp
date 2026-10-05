package com.gitlab.biomorf.tscalp.util

import android.util.Log
import com.gitlab.biomorf.tscalp.BuildConfig

/**
 * Централизованная обёртка над android.util.Log.
 *
 * - В release-сборке уровни Debug/Verbose отсекаются (нет накладных расходов и шума).
 * - Единая точка для отправки ошибок в AppMetrica (крючок в e()).
 * - Сохраняет контракт Log.*: tag, message, опциональный throwable.
 */
object AppLogger {

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag, message, throwable)
        else Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable)
        else Log.e(tag, message)

        // Крючок для AppMetrica (раскомментировать, когда согласуем политику):
        // if (BuildConfig.DEBUG.not() && throwable != null) {
        //     AppMetrica.reportError(message, throwable)
        // }
    }
}
