package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun TInvestTokenHelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📋 Как получить токен", style = MaterialTheme.typography.titleMedium)
            Text(
                """
                1. Управляйте токенами с компьютера — с телефона может перекинуть в приложение, где этого раздела нет.
                2. Откройте https://www.tbank.ru/invest/settings/ и войдите в личный кабинет Т-Инвестиций.
                3. Убедитесь, что функция «Подтверждение сделок кодом» отключена. Иначе API не сможет выставлять заявки. Крупные сделки (>30 млн ₽) через API недоступны в любом случае.
                4. Найдите подраздел «Токены T-Bank Invest API» и нажмите кнопку "Создать токен". Токен — это просто очень длинный специальный пароль для доступа к брокеру.
                5. Выберите тип, нажав кнопку в соответствующем разделе на странице: песочница (для тестов, безопасно) или биржа (реальная торговля). Для биржи настройте права: только чтение / торговля, и доступ: все счета / конкретный счёт. Рекомендуем сначала потренироваться в песочнице.
                6. Пройдите доп. авторизацию, если система попросит.
                7. Скопируйте токен сразу — он показывается только один раз. Сохраните в надёжном месте (менеджер паролей).
                8. Токен действует 3 месяца с последнего использования. Если перестал работать — выпустите новый.
                9. Отозвать старый токен можно на https://id.tbank.ru/account в подразделе "Приложения, у которых есть доступ к данным для входа".
                10. Никому не передавайте токен. Если он попал в логи или в публичные источники — немедленно отзовите и создайте новый.
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun BcsTokenHelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📋 Как получить токен", style = MaterialTheme.typography.titleMedium)
            Text(
                """
                1. Зайдите в личный кабинет БКС Мир Инвестиций
                2. Перейдите в раздел «Настройки» → «API»
                3. Нажмите «Создать новый токен»
                4. Выберите права: чтение портфеля и совершение сделок
                5. Скопируйте полученный refresh‑токен
                6. Вставьте его в поле выше

                ⚠️ Рекомендации по безопасности:
                • Сначала тестируйте в режиме песочницы
                • Не передавайте токен третьим лицам
                • Токен хранится в зашифрованном виде
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun FinamTokenHelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Как получить токен", style = MaterialTheme.typography.titleMedium)
            Text(
                """
                1. Зайдите в личный кабинет Finam
                2. Перейдите в раздел «Настройки» → «API»
                3. Создайте новый токен с правами на торговлю и чтение
                4. Скопируйте секретный токен
                5. Вставьте его в поле выше

                ⚠️ Рекомендации:
                • Храните токен в безопасном месте
                • Не передавайте третьим лицам
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
