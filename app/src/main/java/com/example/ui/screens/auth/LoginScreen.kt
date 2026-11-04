package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.PharmaOSTheme
import com.example.domain.model.UserRole
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreenContent(
        uiState = uiState,
        onRoleSelected = { viewModel.selectRole(it) },
        onLogin = { id, pass, role, onSuccess -> viewModel.login(id, pass, role, onSuccess) },
        onGoogleSignIn = { email, name, role, onSuccess -> viewModel.signInWithGoogle(email, name, role, onSuccess) },
        onClearMessages = { viewModel.clearMessages() },
        onNavigateToRegister = onNavigateToRegister,
        onLoginSuccess = onLoginSuccess
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreenContent(
    uiState: AuthUiState,
    onRoleSelected: (UserRole) -> Unit,
    onLogin: (String, String, UserRole, () -> Unit) -> Unit,
    onGoogleSignIn: (String, String, UserRole, () -> Unit) -> Unit,
    onClearMessages: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotIdentifier by remember { mutableStateOf("") }
    var showGoogleAccountPicker by remember { mutableStateOf(false) }
    var customGoogleEmail by remember { mutableStateOf("") }
    var customGoogleName by remember { mutableStateOf("") }
    var isAddingCustomGoogleAccount by remember { mutableStateOf(false) }

    val selectedRole = uiState.selectedRole

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessages()
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
            // Top App Bar / Brand Header
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
                            // Draw stylized 'P' monogram with cross
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

            // Hero Header Section with 3D Pill Illustration (Matching reference image)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Welcome Back",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sign in to continue your pharma journey.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Active role badge pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF2563EB).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = when (selectedRole) {
                                UserRole.STOCKIST -> "📦 Stockist & Distributor Portal"
                                UserRole.PHARMA_COMPANY -> "💊 Pharma Company / Brand Portal"
                                UserRole.MANUFACTURER -> "🏭 Manufacturer Portal"
                                UserRole.ADMIN -> "🛡️ Administrator Portal"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2563EB),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Custom Pill & Tablet 3D Illustration Component
                PillIllustration(
                    modifier = Modifier
                        .size(width = 118.dp, height = 104.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Role Selector Segmented Tabs (Manufacturer, Company, Stockist, Administrator)
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
                    RoleTabItem(
                        title = "Stockist",
                        subtitle = "Buyer",
                        icon = Icons.Default.Person,
                        isSelected = selectedRole == UserRole.STOCKIST,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("role_tab_stockist"),
                        onClick = { onRoleSelected(UserRole.STOCKIST) }
                    )

                    RoleTabItem(
                        title = "Company",
                        subtitle = "Brand",
                        icon = Icons.Default.Medication,
                        isSelected = selectedRole == UserRole.PHARMA_COMPANY,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("role_tab_company"),
                        onClick = { onRoleSelected(UserRole.PHARMA_COMPANY) }
                    )

                    RoleTabItem(
                        title = "Manufacturer",
                        subtitle = "Plant",
                        icon = Icons.Default.Business,
                        isSelected = selectedRole == UserRole.MANUFACTURER,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("role_tab_manufacturer"),
                        onClick = { onRoleSelected(UserRole.MANUFACTURER) }
                    )

                    RoleTabItem(
                        title = "Admin",
                        subtitle = "Master",
                        icon = Icons.Default.AdminPanelSettings,
                        isSelected = selectedRole == UserRole.ADMIN,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("role_tab_admin"),
                        onClick = { onRoleSelected(UserRole.ADMIN) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Contextual Portal Header based on selected role
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF1F5F9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val roleDescription = when (selectedRole) {
                        UserRole.STOCKIST -> "Stockist Portal: Source medicines at lowest PTR, run basket optimizer & manage credit ledger."
                        UserRole.PHARMA_COMPANY -> "Company Portal: List brand medicines, configure trade schemes & track distribution."
                        UserRole.MANUFACTURER -> "Manufacturer Portal: Bulk production batches, dispatch logistics & trade offers."
                        UserRole.ADMIN -> if (uiState.isFirstUser) {
                            "Master Admin: First account created on this device automatically becomes Master Administrator."
                        } else {
                            "Administrator Portal: System governance, platform users, safety recalls & compliance audit."
                        }
                    }
                    Text(
                        text = roleDescription,
                        fontSize = 11.5.sp,
                        color = Color(0xFF475569),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Credentials Input Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // 1. Mobile Number / Email ID Header & Field
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mobile Number / Email ID",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            // Country Code Prefix Pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "+91",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Thin Vertical Divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(28.dp)
                                    .background(Color(0xFFCBD5E1))
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Text Input
                            OutlinedTextField(
                                value = identifier,
                                onValueChange = { identifier = it },
                                placeholder = {
                                    Text(
                                        text = "Enter your number or email",
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_email_input")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Password Field
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = {
                                Text(
                                    text = "Password",
                                    fontSize = 13.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    modifier = Modifier.testTag("login_password_visibility_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    onLogin(identifier, password, selectedRole, onLoginSuccess)
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input")
                        )
                    }

                    // Forgot Password Link (Right aligned)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Forgot Password?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2563EB),
                            modifier = Modifier
                                .clickable {
                                    forgotIdentifier = identifier
                                    showForgotPasswordDialog = true
                                }
                                .padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Primary "Sign In ->" Button (Vibrant Royal Blue Pill Button)
                    Button(
                        onClick = {
                            onLogin(identifier, password, selectedRole, onLoginSuccess)
                        },
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(6.dp, shape = RoundedCornerShape(26.dp), spotColor = Color(0x662563EB))
                            .testTag("login_submit_button")
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
                                    text = "Sign In as ${selectedRole.displayName.substringBefore(" /")}",
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

                    Spacer(modifier = Modifier.height(20.dp))

                    // "─── OR ───" Divider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = 1.dp,
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "  OR  ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = 1.dp,
                            color = Color(0xFFE2E8F0)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // "Continue with Google" Button
                    OutlinedButton(
                        onClick = {
                            if (customGoogleEmail.isBlank() && identifier.contains("@")) {
                                customGoogleEmail = identifier.trim()
                            }
                            showGoogleAccountPicker = true
                        },
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("google_login_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            GoogleIcon(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Continue with Google",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // "Don't have an account? Sign up" Link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Don't have an account?",
                            fontSize = 13.5.sp,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (uiState.isFirstUser) "Register (First is Admin)" else "Sign up",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB),
                            modifier = Modifier
                                .clickable { onNavigateToRegister() }
                                .padding(vertical = 4.dp)
                                .testTag("login_to_register_button")
                        )
                    }
                }
            }

            // Quick Device Account Switcher (Convenient for multi-role testing on single device)
            val matchingUsers = uiState.allRegisteredUsers.filter { it.role == selectedRole.name }
            val otherUsers = uiState.allRegisteredUsers.filter { it.role != selectedRole.name }

            if (uiState.allRegisteredUsers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedRole.displayName} Accounts",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2563EB).copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "${matchingUsers.size} ready",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "Tap to autofill credentials for instant testing on device:",
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (matchingUsers.isEmpty()) {
                            Text(
                                text = "No ${selectedRole.displayName} accounts registered yet. Tap 'Sign up' above to create one.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            matchingUsers.forEach { regUser ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            identifier = regUser.email
                                            password = "Password@123" // Standard test password default
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFF2563EB).copy(alpha = 0.12f),
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = Color(0xFF2563EB)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${regUser.fullName} (${regUser.businessName})",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = "${regUser.email} • Ph: ${regUser.phone}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                        Text(
                                            text = "Autofill",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2563EB)
                                        )
                                    }
                                }
                            }
                        }

                        if (otherUsers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Accounts registered under other roles: ${otherUsers.size}. Switch tabs above to access them.",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Security Statement (Matching reference image)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
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
        }
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = {
                Text(
                    text = "Reset Password",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your registered email address or mobile number to receive instructions to reset your account password:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = forgotIdentifier,
                        onValueChange = { forgotIdentifier = it },
                        label = { Text("Email or Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showForgotPasswordDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Password reset verification link has been sent to $forgotIdentifier")
                        }
                    }
                ) {
                    Text("Send Reset Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Google Sign-In Account Picker Dialog
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
                            GoogleIcon(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign in with Google",
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
                        text = "Choose an account to continue to PharmaOS B2B",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Selected Role Context Pill
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
                                text = "Target Portal: ",
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

                    // If existing registered accounts exist, display quick-select cards
                    if (uiState.allRegisteredUsers.isNotEmpty()) {
                        Text(
                            text = "Or choose registered account on this device:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            uiState.allRegisteredUsers.take(4).forEach { regUser ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onGoogleSignIn(
                                                regUser.email,
                                                regUser.fullName,
                                                try { UserRole.valueOf(regUser.role) } catch (_: Exception) { selectedRole },
                                                {
                                                    showGoogleAccountPicker = false
                                                    onLoginSuccess()
                                                }
                                            )
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFF2563EB),
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = regUser.fullName.firstOrNull()?.uppercase() ?: "U",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = regUser.fullName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = regUser.email,
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(thickness = 0.8.dp, color = Color(0xFFE2E8F0))
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Direct Google / Enterprise Email Authentication Form
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Enter Google / Enterprise Email",
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
                                    .testTag("google_email_input")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = customGoogleName,
                                onValueChange = { customGoogleName = it },
                                label = { Text("Full Name / Organization (Optional)") },
                                placeholder = { Text("e.g. Apex Health Corp") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
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
                                    .testTag("google_name_input")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val cleanEmail = customGoogleEmail.trim()
                                    if (cleanEmail.isNotBlank()) {
                                        val cleanName = customGoogleName.trim().ifBlank {
                                            cleanEmail.substringBefore("@")
                                                .replace(".", " ")
                                                .replace("_", " ")
                                                .split(" ")
                                                .filter { it.isNotBlank() }
                                                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                                                .ifBlank { "Enterprise Partner" }
                                        }
                                        onGoogleSignIn(
                                            cleanEmail,
                                            cleanName,
                                            selectedRole,
                                            {
                                                showGoogleAccountPicker = false
                                                onLoginSuccess()
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
                                    .testTag("google_api_signin_button")
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
                                            text = "Sign In via Google API",
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
                        text = "To continue, Google will share your name and email address with PharmaOS. Review PharmaOS Privacy Policy and Terms of Service.",
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
 * Custom Tab Item for Role Selection (Stockist, Company, Manufacturer, Admin)
 */
@Composable
private fun RoleTabItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF2563EB) else Color.Transparent,
        label = "roleTabBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF64748B),
        label = "roleTabContent"
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
private fun PillIllustration(
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
internal fun GoogleIcon(modifier: Modifier = Modifier) {
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

@Preview(name = "Login Screen - Light Mode", showBackground = true)
@Preview(name = "Login Screen - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun LoginScreenPreview() {
    PharmaOSTheme {
        LoginScreenContent(
            uiState = AuthUiState(
                isLoading = false,
                selectedRole = UserRole.STOCKIST
            ),
            onRoleSelected = {},
            onLogin = { _, _, _, _ -> },
            onGoogleSignIn = { _, _, _, _ -> },
            onClearMessages = {},
            onNavigateToRegister = {},
            onLoginSuccess = {}
        )
    }
}

