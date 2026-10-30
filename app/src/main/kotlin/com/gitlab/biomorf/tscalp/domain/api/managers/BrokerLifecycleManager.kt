package com.gitlab.biomorf.tscalp.domain.api.managers

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Доменный сервис восстановления lifecycle брокеров при старте приложения.
 *
 * Вынесен из `TScalpApplication` (composition root) — clean-audit Н50:
 * логика выбора счёта (savedAccountId → accounts → sandbox-fallback)
 * принадлежит domain, а не composition root.
 *
 * `refreshInitializationState()` вызывается внутри после успешной
 * инициализации — обновляет `BrokerRegistry.anyInitialized`.
 *
 * `TScalpApplication` остаётся composition root: `restoreBrokerConnection`
 * проверяет наличие токена и делегирует сюда.
 */
@Singleton
class BrokerLifecycleManager
    @Inject
    constructor(
        private val brokerRegistry: BrokerRegistry,
        private val accountPreferencesRepository: AccountPreferencesRepository,
        private val brokerCredentialsRepository: BrokerCredentialsRepository,
        private val positionFlowManager: PositionFlowManager,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "BrokerLifecycleManager"
        }

        /**
         * Инициализирует TInvest через lifecycle и запускает стрим позиций.
         *
         * Счёт выбирается через [resolveTInvestAccountId]: при успешном
         * `getAccounts` используется сохранённый или первый доступный счёт,
         * при падении — сохранённый (если есть), иначе в песочнице
         * открывается новый.
         *
         * `positionFlowManager.start()` вызывается независимо от того,
         * удалось ли получить список счетов — это позволяет стримам
         * работать при временном сбое `getAccounts`.
         */
        suspend fun restoreTInvest(broker: BrokerApi) {
            val creds = brokerCredentialsRepository.loadBrokerCredentials(BrokerName.TINVEST)
            if (creds == null) {
                logger.w(TAG, "TInvest: нет сохранённых креденшелов")
                return
            }
            val (token, sandbox) = creds

            val initialized =
                broker.lifecycleTInvest?.initialize(BrokerCredentials.TInvest(token, sandbox)) ?: false
            if (!initialized) {
                logger.e(TAG, "TInvest: не удалось инициализировать API")
                return
            }
            brokerRegistry.refreshInitializationState()

            val savedAccountId = accountPreferencesRepository.loadDefaultAccountId(BrokerName.TINVEST)
            val accountId = resolveTInvestAccountId(broker, sandbox, savedAccountId)

            if (accountId == null) {
                logger.w(TAG, "TInvest: не удалось выбрать/создать счёт")
                return
            }

            if (accountId != savedAccountId) {
                accountPreferencesRepository.saveDefaultAccountId(BrokerName.TINVEST, accountId)
            }
            positionFlowManager.start(accountId)
        }

        /**
         * Выбирает accountId для T-Invest с fallback на сохранённый счёт.
         *
         * Порядок:
         * 1. `getAccounts` успешно:
         *    - savedAccountId есть в списке → он;
         *    - иначе список не пуст → первый;
         *    - иначе sandbox → новый счёт в песочнице;
         *    - иначе null.
         * 2. `getAccounts` упало:
         *    - savedAccountId есть → он (главный fallback);
         *    - иначе sandbox → новый счёт в песочнице;
         *    - иначе null.
         */
        private suspend fun resolveTInvestAccountId(
            broker: BrokerApi,
            sandbox: Boolean,
            savedAccountId: String?,
        ): String? {
            val accounts =
                try {
                    broker.accounts?.getAccounts(sandbox)
                } catch (e: Exception) {
                    logger.w(TAG, "TInvest: getAccounts failed, fallback to savedAccountId", e)
                    null
                }

            return when {
                accounts == null -> savedAccountId ?: tryOpenSandboxAccount(broker, sandbox)
                savedAccountId != null && accounts.any { it.id == savedAccountId } -> savedAccountId
                accounts.isNotEmpty() -> accounts.first().id
                sandbox -> tryOpenSandboxAccount(broker, sandbox)
                else -> null
            }
        }

        /**
         * Открывает новый счёт в песочнице. Null вне sandbox или при ошибке.
         */
        private suspend fun tryOpenSandboxAccount(
            broker: BrokerApi,
            sandbox: Boolean,
        ): String? {
            if (!sandbox) return null
            return try {
                broker.sandbox?.openSandboxAccount()
            } catch (e: Exception) {
                logger.w(TAG, "TInvest: openSandboxAccount failed", e)
                null
            }
        }

        /**
         * Инициализирует BCS через lifecycle по сохранённым refresh-токену и режиму записи.
         */
        suspend fun restoreBcs(broker: BrokerApi) {
            val creds = brokerCredentialsRepository.loadBrokerCredentials(BrokerName.BCS) ?: return
            val (refreshToken, isWriteMode) = creds
            val initialized =
                broker.lifecycleBcs?.initialize(BrokerCredentials.Bcs(refreshToken, isWriteMode)) ?: false
            if (!initialized) {
                logger.e(TAG, "Не удалось инициализировать BCS")
                return
            }
            brokerRegistry.refreshInitializationState()
        }
    }
