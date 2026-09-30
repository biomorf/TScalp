package com.example.tscalp.util

import com.example.tscalp.domain.models.AppError
import com.example.tscalp.domain.models.AppResult
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Оборачивает блок кода в AppResult.
 * CancellationException перебрасывается — она не ошибка, а отмена корутины.
 */
inline fun <T> runCatchingAppResult(block: () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppResult.Failure(e.toAppError())
    }

/**
 * Классификатор исключений → AppError.
 * Отдельно различаем сетевые ошибки, авторизацию и «не найдено».
 */
fun Throwable.toAppError(): AppError = when (this) {
    is UnknownHostException,
    is SocketTimeoutException -> AppError.Network(message ?: "Ошибка сети", this)

    is IOException -> {
        val msg = message.orEmpty()
        when {
            msg.contains("401", ignoreCase = true) ||
                    msg.contains("403", ignoreCase = true) ||
                    msg.contains("UNAUTHENTICATED", ignoreCase = true) ->
                AppError.Auth("Ошибка авторизации", this)

            msg.contains("NOT_FOUND", ignoreCase = true) ||
                    msg.contains("404", ignoreCase = true) ->
                AppError.NotFound("Не найдено", this)

            msg.contains("400", ignoreCase = true) ||
                    msg.contains("500", ignoreCase = true) ||
                    msg.contains("HTTP", ignoreCase = true) ->
                AppError.Api(msg, code = extractHttpCode(msg), this)

            else -> AppError.Network(msg.ifBlank { "Ошибка сети" }, this)
        }
    }

    else -> AppError.Unknown(message ?: "Неизвестная ошибка", this)
}

private fun extractHttpCode(message: String): Int? =
    Regex("""\b(\d{3})\b""").find(message)?.groupValues?.get(1)?.toIntOrNull()