package com.gitlab.biomorf.tscalp.data.log

import android.util.Log
import com.gitlab.biomorf.tscalp.BuildConfig
import com.gitlab.biomorf.tscalp.domain.log.Logger
import javax.inject.Inject

/**
 * Реализация [Logger] для Android.
 * - Debug/Verbose отсекаются в release.
 * - Единая точка для будущей отправки в AppMetrica.
 */
class AndroidAppLogger
    @Inject
    constructor() : Logger {
        override fun d(
            tag: String,
            message: String,
        ) {
            if (BuildConfig.DEBUG) Log.d(tag, message)
        }

        override fun i(
            tag: String,
            message: String,
        ) {
            Log.i(tag, message)
        }

        override fun w(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        }

        override fun e(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            // AppMetrica hook here later
        }
    }
