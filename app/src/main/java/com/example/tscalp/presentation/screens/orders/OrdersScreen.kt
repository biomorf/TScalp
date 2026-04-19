package com.example.tscalp.presentation.screens.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
//fun OrdersScreen() {
fun OrdersScreen(
    viewModel: OrdersViewModel = viewModel(
        factory = OrdersViewModelFactory(LocalContext.current)
    )
) {
    var figi by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Выставление заявки",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = figi,
            onValueChange = { figi = it },
            label = { Text("FIGI инструмента") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it },
            label = { Text("Количество лотов") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { /* Пока ничего */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Выставить заявку")
        }

        Text(
            text = "API не подключен. Перейдите в Настройки",
            color = MaterialTheme.colorScheme.error
        )
    }
}