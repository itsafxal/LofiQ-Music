package com.lofiq.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.lofiq.ui.components.glass.*
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*

@Composable
fun AccountDialog(
    userAccount: UserAccount,
    isAuthenticating: Boolean,
    onDismiss: () -> Unit,
    onStartLogin: () -> Unit,
    onRefreshProfile: () -> Unit,
    onSignOut: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val haptics = rememberHaptics()
    val brandColor = LocalBrandColor.current
    var showSignOutConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(28.dp),
            preset = GlassPreset.MODAL,
            hasGlow = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top close bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACCOUNT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp,
                            fontSize = 11.sp
                        )
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // User Avatar
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(88.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    brandColor.copy(alpha = 0.35f),
                                    BrandTeal.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                ) {
                    if (userAccount.isLoggedIn && !userAccount.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = userAccount.avatarUrl,
                            contentDescription = userAccount.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .border(2.dp, brandColor.copy(alpha = 0.8f), CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(SurfaceElevated, SurfaceCard)
                                    )
                                )
                                .border(
                                    1.dp,
                                    AccentWhite.copy(alpha = 0.2f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (userAccount.isLoggedIn) Icons.Rounded.Person else Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = AccentWhite.copy(alpha = 0.8f),
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Name & Email
                Text(
                    text = if (userAccount.isLoggedIn) userAccount.name else "Guest Listener",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentWhite
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (userAccount.isLoggedIn && !userAccount.email.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = userAccount.email,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            color = TextSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Account status pill
                Surface(
                    color = if (userAccount.isLoggedIn) Color(0xFF1B5E20).copy(alpha = 0.35f) else SurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (userAccount.isLoggedIn) Color(0xFF4CAF50).copy(alpha = 0.5f) else AccentWhite.copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (userAccount.isLoggedIn) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                            contentDescription = null,
                            tint = if (userAccount.isLoggedIn) Color(0xFF81C784) else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (userAccount.isLoggedIn) "YouTube Music Linked" else "Local Session",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (userAccount.isLoggedIn) Color(0xFFC8E6C9) else TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Account Features List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceElevated.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AccountFeatureItem(
                        icon = Icons.Rounded.QueueMusic,
                        title = "Smart Queue & Infinity Radio",
                        subtitle = "Personalized dynamic autoplay"
                    )
                    AccountFeatureItem(
                        icon = Icons.Rounded.Headphones,
                        title = "LofiQ Lossless 320kbps",
                        subtitle = "Direct stream resolution"
                    )
                    AccountFeatureItem(
                        icon = Icons.Rounded.Favorite,
                        title = "Library & Liked Songs Sync",
                        subtitle = if (userAccount.isLoggedIn) "Synchronized with your account" else "Sign in to sync your YouTube Music likes"
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Action Buttons
                if (userAccount.isLoggedIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!isAuthenticating) {
                                    haptics.play(Haptic.Tap)
                                    onRefreshProfile()
                                }
                            },
                            enabled = !isAuthenticating,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AccentWhite.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AccentWhite
                            )
                        ) {
                            if (isAuthenticating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = BrandTeal,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Syncing...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Re-sync",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                haptics.play(Haptic.Select)
                                showSignOutConfirm = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE53935).copy(alpha = 0.2f),
                                contentColor = Color(0xFFFF8A80)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sign Out",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    GlassButton(
                        onClick = {
                            haptics.play(Haptic.Select)
                            onStartLogin()
                        },
                        isPrimary = true,
                        accentColor = BrandTangerine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Login,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign in with YouTube Music",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Settings Navigation Button (Liquid Glass Card)
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    preset = GlassPreset.SUBTLE,
                    shape = RoundedCornerShape(14.dp),
                    padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    onClick = {
                        haptics.play(Haptic.Tap)
                        onDismiss()
                        onOpenSettings()
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = AccentWhite,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Settings & Audio DSP",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentWhite,
                                        fontSize = 14.sp
                                    )
                                )
                                Text(
                                    text = "Audio quality, Equalizer, Cache & Theme",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Sign Out Confirmation Alert
                if (showSignOutConfirm) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color(0xFF2C1010),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Sign out from YouTube Music?",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = AccentWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showSignOutConfirm = false }) {
                                    Text("Cancel", color = TextSecondary, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        showSignOutConfirm = false
                                        onSignOut()
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Confirm Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountFeatureItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(AccentWhite.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentWhite
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            )
        }
    }
}
