package com.lofiq.feature.developer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.BuildConfig
import com.lofiq.R
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*

private val MonochromeCard = Color(0xFF0F0F0F)
private val MonochromeSubtle = Color(0xFF161618)
private val MonochromeBorder = Color.White.copy(alpha = 0.08f)

@Composable
fun DeveloperScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDiagnostics: (() -> Unit)? = null,
    hasActiveSong: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val brandColor = LocalBrandColor.current

    var adminTapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var showAdminPasscodeDialog by remember { mutableStateOf(false) }
    var adminPasscodeInput by remember { mutableStateOf("") }
    var adminPasscodeError by remember { mutableStateOf(false) }

    BackHandler {
        onNavigateBack()
    }

    val copyToClipboard = { label: String, text: String ->
        haptics.play(Haptic.Tap)
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to copy", Toast.LENGTH_SHORT).show()
        }
    }

    val openUrl = { url: String ->
        haptics.play(Haptic.Tap)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    val openInstagram = {
        haptics.play(Haptic.Tap)
        val handle = "its.afxal"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://instagram.com/_u/$handle")).apply {
                setPackage("com.instagram.android")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openUrl("https://www.instagram.com/$handle")
        }
    }

    val openTelegram = {
        haptics.play(Haptic.Tap)
        val handle = "CyberTGz"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$handle")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openUrl("https://t.me/$handle")
        }
    }

    val openEmail = {
        haptics.play(Haptic.Tap)
        val email = "itsafsu@gmail.com"
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_SUBJECT, "LofiQ App Feedback & Connect")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Send Email via..."))
        } catch (e: Exception) {
            openUrl("mailto:$email")
        }
    }

    // Dynamic bottom clearance to ensure MiniPlayer never covers content
    val bottomScrollPadding = if (hasActiveSong) 140.dp else 40.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = bottomScrollPadding)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Screen Header with Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptics.play(Haptic.Tap)
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isLiquidGlass) LiquidGlassTokens.PillGlassFill else MonochromeSubtle)
                        .border(
                            1.dp,
                            if (isLiquidGlass) LiquidGlassTokens.specularBorder(0.20f) else androidx.compose.ui.graphics.SolidColor(MonochromeBorder),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = AccentWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Developer",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = AccentWhite,
                            letterSpacing = (-0.5).sp
                        )
                    )
                    Text(
                        text = "Meet the creator of LofiQ",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // DEVELOPER HERO CARD
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else MonochromeCard,
                border = if (isLiquidGlass) LiquidGlassTokens.specularBorderStroke(0.32f) else BorderStroke(1.dp, MonochromeBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Developer Photo Avatar Ring
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(
                                width = 2.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.75f),
                                        Color.White.copy(alpha = 0.15f)
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_developer),
                            contentDescription = "Muhammed Afsal",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Muhammed Afsal",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentWhite,
                            fontSize = 24.sp,
                            letterSpacing = (-0.4).sp
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "@itsafxal",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pill Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                    ) {
                        Text(
                            text = "Creator & Lead Developer",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = AccentWhite,
                                fontSize = 11.5.sp,
                                letterSpacing = 0.2.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Crafting high-fidelity, ad-free audio software and refined liquid monochrome design for Android.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary.copy(alpha = 0.85f),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // ==========================================
            // CONNECT & SOCIALS SECTION
            // ==========================================
            Text(
                text = "CONNECT & LINKS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.2.sp,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            // 1. Portfolio Website Card
            DeveloperContactCard(
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = "Portfolio",
                        tint = AccentWhite,
                        modifier = Modifier.size(22.dp)
                    )
                },
                title = "Portfolio",
                handle = "itsafxal.vercel.app",
                subtitle = "Personal projects & showcase",
                isLiquidGlass = isLiquidGlass,
                onClick = { openUrl("https://itsafxal.vercel.app/") },
                onCopy = { copyToClipboard("Portfolio URL", "https://itsafxal.vercel.app/") }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Instagram Card
            DeveloperContactCard(
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_instagram),
                        contentDescription = "Instagram",
                        tint = AccentWhite,
                        modifier = Modifier.size(20.dp)
                    )
                },
                title = "Instagram",
                handle = "@its.afxal",
                subtitle = "instagram.com/its.afxal",
                isLiquidGlass = isLiquidGlass,
                onClick = openInstagram,
                onCopy = { copyToClipboard("Instagram Profile", "https://www.instagram.com/its.afxal") }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Telegram Card
            DeveloperContactCard(
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_telegram),
                        contentDescription = "Telegram",
                        tint = AccentWhite,
                        modifier = Modifier.size(20.dp)
                    )
                },
                title = "Telegram",
                handle = "@CyberTGz",
                subtitle = "t.me/CyberTGz",
                isLiquidGlass = isLiquidGlass,
                onClick = openTelegram,
                onCopy = { copyToClipboard("Telegram Handle", "https://t.me/CyberTGz") }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Email Card
            DeveloperContactCard(
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Mail,
                        contentDescription = "Email",
                        tint = AccentWhite,
                        modifier = Modifier.size(22.dp)
                    )
                },
                title = "Email",
                handle = "itsafsu@gmail.com",
                subtitle = "Direct contact & feedback",
                isLiquidGlass = isLiquidGlass,
                onClick = openEmail,
                onCopy = { copyToClipboard("Email Address", "itsafsu@gmail.com") }
            )

            Spacer(modifier = Modifier.height(26.dp))

            // ==========================================
            // ABOUT LOFIQ ARCHITECTURE CARD (Secret Diagnostics Entry)
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime > 3000) {
                            adminTapCount = 1
                        } else {
                            adminTapCount++
                        }
                        lastTapTime = now

                        // Completely silent - NO countdown notifications or toasts!
                        if (adminTapCount >= 7) {
                            haptics.play(Haptic.ToggleOn)
                            adminTapCount = 0
                            adminPasscodeInput = ""
                            adminPasscodeError = false
                            showAdminPasscodeDialog = true
                        }
                    },
                shape = RoundedCornerShape(18.dp),
                color = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else MonochromeCard,
                border = if (isLiquidGlass) LiquidGlassTokens.specularBorderStroke() else BorderStroke(1.dp, MonochromeBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.app_logo_transparent),
                        contentDescription = "LofiQ Logo",
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "LofiQ V${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentWhite,
                                fontSize = 13.5.sp
                            )
                        )
                        Text(
                            text = "Liquid Glass Edition • Built with Jetpack Compose & Media3",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Secret Admin Diagnostics Passcode Dialog
        if (showAdminPasscodeDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAdminPasscodeDialog = false
                    adminPasscodeInput = ""
                    adminPasscodeError = false
                },
                title = {
                    Text(
                        text = "Admin Diagnostics",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentWhite
                        )
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Enter developer authorization key to open system & provider diagnostics.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = adminPasscodeInput,
                            onValueChange = { input ->
                                if (input.length <= 6 && input.all { it.isDigit() }) {
                                    adminPasscodeInput = input
                                    adminPasscodeError = false
                                    if (input == "260626") {
                                        showAdminPasscodeDialog = false
                                        adminPasscodeInput = ""
                                        onNavigateToDiagnostics?.invoke()
                                    }
                                }
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            placeholder = { Text("6-digit PIN", color = TextSecondary) },
                            isError = adminPasscodeError,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentWhite,
                                unfocusedBorderColor = MonochromeBorder,
                                focusedTextColor = AccentWhite,
                                unfocusedTextColor = AccentWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (adminPasscodeError) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Invalid passcode",
                                color = Color(0xFFFF453A),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (adminPasscodeInput == "260626") {
                                showAdminPasscodeDialog = false
                                adminPasscodeInput = ""
                                onNavigateToDiagnostics?.invoke()
                            } else {
                                adminPasscodeError = true
                            }
                        }
                    ) {
                        Text("Unlock", color = AccentWhite, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAdminPasscodeDialog = false
                            adminPasscodeInput = ""
                            adminPasscodeError = false
                        }
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = Color(0xFF141416),
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun DeveloperContactCard(
    icon: @Composable () -> Unit,
    title: String,
    handle: String,
    subtitle: String,
    isLiquidGlass: Boolean,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = if (isLiquidGlass) LiquidGlassTokens.CardGlassFill else MonochromeCard,
        border = if (isLiquidGlass) LiquidGlassTokens.specularBorderStroke() else BorderStroke(1.dp, MonochromeBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Pill
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Label & Handle
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentWhite,
                            fontSize = 15.sp
                        )
                    )
                    Text(
                        text = handle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Quick Copy Action Button
            IconButton(
                onClick = {
                    haptics.play(Haptic.Tap)
                    onCopy()
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy $title",
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // External Open Indicator
            Icon(
                imageVector = Icons.Rounded.OpenInNew,
                contentDescription = "Open $title",
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
