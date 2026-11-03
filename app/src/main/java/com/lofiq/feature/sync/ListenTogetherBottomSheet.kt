package com.lofiq.feature.sync

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lofiq.ui.haptics.Haptic
import com.lofiq.ui.haptics.rememberHaptics
import com.lofiq.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ListenTogetherMode {
    CHOICE,
    HOST,
    JOIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherBottomSheet(
    syncManager: ListenTogetherManager,
    initialMode: ListenTogetherMode = ListenTogetherMode.CHOICE,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val syncState by syncManager.syncState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val brandColor = LocalBrandColor.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var currentMode by remember(initialMode) { mutableStateOf(initialMode) }
    var hostRoomNameInput by remember { mutableStateOf("My Music Lounge") }
    var joinCodeSuffix by remember { mutableStateOf("") }
    var isCheckingCode by remember { mutableStateOf(false) }
    var foundRoomName by remember { mutableStateOf<String?>(null) }
    var joinErrorMessage by remember { mutableStateOf<String?>(null) }
    var isJoining by remember { mutableStateOf(false) }
    var isCreatingRoom by remember { mutableStateOf(false) }
    var createErrorMessage by remember { mutableStateOf<String?>(null) }

    // Live Room Code Lookup as user enters suffix (locked prefix SRG-)
    LaunchedEffect(joinCodeSuffix) {
        val cleanSuffix = joinCodeSuffix.trim()
        if (cleanSuffix.length >= 4) {
            isCheckingCode = true
            joinErrorMessage = null
            delay(350) // debounce typing
            val fullCode = "SRG-$cleanSuffix"
            val (found, name, errorHint) = syncManager.lookupRoom(fullCode)
            isCheckingCode = false
            if (found) {
                foundRoomName = name
                joinErrorMessage = null
            } else {
                foundRoomName = null
                joinErrorMessage = errorHint ?: "No active room found with code $fullCode"
            }
        } else {
            isCheckingCode = false
            foundRoomName = null
            joinErrorMessage = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101010),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = syncState) {
                is ListenTogetherState.Disconnected -> {
                    AnimatedContent(
                        targetState = currentMode,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                        },
                        label = "ListenTogetherModeAnim"
                    ) { mode ->
                        when (mode) {
                            ListenTogetherMode.CHOICE -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Top Header Badge
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Podcasts,
                                            contentDescription = null,
                                            tint = AccentWhite,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "Listen Together",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = AccentWhite,
                                            fontSize = 22.sp
                                        )
                                    )

                                    Text(
                                        text = "Real-time synchronized music streaming",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary,
                                            fontSize = 13.sp
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // Option 1: Host a Room Card
                                    Surface(
                                        onClick = {
                                            haptics.play(Haptic.Tap)
                                            currentMode = ListenTogetherMode.HOST
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color.White.copy(alpha = 0.05f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            Color.White.copy(alpha = 0.14f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(18.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color.White.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Podcasts,
                                                    contentDescription = null,
                                                    tint = AccentWhite,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(16.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Host a Room",
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = AccentWhite,
                                                        fontSize = 16.sp
                                                    )
                                                )
                                                Text(
                                                    text = "Broadcast your queue live to friends",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = TextSecondary,
                                                        fontSize = 12.sp
                                                    )
                                                )
                                            }

                                            Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = null,
                                                tint = Color(0xFF888888),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Option 2: Join a Room Card
                                    Surface(
                                        onClick = {
                                            haptics.play(Haptic.Tap)
                                            currentMode = ListenTogetherMode.JOIN
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color.White.copy(alpha = 0.05f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            Color.White.copy(alpha = 0.14f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(18.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color.White.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Headphones,
                                                    contentDescription = null,
                                                    tint = AccentWhite,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(16.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Join a Room",
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = AccentWhite,
                                                        fontSize = 16.sp
                                                    )
                                                )
                                                Text(
                                                    text = "Enter 4-digit code to sync playback",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = TextSecondary,
                                                        fontSize = 12.sp
                                                    )
                                                )
                                            }

                                            Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = null,
                                                tint = Color(0xFF888888),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            ListenTogetherMode.HOST -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Top Bar with Back
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                haptics.play(Haptic.Tap)
                                                currentMode = ListenTogetherMode.CHOICE
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                                contentDescription = "Back",
                                                tint = AccentWhite
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Host a Live Room",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = AccentWhite,
                                                fontSize = 18.sp
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Host Card
                                    Surface(
                                        shape = RoundedCornerShape(22.dp),
                                        color = Color.White.copy(alpha = 0.05f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp)
                                        ) {
                                            Text(
                                                text = "Room Name",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))

                                            OutlinedTextField(
                                                value = hostRoomNameInput,
                                                onValueChange = { hostRoomNameInput = it },
                                                placeholder = { Text("e.g. My Music Lounge", color = TextTertiary) },
                                                singleLine = true,
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null, tint = AccentWhite)
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = AccentWhite,
                                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                                    focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                                    focusedTextColor = AccentWhite,
                                                    unfocusedTextColor = AccentWhite
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            if (createErrorMessage != null) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = createErrorMessage ?: "",
                                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFFF5252))
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(20.dp))

                                            Button(
                                                onClick = {
                                                    if (!isCreatingRoom) {
                                                        isCreatingRoom = true
                                                        createErrorMessage = null
                                                        haptics.play(Haptic.Tap)
                                                        syncManager.createRoom(hostRoomNameInput) { success, code, err ->
                                                            isCreatingRoom = false
                                                            if (!success) {
                                                                createErrorMessage = err ?: "Could not create room online"
                                                            }
                                                        }
                                                    }
                                                },
                                                enabled = !isCreatingRoom,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White,
                                                    contentColor = Color.Black,
                                                    disabledContainerColor = Color(0xFF252525),
                                                    disabledContentColor = TextTertiary
                                                ),
                                                shape = RoundedCornerShape(22.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                            ) {
                                                if (isCreatingRoom) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp,
                                                        color = Color.Black
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Creating Room...", fontWeight = FontWeight.Bold, color = Color.Black)
                                                } else {
                                                    Icon(Icons.Rounded.Radio, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Start Hosting Room",
                                                        style = MaterialTheme.typography.titleSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Black
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            ListenTogetherMode.JOIN -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Top Bar with Back
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                haptics.play(Haptic.Tap)
                                                currentMode = ListenTogetherMode.CHOICE
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                                contentDescription = "Back",
                                                tint = AccentWhite
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Join a Live Room",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = AccentWhite,
                                                fontSize = 18.sp
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Join Card
                                    Surface(
                                        shape = RoundedCornerShape(22.dp),
                                        color = Color.White.copy(alpha = 0.05f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp)
                                        ) {
                                            Text(
                                                text = "Enter Room Code",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))

                                            OutlinedTextField(
                                                value = joinCodeSuffix,
                                                onValueChange = { input ->
                                                    val clean = input.uppercase()
                                                        .replace("SRG-", "")
                                                        .replace("SRG", "")
                                                        .replace("-", "")
                                                        .filter { it.isLetterOrDigit() }
                                                        .take(6)
                                                    joinCodeSuffix = clean
                                                },
                                                prefix = {
                                                    Text(
                                                        text = "SRG-",
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = AccentWhite,
                                                            fontSize = 17.sp,
                                                            letterSpacing = 1.sp
                                                        ),
                                                        modifier = Modifier.padding(end = 2.dp)
                                                    )
                                                },
                                                placeholder = {
                                                    Text(
                                                        text = "8821",
                                                        color = TextTertiary,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 17.sp
                                                    )
                                                },
                                                singleLine = true,
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = AccentWhite)
                                                },
                                                trailingIcon = {
                                                    if (isCheckingCode) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(18.dp),
                                                            strokeWidth = 2.dp,
                                                            color = AccentWhite
                                                        )
                                                    } else if (foundRoomName != null) {
                                                        Icon(
                                                            Icons.Rounded.CheckCircle,
                                                            contentDescription = "Room Found",
                                                            tint = Color(0xFF00E676),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                },
                                                keyboardOptions = KeyboardOptions(
                                                    capitalization = KeyboardCapitalization.Characters,
                                                    imeAction = ImeAction.Done
                                                ),
                                                keyboardActions = KeyboardActions(
                                                    onDone = {
                                                        if (joinCodeSuffix.isNotBlank() && !isJoining) {
                                                            isJoining = true
                                                            haptics.play(Haptic.Tap)
                                                            val fullCode = "SRG-$joinCodeSuffix"
                                                            syncManager.joinRoom(fullCode) { success, err ->
                                                                isJoining = false
                                                                if (!success) {
                                                                    joinErrorMessage = err ?: "Could not join room"
                                                                }
                                                            }
                                                        }
                                                    }
                                                ),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = if (foundRoomName != null) Color(0xFF00E676) else AccentWhite,
                                                    unfocusedBorderColor = if (foundRoomName != null) Color(0xFF00E676) else Color.White.copy(alpha = 0.15f),
                                                    focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                                    focusedTextColor = AccentWhite,
                                                    unfocusedTextColor = AccentWhite
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            // Found Room Badge Indicator
                                            if (foundRoomName != null) {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0xFF00E676).copy(alpha = 0.15f))
                                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                                        .fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.CheckCircle,
                                                        contentDescription = null,
                                                        tint = Color(0xFF00E676),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Room Found: $foundRoomName",
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            color = Color(0xFF00E676),
                                                            fontWeight = FontWeight.SemiBold
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            } else if (joinErrorMessage != null) {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    text = joinErrorMessage ?: "",
                                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFFF5252))
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(20.dp))

                                            Button(
                                                onClick = {
                                                    if (joinCodeSuffix.isNotBlank() && !isJoining) {
                                                        isJoining = true
                                                        haptics.play(Haptic.Tap)
                                                        val fullCode = "SRG-$joinCodeSuffix"
                                                        syncManager.joinRoom(fullCode) { success, err ->
                                                            isJoining = false
                                                            if (!success) {
                                                                joinErrorMessage = err ?: "Could not join room"
                                                            }
                                                        }
                                                    }
                                                },
                                                enabled = joinCodeSuffix.isNotBlank() && !isJoining,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White,
                                                    contentColor = Color.Black,
                                                    disabledContainerColor = Color(0xFF252525),
                                                    disabledContentColor = TextTertiary
                                                ),
                                                shape = RoundedCornerShape(22.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                            ) {
                                                if (isJoining) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp,
                                                        color = Color.Black
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Joining Room...", fontWeight = FontWeight.Bold, color = Color.Black)
                                                } else {
                                                    Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Join Live Room",
                                                        style = MaterialTheme.typography.titleSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Black
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }

                is ListenTogetherState.Connected -> {
                    // ==========================================
                    // CONNECTED ACTIVE ROOM VIEW
                    // ==========================================
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (state.isHost) AccentBlue.copy(alpha = 0.5f) else brandColor.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Status Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (state.isHost) AccentBlue.copy(alpha = 0.15f) else brandColor.copy(alpha = 0.15f)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (state.isHost) "👑 YOU ARE THE LIVE HOST" else "🎧 LISTENING TO HOST",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (state.isHost) AccentBlue else brandColor,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.1.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = state.roomName,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Room Code Box with Copy & Share
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(BackgroundBlack)
                                    .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = state.roomCode,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        color = TextPrimary,
                                        letterSpacing = 2.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(state.roomCode))
                                        haptics.play(Haptic.Tick)
                                        Toast.makeText(context, "Room Code copied: ${state.roomCode}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = AccentBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        haptics.play(Haptic.Tick)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Join my live music room '${state.roomName}' on LofiQ!\nRoom Code: ${state.roomCode}"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Room Code"))
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Share,
                                        contentDescription = "Share code",
                                        tint = brandColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Listener Count
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${state.listenerCount} listener(s) in sync",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }

                            // Synced Track Preview
                            if (state.currentSong != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = BackgroundBlack.copy(alpha = 0.6f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        AsyncImage(
                                            model = state.currentSong.artworkUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SurfaceCard)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = state.currentSong.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = state.currentSong.artist,
                                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = if (state.isPlaying) Icons.Rounded.Equalizer else Icons.Rounded.PauseCircle,
                                            contentDescription = null,
                                            tint = if (state.isPlaying) AccentBlue else TextTertiary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Real-time Reaction Emojis
                    Text(
                        text = "Live Reactions",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextTertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("🔥", "❤️", "🎵", "👏", "🎉").forEach { emoji ->
                            IconButton(
                                onClick = {
                                    haptics.play(Haptic.Tick)
                                    syncManager.sendReaction(emoji)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceCard)
                                    .border(1.dp, SurfaceBorder, CircleShape)
                            ) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }
                    }

                    // Display Recent Live Reactions Bubble Stream
                    if (state.recentReactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceCard.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Recent: ",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary)
                            )
                            state.recentReactions.takeLast(6).forEach { r ->
                                Text(
                                    text = "${r.emoji} ",
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Leave / End Room Button
                    OutlinedButton(
                        onClick = {
                            haptics.play(Haptic.Tap)
                            syncManager.leaveRoom()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF5252)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isHost) "End Session" else "Leave Room",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
