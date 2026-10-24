package com.gitlab.biomorf.tscalp.domain.api

/**
 * Опциональный контракт: lifecycle брокера (login / logout).
 *
 * Реализуется брокером, если он поддерживает инициализацию
 * по сохранённым credentials. Доступ через
 * `BrokerApi.lifecycle` (`null` = не поддерживает).
 *
 * **Типизация через [BrokerCredentials].** Каждая реализация
 * принимает свой тип в [initialize] через `when`, неверный
 * тип — `IllegalArgumentException`. Альтернатива — три
 * отдельных метода с no-op default-реализациями — отклонена
 * по 2c (интерфейс знает про всех брокеров, data протекает
 * в domain). См. `BrokerCredentials` KDoc.
 *
 * См. `docs/ROADMAP.md` → `### Broker lifecycle API` (#2 в
 * umbrella `### Рефакторинг ролей BrokerApi (nullable-val,
 * Scenario2)`).
 */
interface BrokerLifecycleApi {
    /**
     * Инициализация брокера по сохранённым credentials.
     *
     * @return `true` — успех (брокер готов к работе,
     *   `isInitialized == true`), `false` — ошибка (неверный
     *   токен, сеть, отказ сервера). Сообщение об ошибке —
     *   через [AppLogger][com.gitlab.biomorf.tscalp.util.AppLogger]
     *   и `AppResult.Failure` на уровне ViewModel.
     * @throws IllegalArgumentException если [credentials]
     *   не того типа, который поддерживает эта реализация
     *   (например, [BrokerCredentials.Bcs] передан в
     *   T-Invest-реализацию).
     */
    suspend fun initialize(credentials: BrokerCredentials): Boolean

    /**
     * Деинициализация: закрыть каналы, обнулить состояние.
     * После вызова `isInitialized == false`.
     *
     * Не `suspend` — закрытие каналов синхронное
     * (`ManagedChannel.shutdownNow`, `wsScope.cancel`).
     */
    fun deinitialize()
}
