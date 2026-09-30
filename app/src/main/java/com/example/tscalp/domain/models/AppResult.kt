package com.example.tscalp.domain.models

/**
 * Типизированный результат операции: успех со значением или ошибка.
 *
 * Заменяет разрозненные try/catch и «магические» null-возвраты.
 * Все слои (data → repository → ViewModel → UI) общаются через этот контракт.
 */
sealed class AppResult<out T> {

    data class Success<T>(val data: T) : AppResult<T>()

    data class Failure(val error: AppError) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.data
}

/**
 * Доменные категории ошибок.
 * Позволяют UI показывать разное сообщение и по-разному реагировать:
 *  - Network — предложить «Повторить»;
 *  - Auth — предложить «Переподключиться»;
 *  - NotFound — не повторять, а показать пустое состояние;
 *  - Api/Unknown — логировать и отправлять в AppMetrica.
 */
sealed class AppError(open val message: String, open val cause: Throwable? = null) {
    data class Network(
        override val message: String = "Ошибка сети",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class Auth(
        override val message: String = "Ошибка авторизации",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class NotFound(
        override val message: String = "Не найдено",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class Api(
        override val message: String,
        val code: Int? = null,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class Unknown(
        override val message: String = "Неизвестная ошибка",
        override val cause: Throwable? = null
    ) : AppError(message, cause)
}