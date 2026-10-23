package com.gitlab.biomorf.tscalp.domain.models

/**
 * Состояние источника данных для менеджера (*FlowManager).
 *
 * Отражает, откуда текущий поток получает данные — из быстрого
 * источника (стрим) или медленного (polling), или данные вообще
 * не поступают. Сигнализирует менеджер (PositionFlowManager,
 * PriceFlowManager, OrderFlowManager), так как он агрегирует
 * источники и знает их состояние.
 *
 * Переходы и пороги — в [com.gitlab.biomorf.tscalp.domain.api.StreamStateTracker].
 *
 * Значения:
 * - [INITIALIZING] — источники запущены, но данных ещё не было.
 * - [LIVE] — данные идут из стрима (gRPC/WS).
 * - [POLLING] — стрим молчит, но polling доставляет данные
 *   (резервный режим).
 * - [STALE] — данные не приходят ни из одного источника
 *   дольше порога (по умолчанию 30 сек).
 * - [STOPPED] — менеджер остановлен (stop() или пустой union).
 */
enum class StreamState {
    INITIALIZING,
    LIVE,
    POLLING,
    STALE,
    STOPPED,
}
