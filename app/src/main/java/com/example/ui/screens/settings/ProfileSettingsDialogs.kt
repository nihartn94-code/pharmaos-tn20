package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.AuthUser
import com.example.domain.model.ContactVerificationChannel
import com.example.domain.model.LegalDocument
import com.example.domain.model.UserAddress

@Composable
internal fun EditProfileDialog(
    user: AuthUser,
    isSaving: Boolean,
    validationError: String?,
    onDismiss: () -> Unit,
    onSave: (
        fullName: String,
        businessName: String,
        email: String,
        phone: String,
        gstin: String?,
        profileBio: String?
    ) -> Unit
) {
    var fullName by remember(user) { mutableStateOf(user.fullName) }
    var businessName by remember(user) { mutableStateOf(user.businessName) }
    var email by remember(user) { mutableStateOf(user.email) }
    var phone by remember(user) { mutableStateOf(user.phone) }
    var gstin by remember(user) { mutableStateOf(user.gstin.orEmpty()) }
    var profileBio by remember(user) { mutableStateOf(user.profileBio.orEmpty()) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .testTag("edit_profile_dialog"),
        title = {
            Text(
                text = "Edit Profile Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!validationError.isNullOrBlank()) {
                    InlineValidationBanner(
                        message = validationError,
                        testTag = "profile_validation_error"
                    )
                }

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_input_full_name")
                )

                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Business / Pharmacy Name *") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_input_business_name")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address *") },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    supportingText = {
                        Text("Changing your email clears verification until re-verified with the server.")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_input_email")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number *") },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    supportingText = {
                        Text("Changing your phone number clears verification until re-verified.")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_input_phone")
                )

                OutlinedTextField(
                    value = gstin,
                    onValueChange = { gstin = it.uppercase() },
                    label = { Text("GSTIN (Optional, 15 chars)") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_input_gstin")
                )

                OutlinedTextField(
                    value = profileBio,
                    onValueChange = { profileBio = it },
                    label = { Text("Profile Information / Notes") },
                    minLines = 2,
                    maxLines = 3,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_input_bio")
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Account Role: ${user.role.displayName} (managed by PharmaOS governance)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        fullName,
                        businessName,
                        email,
                        phone,
                        gstin.takeIf { it.isNotBlank() },
                        profileBio.takeIf { it.isNotBlank() }
                    )
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("save_profile_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.testTag("cancel_profile_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun AddressFormDialog(
    initialAddress: UserAddress?,
    defaultContactName: String,
    defaultPhone: String,
    isSaving: Boolean,
    validationError: String?,
    onDismiss: () -> Unit,
    onSave: (UserAddress) -> Unit
) {
    val isEditing = initialAddress != null && initialAddress.id > 0L
    val addressTypePresets = listOf("Main Pharmacy", "Warehouse", "Billing Office", "Branch")

    var addressName by remember(initialAddress) {
        mutableStateOf(initialAddress?.addressName ?: "Main Pharmacy")
    }
    var contactPerson by remember(initialAddress) {
        mutableStateOf(initialAddress?.contactPerson ?: defaultContactName)
    }
    var phone by remember(initialAddress) {
        mutableStateOf(initialAddress?.phone ?: defaultPhone)
    }
    var addressLine1 by remember(initialAddress) {
        mutableStateOf(initialAddress?.addressLine1.orEmpty())
    }
    var addressLine2 by remember(initialAddress) {
        mutableStateOf(initialAddress?.addressLine2.orEmpty())
    }
    var city by remember(initialAddress) {
        mutableStateOf(initialAddress?.city.orEmpty())
    }
    var state by remember(initialAddress) {
        mutableStateOf(initialAddress?.state.orEmpty())
    }
    var postalCode by remember(initialAddress) {
        mutableStateOf(initialAddress?.postalCode.orEmpty())
    }
    var country by remember(initialAddress) {
        mutableStateOf(initialAddress?.country?.ifBlank { "India" } ?: "India")
    }
    var isDefault by remember(initialAddress) {
        mutableStateOf(initialAddress?.isDefault ?: false)
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .testTag("address_form_dialog"),
        title = {
            Text(
                text = if (isEditing) "Edit Address" else "Add New Address",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!validationError.isNullOrBlank()) {
                    InlineValidationBanner(
                        message = validationError,
                        testTag = "address_validation_error"
                    )
                }

                // Quick Address Type Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    addressTypePresets.take(3).forEach { preset ->
                        FilterChip(
                            selected = addressName.equals(preset, ignoreCase = true),
                            onClick = { addressName = preset },
                            label = { Text(preset, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = addressName,
                    onValueChange = { addressName = it },
                    label = { Text("Address Name / Type *") },
                    placeholder = { Text("e.g. Main Pharmacy, Warehouse") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_input_name")
                )

                OutlinedTextField(
                    value = contactPerson,
                    onValueChange = { contactPerson = it },
                    label = { Text("Contact Person *") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_input_contact_person")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number *") },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_input_phone")
                )

                OutlinedTextField(
                    value = addressLine1,
                    onValueChange = { addressLine1 = it },
                    label = { Text("Address Line 1 *") },
                    placeholder = { Text("Building, Shop No., Street") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_input_line1")
                )

                OutlinedTextField(
                    value = addressLine2,
                    onValueChange = { addressLine2 = it },
                    label = { Text("Address Line 2 (Optional)") },
                    placeholder = { Text("Area, Landmark, Industrial Estate") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_input_line2")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City *") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("address_input_city")
                    )
                    OutlinedTextField(
                        value = state,
                        onValueChange = { state = it },
                        label = { Text("State *") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("address_input_state")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = postalCode,
                        onValueChange = { postalCode = it },
                        label = { Text("Postal Code *") },
                        singleLine = true,
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("address_input_postal_code")
                    )
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("Country *") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("address_input_country")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Set as Default Address",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Used for dispatch, billing, and tax invoices",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("address_input_default_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        UserAddress(
                            id = initialAddress?.id ?: 0L,
                            userId = initialAddress?.userId ?: 0L,
                            addressName = addressName,
                            contactPerson = contactPerson,
                            phone = phone,
                            addressLine1 = addressLine1,
                            addressLine2 = addressLine2,
                            city = city,
                            state = state,
                            postalCode = postalCode,
                            country = country,
                            isDefault = isDefault
                        )
                    )
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("save_address_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isEditing) "Update Address" else "Save Address")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.testTag("cancel_address_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun DeleteAddressConfirmDialog(
    address: UserAddress,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        modifier = Modifier.testTag("delete_address_dialog"),
        title = {
            Text(
                text = "Delete Address?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Remove \"${address.addressName}\" (${address.formattedSingleLine()}) from your saved addresses?"
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_delete_address_button")
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun ContactVerificationDialog(
    channel: ContactVerificationChannel,
    targetValue: String,
    statusHint: String?,
    isVerifying: Boolean,
    onDismiss: () -> Unit,
    onConfirmCode: (String) -> Unit
) {
    var code by remember(channel, targetValue) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .testTag("contact_verification_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Verify ${channel.label}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = statusHint ?: "Enter the verification code sent by the backend server to $targetValue.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Verification Code") },
                    singleLine = true,
                    enabled = !isVerifying,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verification_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmCode(code) },
                enabled = !isVerifying && code.trim().length >= 4,
                modifier = Modifier.testTag("confirm_verification_button")
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Confirm Verification")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isVerifying) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun LegalDocumentViewerDialog(
    document: LegalDocument,
    onDismiss: () -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("legal_document_viewer"),
        icon = {
            Icon(
                imageVector = Icons.Default.Policy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = document.title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("legal_viewer_title")
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = document.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!document.versionLabel.isNullOrBlank()) {
                    Text(
                        text = "Document Version: ${document.versionLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (!document.configuredUrl.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Configured Legal Document URL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = document.configuredUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("legal_viewer_url")
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { onOpenExternalUrl(document.configuredUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("legal_doc_open_url_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open document URL in browser",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Official Document")
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "No hosted legal document URL is currently configured for this environment. Configure your organization's backend host in Backend API & Sync to load your approved published legal documents.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("legal_viewer_unconfigured_notice")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("legal_viewer_close_button")
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
internal fun InlineValidationBanner(
    message: String,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Validation error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
