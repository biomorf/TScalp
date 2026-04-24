import com.example.tscalp.domain.models.PortfolioPosition

/**
 * UI-состояние экрана портфеля.
 */
data class PortfolioUiState(
    val positions: List<PortfolioPosition> = emptyList(),
    val totalValue: Double = 0.0,
    val balance: Double = 0.0,          // 👈 новое поле
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val isApiInitialized: Boolean = false,
    val sandboxMode: Boolean = false
)