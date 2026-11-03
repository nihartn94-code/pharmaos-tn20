package com.example.ui.screens.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.NotificationEntity
import com.example.domain.model.NotificationType
import com.example.domain.repository.PharmaRepository
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import com.example.util.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    repository: PharmaRepository,
    onNavigateBack: () -> Unit
) {
    val notifications by repository.getApiNotificationsFlow().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Trade & Alert Center",
                subtitle = "${notifications.count { !it.isRead }} unread notifications",
                onBackClick = onNavigateBack,
                actions = {
                    if (notifications.any { !it.isRead }) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch { repository.patchApiNotificationsReadAll() }
                            },
                            modifier = Modifier.testTag("mark_all_read_button")
                        ) {
                            Text("Mark All Read")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (notifications.isEmpty()) {
            EmptyState(
                title = "All Caught Up!",
                message = "You have no trade notifications or alert directives at this time.",
                icon = Icons.Default.Notifications,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { item ->
                    val type = try { NotificationType.valueOf(item.type) } catch (_: Exception) { NotificationType.ORDER_STATUS_CHANGED }
                    val (icon, tint) = when (type) {
                        NotificationType.ORDER_STATUS_CHANGED, NotificationType.NEW_ORDER_RECEIVED -> Icons.Default.LocalShipping to MaterialTheme.colorScheme.primary
                        NotificationType.EXPIRY_ALERT -> Icons.Default.HourglassBottom to Color(0xFFEE9B00)
                        NotificationType.RECALL_ALERT, NotificationType.SHORTAGE_RISK -> Icons.Default.Warning to MaterialTheme.colorScheme.error
                        NotificationType.RETURN_UPDATE -> Icons.Default.AssignmentTurnedIn to Color(0xFF2A9D8F)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!item.isRead) {
                                    coroutineScope.launch { repository.patchApiNotificationRead(item.id) }
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = tint.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (item.isRead) FontWeight.SemiBold else FontWeight.Bold
                                    )
                                    if (!item.isRead) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(8.dp)
                                        ) {}
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = Formatters.formatDateTime(item.createdAtEpochMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
