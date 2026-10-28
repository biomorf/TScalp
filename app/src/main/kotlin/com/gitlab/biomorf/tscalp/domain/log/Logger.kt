package com.gitlab.biomorf.tscalp.domain.log

/**
 * Абстракция логирования для domain-слоя.
 * Не зависит от платформы (Android/KMP).
 */
interface Logger {
    fun d(
        tag: String,
        message: String,
    )

    fun i(
        tag: String,
        message: String,
    )

    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}
