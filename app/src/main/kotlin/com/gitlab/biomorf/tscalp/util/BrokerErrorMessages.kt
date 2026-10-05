package com.gitlab.biomorf.tscalp.util

import com.gitlab.biomorf.tscalp.domain.models.BrokerName

/**
 * Центральный реестр расшифровок кодов ошибок брокеров.
 *
 * Каждый брокер имеет свою таблицу. Если брокер ещё не заведён
 * или код не найден — возвращается generic-fallback с кодом.
 *
 * Чтобы добавить нового брокера:
 *  1. Создайте файл <Broker>ErrorMessages.kt с картой Map<String, String>.
 *  2. Добавьте его в [tables] ниже.
 */
object BrokerErrorMessages {

    private val tables: Map<BrokerName, Map<String, String>> = mapOf(
        BrokerName.TINVEST to TInvestErrorMessages.messages
        // BrokerName.BCS to BcsErrorMessages.messages,   // TODO
        // BrokerName.FINAM to FinamErrorMessages.messages // TODO
    )

    /**
     * Расшифровывает код ошибки брокера в человекочитаемое сообщение.
     * Возвращает fallback с кодом, если брокер/код не заведены.
     */
    fun translate(brokerName: BrokerName, code: String): String {
        val mapped = tables[brokerName]?.get(code)
        if (mapped != null) return mapped

        return "Ошибка биржи (код $code). Попробуйте позже или проверьте параметры."
    }

    /**
     * Проверяет, известен ли код для брокера.
     * Полезно для аналитики и логов: подсветить незнакомые коды.
     */
    fun isKnown(brokerName: BrokerName, code: String): Boolean =
        tables[brokerName]?.containsKey(code) == true
}
