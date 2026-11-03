package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AccountStatus
import com.example.domain.model.UserRole
import com.example.ui.components.AppTopBar
import com.example.ui.components.ConfirmDialog
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserManagementScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Admin: User Management",
                subtitle = "${uiState.allUsers.size} accounts registered",
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.allUsers, key = { it.id }) { u ->
                val isSuspended = u.status == AccountStatus.SUSPENDED.name
                val isAdmin = u.role == UserRole.ADMIN.name

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(u.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text("${u.businessName} • ${u.role}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSuspended) Color(0xFFFEE2E2) else Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    text = u.status,
                                    color = if (isSuspended) Color(0xFFB91C1C) else Color(0xFF15803D),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Email: ${u.email} | Phone: ${u.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        if (!u.gstin.isNullOrBlank()) {
                            Text("GSTIN: ${u.gstin}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }

                        if (!isAdmin && u.id != uiState.currentUser?.id) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val nextStatus = if (isSuspended) AccountStatus.ACTIVE else AccountStatus.SUSPENDED
                                    viewModel.updateUserStatus(u.id, nextStatus)
                                },
                                colors = if (isSuspended) ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
                                else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("toggle_user_status_${u.id}")
                            ) {
                                Text(if (isSuspended) "Reactivate Account" else "Suspend Account")
                            }
                        }
                    }
                }
            }
        }
    }
}
