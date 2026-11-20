package com.example.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.config.AuthExecutionMode
import com.example.domain.model.UserRole
import com.example.domain.model.AuthUser
import com.example.domain.model.AccountStatus
import com.example.ui.components.AppTopBar
import com.example.ui.components.BackendConfigDialog
import com.example.ui.components.BackendStatusBar

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.PharmaOSTheme

@Composable
fun ProfileSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSupplierSettings: () -> Unit,
    onNavigateToCsv: () -> Unit,
    onNavigateToAdminUsers: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileSettingsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToSupplierSettings = onNavigateToSupplierSettings,
        onNavigateToCsv = onNavigateToCsv,
        onNavigateToAdminUsers = onNavigateToAdminUsers,
        onToggleDarkTheme = { viewModel.setDarkTheme(it) },
        onSelectAuthMode = { viewModel.setAuthExecutionMode(it) },
        onConfigureBackendUrl = { viewModel.configureBackendUrl(it) },
        onSyncFromBackend = { viewModel.syncFromBackend() },
        onClearMessages = { viewModel.clearMessages() },
        onLogout = { viewModel.logout(onLoggedOut) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsContent(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onNavigateToSupplierSettings: () -> Unit,
    onNavigateToCsv: () -> Unit,
    onNavigateToAdminUsers: () -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSelectAuthMode: (AuthExecutionMode) -> Unit = {},
    onConfigureBackendUrl: (String) -> Unit = {},
    onSyncFromBackend: () -> Unit = {},
    onClearMessages: () -> Unit = {},
    onLogout: () -> Unit
) {
    val user = uiState.currentUser
    val snackbarHostState = remember { SnackbarHostState() }
    var showBackendDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        val msg = uiState.errorMessage ?: uiState.successMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onClearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Settings & Profile",
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(user?.businessName ?: "My Business", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("${user?.fullName} • ${user?.role?.displayName ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text("Email: ${user?.email ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                    Text("Phone: ${user?.phone ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                    if (!user?.gstin.isNullOrBlank()) {
                        Text("GSTIN: ${user?.gstin}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Address: ${user?.address ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Theme Preferences
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Dark Theme", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Enable low-light dark interface", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Switch(
                        checked = uiState.isDarkTheme,
                        onCheckedChange = { onToggleDarkTheme(it) },
                        modifier = Modifier.testTag("dark_theme_toggle")
                    )
                }
            }

            // Backend API & Sync Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Backend API & Sync",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    BackendStatusBar(
                        authExecutionMode = uiState.authExecutionMode,
                        backendBaseUrl = uiState.backendBaseUrl,
                        isBackendConfigured = uiState.isBackendConfigured,
                        onConfigureClick = { showBackendDialog = true }
                    )
                    OutlinedButton(
                        onClick = onSyncFromBackend,
                        enabled = !uiState.isSyncing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sync_backend_button")
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Catalogue, Orders & Alerts")
                        }
                    }
                }
            }

            // Navigation Links
            Text("Business Operations", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

            if (user?.role?.isSupplier == true || user?.role == UserRole.ADMIN) {
                SettingsActionCard(
                    title = "Supplier Freight & Logistics Settings",
                    subtitle = "Configure delivery fees, free delivery limits & lead times",
                    icon = Icons.Default.LocalShipping,
                    onClick = onNavigateToSupplierSettings
                )
            }

            SettingsActionCard(
                title = "CSV Import / Export & ERP Sync",
                subtitle = "Batch import catalogue and export orders to CSV/JSON",
                icon = Icons.Default.FileDownload,
                onClick = onNavigateToCsv
            )

            if (user?.role == UserRole.ADMIN) {
                SettingsActionCard(
                    title = "Administrator: User Management",
                    subtitle = "Manage registered trading accounts and access status",
                    icon = Icons.Default.AdminPanelSettings,
                    onClick = onNavigateToAdminUsers
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Logout & Account Switch Button
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("logout_button")
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out / Switch Account", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showBackendDialog) {
        BackendConfigDialog(
            currentMode = uiState.authExecutionMode,
            currentBaseUrl = uiState.backendBaseUrl,
            onDismiss = { showBackendDialog = false },
            onSelectMode = onSelectAuthMode,
            onSaveBaseUrl = onConfigureBackendUrl
        )
    }
}

@Composable
fun SettingsActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Preview(name = "Settings - Light Mode", showBackground = true)
@Preview(name = "Settings - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ProfileSettingsScreenPreview() {
    PharmaOSTheme {
        ProfileSettingsContent(
            uiState = SettingsUiState(
                currentUser = AuthUser(
                    id = 1L,
                    fullName = "Rohan Sharma",
                    businessName = "Apex Pharma Distributors",
                    email = "rohan.sharma@apexpharma.com",
                    phone = "+919876543210",
                    gstin = "27ABCDE1234F1Z5",
                    address = "Shop 12, Medical Market, Mumbai, MH",
                    role = UserRole.STOCKIST,
                    status = AccountStatus.ACTIVE
                ),
                isDarkTheme = false
            ),
            onNavigateBack = {},
            onNavigateToSupplierSettings = {},
            onNavigateToCsv = {},
            onNavigateToAdminUsers = {},
            onToggleDarkTheme = {},
            onLogout = {}
        )
    }
}

