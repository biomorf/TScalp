package com.gitlab.biomorf.tscalp.domain.log

/** No-op Logger для тестов (логирование не верифицируется). */
@Suppress("EmptyFunctionBlock")
internal object TestLogger : Logger {
    override fun d(
        tag: String,
        message: String,
    ) {}

    override fun i(
        tag: String,
        message: String,
    ) {}

    override fun w(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {}

    override fun e(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {}
}
