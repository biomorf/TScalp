package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.presentation.util.formatDomesticCurrency

@Composable
internal fun BoxScope.OrdersScreenShadows(
    showTopShadow: Boolean,
    showBottomShadow: Boolean,
) {
    if (showTopShadow) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.08f), Color.Transparent),
                            ),
                    ).align(Alignment.TopCenter),
        )
    }
    if (showBottomShadow) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.08f)),
                            ),
                    ).align(Alignment.BottomCenter),
        )
    }
}

@Composable
internal fun OrdersBottomButtons(
    freeBalance: Double?,
    isFormValid: Boolean,
    isLoading: Boolean,
    onBuyClick: () -> Unit,
    onSellClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (freeBalance != null) {
            Text(
                text = "Свободно: ${formatDomesticCurrency(freeBalance)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = onBuyClick,
                modifier = Modifier.weight(1f),
                enabled = isFormValid && !isLoading,
            ) {
                Text("КУПИТЬ")
            }
            Button(
                onClick = onSellClick,
                modifier = Modifier.weight(1f),
                enabled = isFormValid && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("ПРОДАТЬ")
            }
        }
    }
}
