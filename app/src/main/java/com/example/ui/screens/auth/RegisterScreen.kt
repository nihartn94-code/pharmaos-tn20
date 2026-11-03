package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.UserRole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onRegisterSuccess: (UserRole) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Form Fields matching reference image
    var enterpriseName by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var drugLicenseOrGstin by remember { mutableStateOf("") }

    // Optional expandable details (Address / Contact Person)
    var showExtraDetails by remember { mutableStateOf(false) }
    var contactPersonName by remember { mutableStateOf("") }
    var registeredAddress by remember { mutableStateOf("") }

    // Terms Agreement checkbox
    var agreedToTerms by remember { mutableStateOf(true) }
    var showTermsDialog by remember { mutableStateOf(false) }

    // Google Sign-up modal states
    var showGoogleAccountPicker by remember { mutableStateOf(false) }
    var customGoogleEmail by remember { mutableStateOf("") }
    var customGoogleName by remember { mutableStateOf("") }
    var isAddingCustomGoogleAccount by remember { mutableStateOf(false) }

    // Synchronize initial role: if first user, automatically Admin; otherwise respect ViewModel selectedRole
    var selectedRole by remember {
        mutableStateOf(
            if (uiState.isFirstUser) UserRole.ADMIN
            else if (uiState.selectedRole != UserRole.ADMIN) uiState.selectedRole
            else UserRole.STOCKIST
        )
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFC),
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Brand Header (Matching reference image)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Logo Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0097A7),
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(24.dp)) {
                            val stroke = 3.dp.toPx()
                            drawLine(
                                color = Color.White,
                                start = Offset(size.width * 0.3f, size.height * 0.2f),
                                end = Offset(size.width * 0.3f, size.height * 0.85f),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                            drawArc(
                                color = Color.White,
                                startAngle = -90f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(size.width * 0.15f, size.height * 0.18f),
                                size = Size(size.width * 0.65f, size.height * 0.42f),
                                style = Stroke(width = stroke)
                            )
                            // Plus cross accent in loop
                            drawLine(
                                color = Color.White,
                                start = Offset(size.width * 0.48f, size.height * 0.32f),
                                end = Offset(size.width * 0.48f, size.height * 0.46f),
                                strokeWidth = stroke * 0.85f,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(size.width * 0.41f, size.height * 0.39f),
                                end = Offset(size.width * 0.55f, size.height * 0.39f),
                                strokeWidth = stroke * 0.85f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Pharma",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "OS",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF2563EB)
                        )
                    }
                    Text(
                        text = "CONNECT • TRADE • GROW",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // 2. Hero Header Section with 3D Pill Illustration (Matching reference image)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Create Account",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Join India's trusted B2B pharma marketplace & network.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 18.sp
                    )

                    if (uiState.isFirstUser) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "🛡️ First Account: Master Administrator Setup",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0369A1),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Custom Pill & Tablet 3D Illustration Component
                RegisterPillIllustration(
                    modifier = Modifier.size(width = 118.dp, height = 104.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Role Selector Segmented Tabs (Stockist, Company, Manufacturer, and Admin)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RegisterRoleTabItem(
                        title = "Stockist",
                        icon = Icons.Default.Person,
                        isSelected = selectedRole == UserRole.STOCKIST,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("register_role_stockist"),
                        onClick = {
                            selectedRole = UserRole.STOCKIST
                            viewModel.selectRole(UserRole.STOCKIST)
                        }
                    )

                    RegisterRoleTabItem(
                        title = "Company",
                        icon = Icons.Default.Medication,
                        isSelected = selectedRole == UserRole.PHARMA_COMPANY,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("register_role_company"),
                        onClick = {
                            selectedRole = UserRole.PHARMA_COMPANY
                            viewModel.selectRole(UserRole.PHARMA_COMPANY)
                        }
                    )

                    RegisterRoleTabItem(
                        title = "Manufacturer",
                        icon = Icons.Default.Business,
                        isSelected = selectedRole == UserRole.MANUFACTURER,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("register_role_manufacturer"),
                        onClick = {
                            selectedRole = UserRole.MANUFACTURER
                            viewModel.selectRole(UserRole.MANUFACTURER)
                        }
                    )

                    if (uiState.isFirstUser) {
                        RegisterRoleTabItem(
                            title = "Admin",
                            icon = Icons.Default.AdminPanelSettings,
                            isSelected = selectedRole == UserRole.ADMIN,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("register_role_admin"),
                            onClick = {
                                selectedRole = UserRole.ADMIN
                                viewModel.selectRole(UserRole.ADMIN)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Form Input Fields (High-fidelity card containers matching reference screenshot)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Field 1: ENTERPRISE / CONTACT NAME
                B2BInputFieldCard(
                    label = "ENTERPRISE / CONTACT NAME",
                    modifier = Modifier.testTag("register_enterprise_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (enterpriseName.isEmpty()) {
                                Text(
                                    text = "e.g. Apex Pharma Distributors",
                                    fontSize = 14.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            BasicTextField(
                                value = enterpriseName,
                                onValueChange = { enterpriseName = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B)
                                ),
                                cursorBrush = SolidColor(Color(0xFF2563EB)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_fullname_input")
                            )
                        }
                    }
                }

                // Field 2: MOBILE NUMBER (+91 prefix pill + 10-digit number)
                B2BInputFieldCard(
                    label = "MOBILE NUMBER",
                    modifier = Modifier.testTag("register_mobile_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // +91 Country Selector Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "+91",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Vertical divider
                        Box(
                            modifier = Modifier
                                .height(22.dp)
                                .width(1.dp)
                                .background(Color(0xFFE2E8F0))
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (mobileNumber.isEmpty()) {
                                Text(
                                    text = "Enter 10-digit mobile number",
                                    fontSize = 14.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            BasicTextField(
                                value = mobileNumber,
                                onValueChange = {
                                    val digitsOnly = it.filter { ch -> ch.isDigit() }
                                    if (digitsOnly.length <= 10) mobileNumber = digitsOnly
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B)
                                ),
                                cursorBrush = SolidColor(Color(0xFF2563EB)),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_phone_input")
                            )
                        }
                    }
                }

                // Field 3: BUSINESS EMAIL ADDRESS
                B2BInputFieldCard(
                    label = "BUSINESS EMAIL ADDRESS",
                    modifier = Modifier.testTag("register_email_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (emailAddress.isEmpty()) {
                                Text(
                                    text = "contact@pharma-enterprises.com",
                                    fontSize = 14.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            BasicTextField(
                                value = emailAddress,
                                onValueChange = { emailAddress = it.trim() },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B)
                                ),
                                cursorBrush = SolidColor(Color(0xFF2563EB)),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_email_input")
                            )
                        }
                    }
                }

                // Field 4: CREATE SECURE PASSWORD
                B2BInputFieldCard(
                    label = "CREATE SECURE PASSWORD",
                    modifier = Modifier.testTag("register_password_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (password.isEmpty()) {
                                Text(
                                    text = "Min. 8 characters",
                                    fontSize = 14.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            BasicTextField(
                                value = password,
                                onValueChange = { password = it },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                textStyle = TextStyle(
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B)
                                ),
                                cursorBrush = SolidColor(Color(0xFF2563EB)),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_password_input")
                            )
                        }
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Field 5: DRUG LICENSE (DL) OR GSTIN with B2B Verified Chip
                B2BInputFieldCard(
                    label = "DRUG LICENSE (DL) OR GSTIN",
                    badge = "B2B Verified",
                    modifier = Modifier.testTag("register_gstin_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (drugLicenseOrGstin.isEmpty()) {
                                Text(
                                    text = "20B/21B LICENSE NO. OR 15-DIGIT GSTIN",
                                    fontSize = 13.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            BasicTextField(
                                value = drugLicenseOrGstin,
                                onValueChange = { drugLicenseOrGstin = it.uppercase() },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B)
                                ),
                                cursorBrush = SolidColor(Color(0xFF2563EB)),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (agreedToTerms) {
                                            viewModel.register(
                                                fullName = if (contactPersonName.isNotBlank()) contactPersonName else enterpriseName,
                                                businessName = enterpriseName,
                                                email = emailAddress,
                                                phone = mobileNumber,
                                                gstin = drugLicenseOrGstin.ifBlank { null },
                                                address = registeredAddress.ifBlank { "Registered Commercial Premises, India" },
                                                password = password,
                                                confirmPassword = password,
                                                role = selectedRole,
                                                onSuccess = { onRegisterSuccess(selectedRole) }
                                            )
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_gstin_input")
                            )
                        }
                    }
                }

                // Optional Expandable Contact & Address Details
                AnimatedVisibility(visible = showExtraDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Contact Person Name
                        B2BInputFieldCard(label = "CONTACT PERSON NAME (OPTIONAL)") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (contactPersonName.isEmpty()) {
                                        Text(
                                            text = "e.g. Rajesh Sharma (Managing Director)",
                                            fontSize = 14.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    BasicTextField(
                                        value = contactPersonName,
                                        onValueChange = { contactPersonName = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 14.sp,
                                            color = Color(0xFF1E293B)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Registered Address
                        B2BInputFieldCard(label = "REGISTERED COMMERCIAL ADDRESS") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (registeredAddress.isEmpty()) {
                                        Text(
                                            text = "e.g. Plot 42, Pharma Hub, Andheri East, Mumbai",
                                            fontSize = 14.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    BasicTextField(
                                        value = registeredAddress,
                                        onValueChange = { registeredAddress = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 14.sp,
                                            color = Color(0xFF1E293B)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("register_address_input")
                                    )
                                }
                            }
                        }
                    }
                }

                // Toggle for Optional details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = if (showExtraDetails) "− Hide additional address details" else "+ Add registered address details",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2563EB),
                        modifier = Modifier
                            .clickable { showExtraDetails = !showExtraDetails }
                            .padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Terms and Conditions Checkbox (Matching reference image)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { agreedToTerms = !agreedToTerms }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Custom Styled Checkbox
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (agreedToTerms) Color(0xFF2563EB) else Color.White,
                    border = BorderStroke(1.5.dp, if (agreedToTerms) Color(0xFF2563EB) else Color(0xFF94A3B8)),
                    modifier = Modifier.size(20.dp)
                ) {
                    if (agreedToTerms) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Agreed",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "I agree to the ",
                        fontSize = 12.5.sp,
                        color = Color(0xFF475569)
                    )
                    Text(
                        text = "Terms of Service",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.clickable { showTermsDialog = true }
                    )
                    Text(
                        text = " & ",
                        fontSize = 12.5.sp,
                        color = Color(0xFF475569)
                    )
                    Text(
                        text = "Pharma B2B Policy",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.clickable { showTermsDialog = true }
                    )
                    Text(
                        text = ".",
                        fontSize = 12.5.sp,
                        color = Color(0xFF475569)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Primary Action: "Create Account →" (Vibrant royal blue pill button)
            Button(
                onClick = {
                    if (!agreedToTerms) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Please agree to the Terms of Service & Pharma B2B Policy to continue.")
                        }
                        return@Button
                    }
                    viewModel.register(
                        fullName = if (contactPersonName.isNotBlank()) contactPersonName else enterpriseName,
                        businessName = enterpriseName,
                        email = emailAddress,
                        phone = mobileNumber,
                        gstin = drugLicenseOrGstin.ifBlank { null },
                        address = registeredAddress.ifBlank { "Registered Commercial Premises, India" },
                        password = password,
                        confirmPassword = password,
                        role = selectedRole,
                        onSuccess = { onRegisterSuccess(selectedRole) }
                    )
                },
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(6.dp, shape = RoundedCornerShape(26.dp), spotColor = Color(0x662563EB))
                    .testTag("register_submit_button")
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Create Account",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 7. Secondary Action: "Sign up with Google"
            OutlinedButton(
                onClick = {
                    if (customGoogleEmail.isBlank() && emailAddress.isNotBlank() && emailAddress.contains("@")) {
                        customGoogleEmail = emailAddress.trim()
                    }
                    if (customGoogleName.isBlank()) {
                        if (enterpriseName.isNotBlank()) customGoogleName = enterpriseName.trim()
                        else if (contactPersonName.isNotBlank()) customGoogleName = contactPersonName.trim()
                    }
                    showGoogleAccountPicker = true
                },
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("register_google_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    RegisterGoogleIcon(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sign up with Google",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 8. Navigation Link: "Already have an account? Sign In"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already have an account?",
                    fontSize = 13.5.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Sign In",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2563EB),
                    modifier = Modifier
                        .clickable { onNavigateBack() }
                        .padding(vertical = 4.dp)
                        .testTag("register_to_login_button")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 9. Bottom Security Notice (Matching reference image)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Your data is safe with us. We follow industry-standard security practices.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // Terms of Service & B2B Policy Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = {
                Text(
                    text = "PharmaOS B2B Trading Policy",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "1. Drug License & Regulatory Compliance:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "All pharmaceutical trades, offers, and dispatches on PharmaOS must comply with the Drugs and Cosmetics Act and local regulatory guidelines. Registered entities must possess valid 20B/21B retail/wholesale licenses or manufacturing permits.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "2. Credit Ledger & Settlement Terms:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Stockist credit limits, credit notes, and outstanding trade invoices are tracked transparently in the PharmaOS Credit Ledger. Orders exceeding allocated supplier credit limits require pre-payment.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "3. Batch Tracking & Expiry Safeguards:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Near-expiry batches (<90 days) trigger automatic early-warning alerts, preventing accidental dispatch of substandard stock.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        agreedToTerms = true
                        showTermsDialog = false
                    }
                ) {
                    Text("Accept & Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Google Sign-Up Account Picker Dialog
    if (showGoogleAccountPicker) {
        Dialog(
            onDismissRequest = {
                showGoogleAccountPicker = false
                isAddingCustomGoogleAccount = false
            }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header Row with Google branding
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RegisterGoogleIcon(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign up with Google",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        IconButton(
                            onClick = {
                                showGoogleAccountPicker = false
                                isAddingCustomGoogleAccount = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Create your PharmaOS B2B enterprise account with Google",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Role Chip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2563EB).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Registering as: ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = selectedRole.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Direct Google Email Registration Form
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Enter Google / Corporate Email",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customGoogleEmail,
                                onValueChange = { customGoogleEmail = it },
                                label = { Text("Google Email ID") },
                                placeholder = { Text("user@gmail.com") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_google_email_input")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = customGoogleName,
                                onValueChange = { customGoogleName = it },
                                label = { Text("Enterprise / Business Name") },
                                placeholder = { Text("e.g. Apollo Healthcare Ltd") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Business,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_google_name_input")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val cleanEmail = customGoogleEmail.trim()
                                    if (cleanEmail.isNotBlank()) {
                                        val cleanName = customGoogleName.trim().ifBlank {
                                            if (enterpriseName.isNotBlank()) enterpriseName.trim()
                                            else if (contactPersonName.isNotBlank()) contactPersonName.trim()
                                            else {
                                                cleanEmail.substringBefore("@")
                                                    .replace(".", " ")
                                                    .replace("_", " ")
                                                    .split(" ")
                                                    .filter { it.isNotBlank() }
                                                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                                                    .ifBlank { "Enterprise Partner" }
                                            }
                                        }
                                        viewModel.signInWithGoogle(
                                            email = cleanEmail,
                                            displayName = cleanName,
                                            role = selectedRole,
                                            onSuccess = {
                                                showGoogleAccountPicker = false
                                                onRegisterSuccess(selectedRole)
                                            }
                                        )
                                    }
                                },
                                enabled = customGoogleEmail.isNotBlank() && !uiState.isLoading,
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("register_google_api_submit_button")
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GoogleIcon(modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Sign Up via Google API",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Privacy notice
                    Text(
                        text = "By signing up with Google, you agree to the PharmaOS Terms of Service and Privacy Policy.",
                        fontSize = 10.5.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Reusable Card container for B2B input fields matching the reference UI style
 */
@Composable
private fun B2BInputFieldCard(
    label: String,
    badge: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = Color(0xFF64748B)
                )

                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE0F7FA)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00838F),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            content()
        }
    }
}

/**
 * Tab Item for Role Selector (Stockist, Company, Manufacturer, Admin)
 */
@Composable
private fun RegisterRoleTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF2563EB) else Color.Transparent,
        label = "registerRoleTabBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF64748B),
        label = "registerRoleTabContent"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        shadowElevation = if (isSelected) 3.dp else 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Custom Compose Canvas rendering the 3D Pill, Tablet, Leaf, and soft Aura
 * shown in the reference image.
 */
@Composable
private fun RegisterPillIllustration(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Soft glowing organic background aura
        val auraPath = Path().apply {
            moveTo(w * 0.15f, h * 0.35f)
            cubicTo(w * 0.25f, h * 0.05f, w * 0.85f, h * 0.05f, w * 0.95f, h * 0.45f)
            cubicTo(w * 1.05f, h * 0.85f, w * 0.65f, h * 0.98f, w * 0.35f, h * 0.92f)
            cubicTo(w * 0.05f, h * 0.85f, w * 0.08f, h * 0.55f, w * 0.15f, h * 0.35f)
            close()
        }
        drawPath(
            path = auraPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFBAE6FD).copy(alpha = 0.85f),
                    Color(0xFFE0F2FE).copy(alpha = 0.45f),
                    Color(0xFFF0F9FF).copy(alpha = 0.05f)
                ),
                center = Offset(w * 0.6f, h * 0.5f),
                radius = w * 0.55f
            )
        )

        // 2. White pharmaceutical tablet disc in background
        val tabletCenter = Offset(w * 0.75f, h * 0.64f)
        val tabletRadiusX = w * 0.18f
        val tabletRadiusY = h * 0.10f

        // Tablet shadow
        drawOval(
            color = Color(0x220F172A),
            topLeft = Offset(tabletCenter.x - tabletRadiusX, tabletCenter.y - tabletRadiusY + 3.dp.toPx()),
            size = Size(tabletRadiusX * 2, tabletRadiusY * 2)
        )
        // Tablet body
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, Color(0xFFF1F5F9), Color(0xFFE2E8F0)),
                startY = tabletCenter.y - tabletRadiusY,
                endY = tabletCenter.y + tabletRadiusY
            ),
            topLeft = Offset(tabletCenter.x - tabletRadiusX, tabletCenter.y - tabletRadiusY),
            size = Size(tabletRadiusX * 2, tabletRadiusY * 2)
        )
        // Tablet debossed center score line
        drawLine(
            color = Color(0xFFCBD5E1),
            start = Offset(tabletCenter.x - tabletRadiusX * 0.55f, tabletCenter.y),
            end = Offset(tabletCenter.x + tabletRadiusX * 0.55f, tabletCenter.y),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 3. Fresh Green Leaf accent (matching reference image)
        val leafPath = Path().apply {
            moveTo(w * 0.28f, h * 0.48f)
            cubicTo(w * 0.20f, h * 0.35f, w * 0.28f, h * 0.26f, w * 0.38f, h * 0.28f)
            cubicTo(w * 0.40f, h * 0.38f, w * 0.36f, h * 0.48f, w * 0.28f, h * 0.48f)
            close()
        }
        drawPath(
            path = leafPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF4ADE80), Color(0xFF16A34A)),
                start = Offset(w * 0.28f, h * 0.48f),
                end = Offset(w * 0.38f, h * 0.28f)
            )
        )
        drawLine(
            color = Color(0xFF15803D),
            start = Offset(w * 0.28f, h * 0.46f),
            end = Offset(w * 0.36f, h * 0.30f),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 4. Capsule Pill (Angled at -35 degrees)
        rotate(degrees = -35f, pivot = Offset(w * 0.52f, h * 0.52f)) {
            val pillWidth = w * 0.26f
            val pillHeight = h * 0.56f
            val pillLeft = (w * 0.52f) - (pillWidth / 2f)
            val pillTop = (h * 0.52f) - (pillHeight / 2f)
            val cornerRadius = pillWidth / 2f

            // Capsule Drop Shadow
            drawRoundRect(
                color = Color(0x331E3A8A),
                topLeft = Offset(pillLeft, pillTop + 4.dp.toPx()),
                size = Size(pillWidth, pillHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )

            // Top Cap (Vivid Royal Blue)
            clipRect(
                left = pillLeft,
                top = pillTop,
                right = pillLeft + pillWidth,
                bottom = pillTop + (pillHeight / 2f)
            ) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
                        start = Offset(pillLeft, pillTop),
                        end = Offset(pillLeft + pillWidth, pillTop + pillHeight / 2f)
                    ),
                    topLeft = Offset(pillLeft, pillTop),
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )

                // Specular light glare on blue cap
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.6f), Color.Transparent),
                        start = Offset(pillLeft + 3.dp.toPx(), pillTop + 4.dp.toPx()),
                        end = Offset(pillLeft + pillWidth * 0.6f, pillTop + pillHeight * 0.25f)
                    ),
                    topLeft = Offset(pillLeft + 3.dp.toPx(), pillTop + 4.dp.toPx()),
                    size = Size(pillWidth * 0.32f, pillHeight * 0.35f),
                    cornerRadius = CornerRadius(cornerRadius * 0.6f, cornerRadius * 0.6f)
                )
            }

            // Bottom Cap (Pure Pearlescent White)
            clipRect(
                left = pillLeft,
                top = pillTop + (pillHeight / 2f),
                right = pillLeft + pillWidth,
                bottom = pillTop + pillHeight
            ) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC), Color(0xFFE2E8F0)),
                        start = Offset(pillLeft, pillTop + pillHeight / 2f),
                        end = Offset(pillLeft + pillWidth, pillTop + pillHeight)
                    ),
                    topLeft = Offset(pillLeft, pillTop),
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )

                // Specular light glare on white cap
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.9f), Color.Transparent),
                        start = Offset(pillLeft + 3.dp.toPx(), pillTop + pillHeight / 2f + 2.dp.toPx()),
                        end = Offset(pillLeft + pillWidth * 0.5f, pillTop + pillHeight * 0.8f)
                    ),
                    topLeft = Offset(pillLeft + 3.dp.toPx(), pillTop + pillHeight / 2f + 2.dp.toPx()),
                    size = Size(pillWidth * 0.28f, pillHeight * 0.32f),
                    cornerRadius = CornerRadius(cornerRadius * 0.5f, cornerRadius * 0.5f)
                )
            }

            // Center separation ring line
            drawLine(
                color = Color(0x330F172A),
                start = Offset(pillLeft, pillTop + (pillHeight / 2f)),
                end = Offset(pillLeft + pillWidth, pillTop + (pillHeight / 2f)),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

/**
 * Authentic 4-color Google 'G' icon rendered with Compose Canvas
 */
@Composable
private fun RegisterGoogleIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = (w.coerceAtMost(h) / 2f) * 0.88f
        val strokeW = r * 0.38f

        // Red top arc
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 180f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = strokeW)
        )

        // Yellow left arc
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 140f,
            sweepAngle = 45f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = strokeW)
        )

        // Green bottom arc
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = strokeW)
        )

        // Blue right arc
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -40f,
            sweepAngle = 85f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = strokeW)
        )

        // Blue horizontal crossbar
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(cx - strokeW * 0.1f, cy),
            end = Offset(cx + r, cy),
            strokeWidth = strokeW,
            cap = StrokeCap.Square
        )
    }
}
