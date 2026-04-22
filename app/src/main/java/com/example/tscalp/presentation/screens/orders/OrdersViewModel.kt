package com.example.tscalp.presentation.screens.orders

import com.example.tscalp.data.api.TinkoffInvestService
import com.example.tscalp.data.repository.InvestRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job                         // <-- ДОБАВЛЕН ИМПОРТ
import kotlinx.coroutines.delay                      // <-- ДОБАВЛЕН ИМПОРТ
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tinkoff.piapi.contract.v1.Instrument       // <-- ДОБАВЛЕН ИМПОРТ
import ru.tinkoff.piapi.contract.v1.OrderDirection

/**
 * ViewModel для экрана выставления заявок.
 *
 * Управляет состоянием формы заявки, поиском инструментов, загрузкой списка счетов
 * и выполнением рыночных заявок через T-Invest API.
 *
 * @param apiService сервис для работы с API Т-Инвестиций
 */
class OrdersViewModel(
    private val apiService: TinkoffInvestService
) : ViewModel() {

    companion object {
        private const val TAG = "OrdersViewModel"  // Тег для логирования
    }

    // Репозиторий для доступа к данным (обёртка над apiService)
    private val repository = InvestRepository(apiService)

    // Приватное мутабельное состояние UI, доступное только внутри ViewModel
    private val _uiState = MutableStateFlow(OrdersUiState())
    // Публичное неизменяемое состояние для подписки из UI
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    // Job для отложенного поиска (debounce), чтобы не дёргать API на каждый ввод символа
    private var searchJob: Job? = null

    init {
        // При создании ViewModel проверяем, инициализирован ли уже API
        checkApiInitialization()
    }

    /**
     * Проверяет, был ли API инициализирован ранее (например, после восстановления токена).
     * Если да – обновляет состояние и загружает счета.
     */
    fun checkApiInitialization() {
        _uiState.update {
            it.copy(isApiInitialized = apiService.isInitialized)
        }
        if (apiService.isInitialized) {
            loadAccounts()
        }
    }

    /**
     * Инициализирует API переданным токеном и режимом (боевой/песочница).
     * После успешной инициализации загружает список счетов.
     *
     * @param token токен доступа к T-Invest API
     * @param sandboxMode true для песочницы, false для боевого режима
     */
    fun initializeApi(token: String, sandboxMode: Boolean = true) {
        try {
            apiService.initialize(token, sandboxMode)
            _uiState.update {
                it.copy(
                    isApiInitialized = true,
                    statusMessage = "API подключен (режим: ${if (sandboxMode) "песочница" else "боевой"})",
                    isError = false
                )
            }
            loadAccounts()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    statusMessage = "Ошибка подключения: ${e.message}",
                    isError = true
                )
            }
        }
    }

    /**
     * Загружает список торговых счетов пользователя и обновляет UI-состояние.
     * Если счета получены, первый автоматически выбирается.
     */
    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val accounts = repository.getAccounts()
                val defaultAccount = accounts.firstOrNull()
                _uiState.update {
                    it.copy(
                        accounts = accounts,
                        selectedAccountId = defaultAccount?.id,
                        isLoading = false,
                        statusMessage = if (accounts.isEmpty()) {
                            "Нет доступных счетов"
                        } else {
                            "Загружено ${accounts.size} счёт(ов)"
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Ошибка загрузки счетов: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    /**
     * Обрабатывает изменение строки поиска инструмента.
     * Запускает отложенный поиск (debounce 500 мс), если длина запроса >= 2 символов.
     *
     * @param query поисковый запрос (тикер, название или FIGI)
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                selectedInstrument = null,
                figi = ""
            )
        }

        // Отменяем предыдущий поиск, чтобы не выполнять параллельные запросы
        searchJob?.cancel()

        if (query.length >= 2) {
            searchJob = viewModelScope.launch {
                // Debounce: ждём 500 мс после последнего ввода перед отправкой запроса
                delay(500)

                _uiState.update { it.copy(isSearching = true) }
                try {
                    val results = repository.searchInstruments(query)
                    _uiState.update {
                        it.copy(
                            searchResults = results,
                            isSearching = false
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            searchResults = emptyList(),
                            isSearching = false,
                            statusMessage = "Ошибка поиска: ${e.message}",
                            isError = true
                        )
                    }
                }
            }
        } else {
            // Если запрос короткий, очищаем результаты поиска
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearching = false
                )
            }
        }
    }

    /**
     * Вызывается при выборе инструмента из выпадающего списка.
     * Заполняет поле FIGI и очищает результаты поиска.
     *
     * @param instrument выбранный инструмент
     */
    fun onInstrumentSelected(instrument: Instrument) {
        _uiState.update {
            it.copy(
                selectedInstrument = instrument,
                figi = instrument.figi,
                searchQuery = "${instrument.ticker} - ${instrument.name}",
                searchResults = emptyList()
            )
        }
    }

    /**
     * Очищает поле поиска и сбрасывает выбранный инструмент.
     */
    fun clearSearch() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                selectedInstrument = null,
                figi = ""
            )
        }
    }

    /**
     * Обрабатывает изменение поля FIGI.
     * Приводит введённое значение к верхнему регистру.
     *
     * @param figi введённый FIGI
     */
    fun onFigiChanged(figi: String) {
        _uiState.update { it.copy(figi = figi.uppercase()) }
    }

    /**
     * Обрабатывает изменение поля "Количество лотов".
     * Оставляет только цифры, фильтруя нечисловые символы.
     *
     * @param quantity введённое количество
     */
    fun onQuantityChanged(quantity: String) {
        val filtered = quantity.filter { it.isDigit() }
        _uiState.update { it.copy(quantity = filtered) }
    }

    /**
     * Обрабатывает выбор торгового счёта из выпадающего списка.
     *
     * @param accountId идентификатор выбранного счёта
     */
    fun onAccountSelected(accountId: String) {
        _uiState.update { it.copy(selectedAccountId = accountId) }
    }

    /**
     * Обработчик нажатия кнопки "Купить".
     * Отправляет рыночную заявку на покупку.
     */
    fun onBuyClick() {
        postOrder(OrderDirection.ORDER_DIRECTION_BUY)
    }

    /**
     * Обработчик нажатия кнопки "Продать".
     * Отправляет рыночную заявку на продажу.
     */
    fun onSellClick() {
        postOrder(OrderDirection.ORDER_DIRECTION_SELL)
    }

    /**
     * Отправляет рыночную заявку через API и обновляет состояние.
     *
     * @param direction направление заявки (BUY или SELL)
     */
    private fun postOrder(direction: OrderDirection) {
        val state = _uiState.value
        val figi = state.figi
        val quantity = state.quantityAsLong ?: return
        val accountId = state.selectedAccountId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                val result = repository.postMarketOrder(
                    figi = figi,
                    quantity = quantity,
                    direction = direction,
                    accountId = accountId
                )

                val directionText = when (direction) {
                    OrderDirection.ORDER_DIRECTION_BUY -> "покупка"
                    OrderDirection.ORDER_DIRECTION_SELL -> "продажа"
                    else -> "операция"
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "✅ Заявка на $directionText выполнена!\n" +
                                "ID: ${result.orderId}\n" +
                                "Исполнено: ${result.executedLots}/${result.totalLots} лотов",
                        isError = false,
                        // Очищаем поля формы после успешной заявки
                        figi = "",
                        quantity = "",
                        searchQuery = "",
                        selectedInstrument = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "❌ Ошибка: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    /**
     * Очищает статусное сообщение (например, после того как пользователь его увидел).
     */
    fun clearStatus() {
        _uiState.update { it.clearStatus() }
    }

    /**
     * Повторяет попытку загрузки счетов (например, если API не был инициализирован при старте).
     */
    fun retryLoadAccounts() {
        if (apiService.isInitialized) {
            loadAccounts()
        }
    }
}