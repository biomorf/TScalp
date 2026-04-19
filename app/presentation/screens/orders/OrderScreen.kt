package com.example.tinvest.presentation.screens.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun OrderScreen(
    viewModel: OrderViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Поле ввода FIGI
        OutlinedTextField(
            value = uiState.figi,
            onValueChange = viewModel::onFigiChanged,
            label = { Text("FIGI инструмента") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Поле ввода количества лотов
        OutlinedTextField(
            value = uiState.quantity,
            onValueChange = viewModel::onQuantityChanged,
            label = { Text("Количество лотов") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Выбор счёта
        AccountDropdown(
            accounts = uiState.accounts,
            selectedAccountId = uiState.selectedAccountId,
            onAccountSelected = viewModel::onAccountSelected,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Кнопки Купить / Продать
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = viewModel::onBuyClick,
                modifier = Modifier.weight(1f),
                enabled = uiState.isFormValid && !uiState.isLoading
            ) {
                Text("Купить")
            }
            
            Button(
                onClick = viewModel::onSellClick,
                modifier = Modifier.weight(1f),
                enabled = uiState.isFormValid && !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Продать")
            }
        }
        
        // Индикатор загрузки
        if (uiState.isLoading) {
            CircularProgressIndicator()
        }
        
        // Статус выполнения
        uiState.statusMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isError) 
                        MaterialTheme.colorScheme.errorContainer 
                    else 
                        MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun AccountDropdown(
    accounts: List<AccountUi>,
    selectedAccountId: String?,
    onAccountSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        TextField(
            value = accounts.find { it.id == selectedAccountId }?.name ?: "Выберите счёт",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = {
                        onAccountSelected(account.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
