package com.lofiq.feature.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.lofiq.R
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    viewModel: FirebaseAuthViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val brandColor = LocalBrandColor.current

    var passwordVisible by remember { mutableStateOf(false) }

    // Google Sign-In Activity Result Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.handleGoogleSignInResult(account, null)
        } catch (e: ApiException) {
            viewModel.handleGoogleSignInResult(null, e)
        }
    }

    DisposableEffect(Unit) {
        viewModel.resetAuthState()
        onDispose {
            viewModel.resetAuthState()
        }
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            haptics.play(Haptic.Select)
            onAuthSuccess()
        }
    }

    // Glowing Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Ambient Background Glow Gradients
        Box(
            modifier = Modifier
                .size(360.dp)
                .offset(x = (-40).dp, y = (-40).dp)
                .scale(glowScale)
                .blur(90.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandPink.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 40.dp, y = 40.dp)
                .scale(glowScale)
                .blur(90.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandTeal.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
        )

        // Optional Top Dismiss Button if presented modally
        if (onDismiss != null) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = AccentWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(if (onDismiss != null) 10.dp else 20.dp))

            // App Logo with Glowing Outer Ring
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(BrandPink.copy(alpha = 0.2f), BrandTeal.copy(alpha = 0.2f))
                        )
                    )
                    .border(
                        BorderStroke(
                            2.dp,
                            Brush.linearGradient(listOf(BrandPink, BrandTeal))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo_transparent),
                    contentDescription = "LofiQ Logo",
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(58.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "LOFIQ",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    brush = Brush.horizontalGradient(
                        listOf(BrandPink, Color(0xFFC084FC), BrandTeal)
                    )
                )
            )

            Text(
                text = "Your Infinite Music Universe",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            // Auth Card Container (Glassmorphism)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = SurfaceElevated.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, AccentWhite.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Sign In / Sign Up Mode Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(SurfaceCard)
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (uiState.authMode == AuthMode.SIGN_IN) brandColor else Color.Transparent)
                                .clickable {
                                    haptics.play(Haptic.Tap)
                                    viewModel.setAuthMode(AuthMode.SIGN_IN)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign In",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.authMode == AuthMode.SIGN_IN) AccentWhite else TextSecondary
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (uiState.authMode == AuthMode.SIGN_UP) brandColor else Color.Transparent)
                                .clickable {
                                    haptics.play(Haptic.Tap)
                                    viewModel.setAuthMode(AuthMode.SIGN_UP)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create Account",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.authMode == AuthMode.SIGN_UP) AccentWhite else TextSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Google Sign-In Button
                    Button(
                        onClick = {
                            haptics.play(Haptic.Tap)
                            val client = viewModel.getGoogleSignInClient(context)
                            googleSignInLauncher.launch(client.signInIntent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Google G Icon
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = "Google",
                                    tint = Color(0xFF4285F4),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Continue with Google",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1F2937)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Divider: OR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = AccentWhite.copy(alpha = 0.12f)
                        )
                        Text(
                            text = "OR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = AccentWhite.copy(alpha = 0.12f)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Display Name Field (Only in Sign Up mode)
                    AnimatedVisibility(
                        visible = uiState.authMode == AuthMode.SIGN_UP,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            OutlinedTextField(
                                value = uiState.displayName,
                                onValueChange = { viewModel.onDisplayNameChange(it) },
                                label = { Text("Full Name") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandColor,
                                    unfocusedBorderColor = AccentWhite.copy(alpha = 0.15f),
                                    focusedTextColor = AccentWhite,
                                    unfocusedTextColor = AccentWhite,
                                    focusedLabelColor = brandColor,
                                    unfocusedLabelColor = TextSecondary,
                                    focusedContainerColor = SurfaceCard,
                                    unfocusedContainerColor = SurfaceCard
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Email Input Field
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = { viewModel.onEmailChange(it) },
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Email,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = brandColor,
                            unfocusedBorderColor = AccentWhite.copy(alpha = 0.15f),
                            focusedTextColor = AccentWhite,
                            unfocusedTextColor = AccentWhite,
                            focusedLabelColor = brandColor,
                            unfocusedLabelColor = TextSecondary,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password Input Field
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = { viewModel.onPasswordChange(it) },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Toggle password",
                                    tint = TextSecondary
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = brandColor,
                            unfocusedBorderColor = AccentWhite.copy(alpha = 0.15f),
                            focusedTextColor = AccentWhite,
                            unfocusedTextColor = AccentWhite,
                            focusedLabelColor = brandColor,
                            unfocusedLabelColor = TextSecondary,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            viewModel.submitEmailAuth()
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Error Message Banner
                    AnimatedVisibility(visible = !uiState.errorMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text(
                                text = uiState.errorMessage.orEmpty(),
                                color = Color(0xFFFF6B6B),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Submit Button (Sign In / Sign Up)
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            haptics.play(Haptic.Tap)
                            viewModel.submitEmailAuth()
                        },
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandColor,
                            contentColor = AccentWhite
                        )
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AccentWhite,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = if (uiState.authMode == AuthMode.SIGN_IN) "Sign In" else "Create LofiQ Account",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Guest Continue Action
            OutlinedButton(
                onClick = {
                    haptics.play(Haptic.Tap)
                    viewModel.continueAsGuest()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, AccentWhite.copy(alpha = 0.15f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary
                )
            ) {
                Text(
                    text = "Continue as Guest / Explore",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "By continuing, your in-app playlists and favorites will sync to your LofiQ cloud.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
