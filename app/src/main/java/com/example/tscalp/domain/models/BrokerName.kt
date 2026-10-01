package com.example.tscalp.domain.models

/**
 * Уникальные имена брокеров, поддерживаемых приложением.
 *
 * @property key строковый идентификатор для SharedPreferences и BrokerManager
 *                 (совместим с legacy-ключами "TInvest_token", "bcs_token", "finam_token").
 * @property displayName человекочитаемое имя для отображения в UI.
 */
enum class BrokerName(val key: String, val displayName: String) {
    TINVEST("TInvest", "Т-Инвестиции"),
    BCS("bcs", "БКС"),
    FINAM("finam", "Финам");

    companion object {
        fun fromKey(key: String): BrokerName? =
            entries.firstOrNull { it.key == key }
    }
}