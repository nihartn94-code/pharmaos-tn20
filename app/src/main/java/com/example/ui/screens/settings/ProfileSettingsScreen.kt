package com.example.ui.screens.settings

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.config.AuthExecutionMode
import com.example.domain.model.AccountStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.ContactVerificationChannel
import com.example.domain.model.LegalDocument
import com.example.domain.model.LegalDocumentsCatalog
import com.example.domain.model.UserAddress
import com.example.domain.model.UserRole
import com.example.ui.components.AppTopBar
import com.example.ui.components.BackendConfigDialog
import com.example.ui.components.BackendStatusBar
import com.example.ui.theme.PharmaOSTheme
import kotlinx.coroutines.launch

@Composable
fun ProfileSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSupplierSettings: () -> Unit,
    onNavigateToCsv: () -> Unit,
    onNavigateToAdminUsers: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenExternalLegalUrl: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarFallback = remember { mutableStateOf<String?>(null) }

    val openUrlHandler: (String) -> Unit = onOpenExternalLegalUrl ?: { url ->
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            scope.launch {
                snackbarFallback.value = "No browser app found to open $url"
            }
        }
    }

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
        onRefreshProfile = { viewModel.refreshProfileAndAddresses() },
        onUpdateProfile = { fullName, businessName, email, phone, gstin, bio, onDone ->
            viewModel.updateProfile(
                fullName = fullName,
                businessName = businessName,
                email = email,
                phone = phone,
                gstin = gstin,
                profileBio = bio,
                onSuccess = onDone
            )
        },
        onRequestContactVerification = { viewModel.requestContactVerification(it) },
        onConfirmContactVerification = { code -> viewModel.confirmContactVerification(code) },
        onDismissContactVerification = { viewModel.dismissContactVerification() },
        onSaveAddress = { address, onDone -> viewModel.saveAddress(address, onSuccess = onDone) },
        onDeleteAddress = { viewModel.deleteAddress(it) },
        onSetDefaultAddress = { viewModel.setDefaultAddress(it) },
        onClearValidationErrors = { viewModel.clearValidationErrors() },
        onOpenLegalUrl = openUrlHandler,
        externalNoticeMessage = snackbarFallback.value,
        onConsumeExternalNotice = { snackbarFallback.value = null },
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
    onRefreshProfile: () -> Unit = {},
    onUpdateProfile: (
        fullName: String,
        businessName: String,
        email: String,
        phone: String,
        gstin: String?,
        profileBio: String?,
        onDone: () -> Unit
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onRequestContactVerification: (ContactVerificationChannel) -> Unit = {},
    onConfirmContactVerification: (String) -> Unit = {},
    onDismissContactVerification: () -> Unit = {},
    onSaveAddress: (UserAddress, () -> Unit) -> Unit = { _, _ -> },
    onDeleteAddress: (Long) -> Unit = {},
    onSetDefaultAddress: (Long) -> Unit = {},
    onClearValidationErrors: () -> Unit = {},
    onOpenLegalUrl: (String) -> Unit = {},
    externalNoticeMessage: String? = null,
    onConsumeExternalNotice: () -> Unit = {},
    onClearMessages: () -> Unit = {},
    onLogout: () -> Unit
) {
    val user = uiState.currentUser
    val snackbarHostState = remember { SnackbarHostState() }
    var showBackendDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editingAddress by remember { mutableStateOf<UserAddress?>(null) }
    var showAddAddressDialog by remember { mutableStateOf(false) }
    var addressPendingDelete by remember { mutableStateOf<UserAddress?>(null) }
    var viewingLegalDocument by remember { mutableStateOf<LegalDocument?>(null) }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        val msg = uiState.errorMessage ?: uiState.successMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onClearMessages()
        }
    }

    LaunchedEffect(externalNoticeMessage) {
        if (externalNoticeMessage != null) {
            snackbarHostState.showSnackbar(externalNoticeMessage)
            onConsumeExternalNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Profile & Settings",
                subtitle = user?.role?.displayName,
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = onRefreshProfile,
                        enabled = !uiState.isRefreshingProfile,
                        modifier = Modifier.testTag("refresh_profile_button")
                    ) {
                        if (uiState.isRefreshingProfile) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Profile & Addresses"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .testTag("profile_settings_scroll"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. USER PROFILE INFORMATION SECTION
                SectionHeader(title = "User Profile")
                ProfileIdentityCard(
                    user = user,
                    defaultAddress = uiState.addresses.firstOrNull { it.isDefault } ?: uiState.addresses.firstOrNull(),
                    onEditProfileClick = {
                        onClearValidationErrors()
                        showEditProfileDialog = true
                    }
                )

                // 2. REGISTERED EMAIL & PHONE VERIFICATION SECTION
                SectionHeader(title = "Registered Email & Phone")
                ContactVerificationCard(
                    user = user,
                    isVerifying = uiState.isVerifyingContact,
                    onEditContactClick = {
                        onClearValidationErrors()
                        showEditProfileDialog = true
                    },
                    onVerifyEmailClick = { onRequestContactVerification(ContactVerificationChannel.EMAIL) },
                    onVerifyPhoneClick = { onRequestContactVerification(ContactVerificationChannel.PHONE) }
                )

                // 3. ADDRESS MANAGEMENT SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(title = "Address Management")
                    OutlinedButton(
                        onClick = {
                            onClearValidationErrors()
                            editingAddress = null
                            showAddAddressDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_address_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add new address",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Address", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                AddressManagementSection(
                    addresses = uiState.addresses,
                    isBusy = uiState.isSavingAddress,
                    onSetDefault = onSetDefaultAddress,
                    onEditAddress = { address ->
                        onClearValidationErrors()
                        editingAddress = address
                        showAddAddressDialog = true
                    },
                    onDeleteAddress = { addressPendingDelete = it }
                )

                // 4. PREFERENCES & BACKEND SYNC SECTION
                SectionHeader(title = "Preferences & Sync")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Dark Theme",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Enable low-light dark interface",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.isDarkTheme,
                            onCheckedChange = { onToggleDarkTheme(it) },
                            modifier = Modifier.testTag("dark_theme_toggle")
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                Text("Sync Profile, Catalogue & Orders")
                            }
                        }
                    }
                }

                // 5. BUSINESS OPERATIONS SECTION
                SectionHeader(title = "Business Operations")

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

                // 6. LEGAL DOCUMENTS SECTION
                SectionHeader(title = "Legal Documents")
                LegalDocumentsCard(
                    documents = uiState.legalDocuments,
                    onDocumentClick = { viewingLegalDocument = it }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 7. LOGOUT & ACCOUNT SWITCH
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

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showEditProfileDialog && user != null) {
        EditProfileDialog(
            user = user,
            isSaving = uiState.isSavingProfile,
            validationError = uiState.profileValidationError,
            onDismiss = {
                onClearValidationErrors()
                showEditProfileDialog = false
            },
            onSave = { fullName, businessName, email, phone, gstin, bio ->
                onUpdateProfile(fullName, businessName, email, phone, gstin, bio) {
                    showEditProfileDialog = false
                }
            }
        )
    }

    if (showAddAddressDialog) {
        AddressFormDialog(
            initialAddress = editingAddress,
            defaultContactName = user?.fullName.orEmpty(),
            defaultPhone = user?.phone.orEmpty(),
            isSaving = uiState.isSavingAddress,
            validationError = uiState.addressValidationError,
            onDismiss = {
                onClearValidationErrors()
                showAddAddressDialog = false
                editingAddress = null
            },
            onSave = { address ->
                onSaveAddress(address) {
                    showAddAddressDialog = false
                    editingAddress = null
                }
            }
        )
    }

    addressPendingDelete?.let { addr ->
        DeleteAddressConfirmDialog(
            address = addr,
            isDeleting = uiState.isSavingAddress,
            onDismiss = { addressPendingDelete = null },
            onConfirmDelete = {
                onDeleteAddress(addr.id)
                addressPendingDelete = null
            }
        )
    }

    uiState.pendingVerificationChannel?.let { channel ->
        ContactVerificationDialog(
            channel = channel,
            targetValue = uiState.pendingVerificationValue,
            statusHint = uiState.verificationStatusHint,
            isVerifying = uiState.isVerifyingContact,
            onDismiss = onDismissContactVerification,
            onConfirmCode = onConfirmContactVerification
        )
    }

    viewingLegalDocument?.let { doc ->
        LegalDocumentViewerDialog(
            document = doc,
            onDismiss = { viewingLegalDocument = null },
            onOpenExternalUrl = onOpenLegalUrl
        )
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ProfileIdentityCard(
    user: AuthUser?,
    defaultAddress: UserAddress?,
    onEditProfileClick: () -> Unit
) {
    val displayName = user?.fullName?.ifBlank { "Unnamed User" } ?: "Not Signed In"
    val initials = remember(displayName) {
        displayName.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "PH" }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_info_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("profile_full_name_text")
                    )
                    Text(
                        text = user?.businessName?.ifBlank { "Business / Pharmacy not set" } ?: "Business / Pharmacy",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("profile_business_name_text")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = user?.role?.displayName ?: "Account",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .testTag("profile_role_badge")
                            )
                        }
                        if (user != null) {
                            val isActive = user.status == AccountStatus.ACTIVE
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = user.status.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) Color(0xFF047857) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProfileDetailRow(
                    label = "Business / Pharmacy",
                    value = user?.businessName?.ifBlank { "Not specified" } ?: "Not specified"
                )
                if (!user?.gstin.isNullOrBlank()) {
                    ProfileDetailRow(label = "GSTIN", value = user?.gstin.orEmpty())
                }
                val primaryAddressText = defaultAddress?.formattedSingleLine()
                    ?: user?.address?.takeIf { it.isNotBlank() }
                    ?: "No address saved yet"
                ProfileDetailRow(label = "Primary Address", value = primaryAddressText)
                if (!user?.profileBio.isNullOrBlank()) {
                    ProfileDetailRow(
                        label = "Profile Info",
                        value = user?.profileBio.orEmpty(),
                        valueTestTag = "profile_bio_text"
                    )
                }
            }

            OutlinedButton(
                onClick = onEditProfileClick,
                enabled = user != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_profile_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit profile",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Profile Information", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProfileDetailRow(
    label: String,
    value: String,
    valueTestTag: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(0.62f)
                .let { if (valueTestTag != null) it.testTag(valueTestTag) else it }
        )
    }
}

@Composable
private fun ContactVerificationCard(
    user: AuthUser?,
    isVerifying: Boolean,
    onEditContactClick: () -> Unit,
    onVerifyEmailClick: () -> Unit,
    onVerifyPhoneClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_verification_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ContactChannelRow(
                icon = Icons.Default.Email,
                label = "Registered Email",
                value = user?.email?.ifBlank { "Not set" } ?: "Not set",
                isVerified = user?.emailVerified == true,
                isBusy = isVerifying,
                valueTestTag = "profile_email_text",
                statusTestTag = "email_verification_status",
                verifyButtonTestTag = "verify_email_button",
                onVerifyClick = onVerifyEmailClick
            )

            HorizontalDivider()

            ContactChannelRow(
                icon = Icons.Default.Phone,
                label = "Registered Phone",
                value = user?.phone?.ifBlank { "Not set" } ?: "Not set",
                isVerified = user?.phoneVerified == true,
                isBusy = isVerifying,
                valueTestTag = "profile_phone_text",
                statusTestTag = "phone_verification_status",
                verifyButtonTestTag = "verify_phone_button",
                onVerifyClick = onVerifyPhoneClick
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Verification is only marked complete after server confirmation.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onEditContactClick,
                    enabled = user != null
                ) {
                    Text("Edit")
                }
            }
        }
    }
}

@Composable
private fun ContactChannelRow(
    icon: ImageVector,
    label: String,
    value: String,
    isVerified: Boolean,
    isBusy: Boolean,
    valueTestTag: String,
    statusTestTag: String,
    verifyButtonTestTag: String,
    onVerifyClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isVerified) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                ) {
                    Text(
                        text = if (isVerified) "Verified" else "Unverified",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isVerified) Color(0xFF047857) else Color(0xFFB45309),
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag(statusTestTag)
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(valueTestTag)
            )
        }
        if (!isVerified && value != "Not set") {
            OutlinedButton(
                onClick = onVerifyClick,
                enabled = !isBusy,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag(verifyButtonTestTag)
            ) {
                Text("Verify", fontSize = 12.sp)
            }
        } else if (isVerified) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Verified",
                tint = Color(0xFF059669),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AddressManagementSection(
    addresses: List<UserAddress>,
    isBusy: Boolean,
    onSetDefault: (Long) -> Unit,
    onEditAddress: (UserAddress) -> Unit,
    onDeleteAddress: (UserAddress) -> Unit
) {
    if (addresses.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("empty_addresses_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "No Saved Addresses",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Add your pharmacy, warehouse, or billing address to streamline deliveries and invoicing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("address_list_container"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        addresses.forEach { address ->
            AddressItemCard(
                address = address,
                isBusy = isBusy,
                onSetDefault = { onSetDefault(address.id) },
                onEdit = { onEditAddress(address) },
                onDelete = { onDeleteAddress(address) }
            )
        }
    }
}

@Composable
private fun AddressItemCard(
    address: UserAddress,
    isBusy: Boolean,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("address_card_${address.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (address.isDefault) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (address.isDefault) 1.5.dp else 1.dp,
            color = if (address.isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = address.addressName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (address.isDefault) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("address_default_badge_${address.id}")
                        )
                    }
                }
            }

            Text(
                text = "${address.contactPerson} • ${address.phone}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = address.formattedSingleLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!address.isDefault) {
                    TextButton(
                        onClick = onSetDefault,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("set_default_address_${address.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Set as default address",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Set Default", fontSize = 12.5.sp)
                    }
                } else {
                    Text(
                        text = "Primary dispatch address",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onEdit,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("edit_address_${address.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit address",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.5.sp)
                    }
                    TextButton(
                        onClick = onDelete,
                        enabled = !isBusy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("delete_address_${address.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete address",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalDocumentsCard(
    documents: List<LegalDocument>,
    onDocumentClick: (LegalDocument) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("legal_documents_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            documents.forEachIndexed { index, doc ->
                val icon = when (doc.id) {
                    "terms_and_conditions" -> Icons.Default.Gavel
                    "privacy_policy" -> Icons.Default.Policy
                    "shipping_delivery" -> Icons.Default.LocalShipping
                    "disclaimer" -> Icons.Outlined.Info
                    else -> Icons.Default.Description
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDocumentClick(doc) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("legal_doc_${doc.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.title,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = doc.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open ${doc.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (index < documents.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    status = AccountStatus.ACTIVE,
                    emailVerified = false,
                    phoneVerified = false,
                    profileBio = "Authorized wholesale stockist serving Greater Mumbai"
                ),
                addresses = listOf(
                    UserAddress(
                        id = 1L,
                        userId = 1L,
                        addressName = "Main Pharmacy",
                        contactPerson = "Rohan Sharma",
                        phone = "9876543210",
                        addressLine1 = "Shop 12, Medical Market",
                        addressLine2 = "Andheri East",
                        city = "Mumbai",
                        state = "Maharashtra",
                        postalCode = "400069",
                        country = "India",
                        isDefault = true
                    )
                ),
                legalDocuments = LegalDocumentsCatalog.resolveDocuments("https://api.pharmaos.example.com/"),
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
