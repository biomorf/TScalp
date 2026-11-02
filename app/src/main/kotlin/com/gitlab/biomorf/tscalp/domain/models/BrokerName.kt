package com.gitlab.biomorf.tscalp.domain.models

/**
 * Уникальные имена брокеров, поддерживаемых приложением.
 *
 * @property key строковый идентификатор для SharedPreferences и BrokerManager
 *                 (совместим с legacy-ключами "TInvest_token", "bcs_token", "finam_token").
 * @property displayName человекочитаемое имя для отображения в UI.
 */
enum class BrokerName(
    val key: String,
    val displayName: String,
    val domesticCurrency: String,
) {
    TINVEST("TInvest", "Т-Инвестиции", "rub"),
    BCS("bcs", "БКС", "rub"),
    FINAM("finam", "Финам", "rub"),
    ;

    companion object {
        fun fromKey(key: String): BrokerName? = entries.firstOrNull { it.key == key }
    }
}
