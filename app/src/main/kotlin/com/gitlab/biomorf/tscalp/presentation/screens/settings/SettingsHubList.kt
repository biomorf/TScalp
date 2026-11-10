package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme

@Composable
internal fun SettingsHubList(onSelect: (String) -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        ListItem(
            headlineContent = { Text("Подключение") },
            supportingContent = { Text("Выбор брокера, токены, режим песочницы") },
            leadingContent = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
            modifier = Modifier.clickable { onSelect("broker") },
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Торговля") },
            supportingContent = { Text("Подтверждение сделок и другие параметры") },
            leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
            modifier = Modifier.clickable { onSelect("trade") },
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Информация") },
            supportingContent = { Text("Версия приложения, о разработчике") },
            leadingContent = {
                Icon(Icons.Default.Info, contentDescription = null)
            },
            modifier = Modifier.clickable { onSelect("info") },
        )
    }
}

@Preview(name = "SettingsHub", showBackground = true, widthDp = 360)
@Composable
private fun SettingsHubListPreview() {
    TScalpTheme {
        SettingsHubList(onSelect = {})
    }
}
