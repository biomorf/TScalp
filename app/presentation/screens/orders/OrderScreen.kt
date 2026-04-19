// В начало функции OrdersScreen добавим:
@Composable
fun OrdersScreen(
    ordersViewModel: OrdersViewModel = viewModel()
) {
    val uiState by ordersViewModel.uiState.collectAsState()
    
    if (!uiState.isApiInitialized) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("API не подключен")
                Text(
                    "Перейдите в настройки и введите токен",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }
    
    // ... остальной код экрана
}