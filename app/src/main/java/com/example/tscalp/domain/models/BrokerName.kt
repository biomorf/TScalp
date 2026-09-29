package com.example.tscalp.domain.models

/**
 * Уникальные имена брокеров, поддерживаемых приложением.
 *
 * @property key строковый идентификатор, под которым брокер регистрируется
 *                 в BrokerManager и хранится в SharedPreferences
 *                 (совместим с legacy-ключами "TInvest_token", "bcs_token", "finam_token").
 */
enum class BrokerName(val key: String) {
    TINVEST("TInvest"),
    BCS("bcs"),
    FINAM("finam");

    companion object {
        fun fromKey(key: String): BrokerName? =
            entries.firstOrNull { it.key == key }
    }
}