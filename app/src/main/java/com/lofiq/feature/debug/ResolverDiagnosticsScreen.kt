package com.lofiq.feature.debug

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.lofiq.core.common.LofiQLogger
import com.lofiq.ui.theme.*

private val StatusGreen = Color(0xFF00E676)
private val StatusRed = Color(0xFFFF5252)
private val StatusYellow = Color(0xFFFFD600)
private val StatusCyan = Color(0xFF00E5FF)
private val CardDarkBg = Color(0xFF141417)
private val CardBorderColor = Color(0xFF282830)
private val TerminalBg = Color(0xFF0A0A0C)

@UnstableApi
@Composable
fun ResolverDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ResolverDiagnosticsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appRuntimeLogs by viewModel.appRuntimeLogs.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    
    // Top-level View Mode: 0 = Diagnostic Test Suite, 1 = App Live Runtime Logs
    var mainViewMode by remember { mutableIntStateOf(0) }
    var selectedSectionIndex by remember { mutableIntStateOf(0) }

    val sectionTabs = listOf(
        "Scorecard",
        "JioSaavn",
        "YouTube",
        "Piped",
        "Lyrics",
        "Pipeline",
        "Media3",
        "Summary",
        "Suite Logs"
    )

    fun copyToClipboard(text: String, label: String) {
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = AccentWhite
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RESOLVER & ENGINE DIAGNOSTICS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentWhite,
                            fontSize = 15.sp,
                            letterSpacing = 0.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (mainViewMode == 0) {
                            if (uiState.isRunning) uiState.currentStep else if (uiState.runCount > 0) "Run #${uiState.runCount} Complete" else "Diagnostic Suite Ready"
                        } else {
                            "Live App Background Stream • ${appRuntimeLogs.size} events captured"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (uiState.isRunning) StatusCyan else TextSecondary,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Primary Mode Selector: [ Diagnostic Test Suite | Live App Runtime Logs ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceElevated)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { mainViewMode = 0 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mainViewMode == 0) BrandPink else Color.Transparent,
                        contentColor = AccentWhite
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Suite", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = { mainViewMode = 1 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mainViewMode == 1) BrandPink else Color.Transparent,
                        contentColor = AccentWhite
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Live App Logs", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            if (mainViewMode == 0) {
                // ==========================================
                // VIEW 1: DIAGNOSTIC TEST SUITE
                // ==========================================

                // Action Bar (Run All, Copy Full)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.runAllTests() },
                        enabled = !uiState.isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentWhite,
                            contentColor = BackgroundBlack
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        if (uiState.isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = BackgroundBlack,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Engines...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (uiState.runCount == 0) "Run All Engine Tests" else "Re-Run All Tests", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (uiState.latestResult != null) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(uiState.latestResult?.finalReportText.orEmpty(), "Full Diagnostic Report")
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentWhite),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Full", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Custom Track Test Runner
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Test Single Custom Song", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = uiState.customTitleInput,
                                onValueChange = { viewModel.updateCustomTitle(it) },
                                placeholder = { Text("Song Title", fontSize = 12.sp, color = TextTertiary) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = AccentWhite, fontSize = 12.sp),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BrandPink,
                                    unfocusedBorderColor = CardBorderColor
                                )
                            )
                            OutlinedTextField(
                                value = uiState.customArtistInput,
                                onValueChange = { viewModel.updateCustomArtist(it) },
                                placeholder = { Text("Artist", fontSize = 12.sp, color = TextTertiary) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = AccentWhite, fontSize = 12.sp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BrandPink,
                                    unfocusedBorderColor = CardBorderColor
                                )
                            )
                            Button(
                                onClick = { viewModel.runCustomSongTest() },
                                enabled = !uiState.isRunning && uiState.customTitleInput.isNotBlank(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPink, contentColor = AccentWhite),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Test", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Section Filter Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedSectionIndex,
                    edgePadding = 16.dp,
                    containerColor = BackgroundBlack,
                    contentColor = AccentWhite,
                    divider = {}
                ) {
                    sectionTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedSectionIndex == index,
                            onClick = { selectedSectionIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedSectionIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedSectionIndex == index) AccentWhite else TextSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Section Content
                Box(modifier = Modifier.fillMaxSize()) {
                    val result = uiState.latestResult
                    if (result == null && !uiState.isRunning) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.BugReport,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Tap 'Run All Engine Tests' to execute diagnostics",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                                )
                            }
                        }
                    } else if (result != null) {
                        when (selectedSectionIndex) {
                            0 -> ScorecardSection(result.scorecards, onCopy = { copyToClipboard(it, "Scorecard") })
                            1 -> JioSaavnDiagnosticsSection(result.jioSaavnDiagnostics, onCopy = { copyToClipboard(it, "JioSaavn Diagnostics") })
                            2 -> YouTubeDiagnosticsSection(result.youTubeDiagnostics, onCopy = { copyToClipboard(it, "YouTube Diagnostics") })
                            3 -> PipedDiagnosticsSection(result.pipedDiagnostics, onCopy = { copyToClipboard(it, "Piped Diagnostics") })
                            4 -> LyricsDiagnosticsSection(result.lyricsDiagnostics, onCopy = { copyToClipboard(it, "Lyrics Diagnostics") })
                            5 -> PipelineDiagnosticsSection(result.pipelineDiagnostics, onCopy = { copyToClipboard(it, "Pipeline Diagnostics") })
                            6 -> Media3DiagnosticsSection(result.media3Diagnostics, onCopy = { copyToClipboard(it, "Media3 Diagnostics") })
                            7 -> SummaryReportSection(result.finalReportText, onCopy = { copyToClipboard(it, "Summary Report") })
                            8 -> SuiteLogsSection(uiState.liveLogs, onCopy = { copyToClipboard(it, "Suite Logs") })
                        }
                    } else {
                        SuiteLogsSection(uiState.liveLogs, onCopy = { copyToClipboard(it, "Suite Logs") })
                    }
                }

            } else {
                // ==========================================
                // VIEW 2: APP RUNTIME LIVE LOGS
                // ==========================================
                AppRuntimeLogsView(
                    logs = appRuntimeLogs,
                    searchQuery = uiState.appLogSearchQuery,
                    selectedCategory = uiState.selectedCategoryFilter,
                    onlyErrors = uiState.onlyErrorsFilter,
                    onSearchChange = { viewModel.updateAppLogSearchQuery(it) },
                    onCategorySelect = { viewModel.setCategoryFilter(it) },
                    onToggleErrors = { viewModel.toggleOnlyErrors(it) },
                    onClearLogs = { viewModel.clearAppRuntimeLogs() },
                    onCopyText = { text, label -> copyToClipboard(text, label) }
                )
            }
        }
    }
}

@Composable
private fun ScorecardSection(
    scorecards: List<ProviderScorecardRow>,
    onCopy: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PROVIDER SCORECARD SUMMARY",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AccentWhite)
                )
                OutlinedButton(
                    onClick = {
                        val text = scorecards.joinToString("\n\n") {
                            "${it.provider} [${it.healthSummary}]\nSearch: ${it.searchPassRate} | Meta: ${it.metadataPassRate} | Stream: ${it.streamPassRate} | Playback: ${it.playbackPassRate} | Avg: ${it.avgLatencyMs}ms"
                        }
                        onCopy(text)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Scorecard", fontSize = 11.sp)
                }
            }
        }
        items(scorecards) { card ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = card.provider,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentWhite,
                                fontSize = 14.sp
                            )
                        )
                        BadgeView(
                            text = card.healthSummary,
                            isPass = card.healthSummary.contains("HEALTHY") || card.healthSummary.contains("ROBUST") || card.healthSummary.contains("WORKING")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricColumn(label = "Search", value = card.searchPassRate)
                        MetricColumn(label = "Metadata", value = card.metadataPassRate)
                        MetricColumn(label = "Stream", value = card.streamPassRate)
                        MetricColumn(label = "Playback", value = card.playbackPassRate)
                        MetricColumn(label = "Avg Latency", value = "${card.avgLatencyMs}ms")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary, fontSize = 10.sp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(color = AccentWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun YouTubeDiagnosticsSection(
    diagnostics: List<YouTubeSongDiagnostic>,
    onCopy: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "YOUTUBE INNERTUBE ISOLATED",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AccentWhite)
                )
                OutlinedButton(
                    onClick = {
                        val text = diagnostics.joinToString("\n\n") { diag ->
                            "Track: ${diag.track.title} - ${diag.track.artist}\nStatus: ${diag.overallStatus.name}\nSearch: ${diag.searchDurationMs}ms (HTTP ${diag.searchHttpStatus}, Results: ${diag.searchResultCount}, VideoId: ${diag.matchedVideoId})\nFailures: ${diag.primaryFailureCategory.name} - ${diag.failureMessage}\nClients: ${diag.clientResults.joinToString { "${it.clientName}: ${it.failureCategory.name} (${it.latencyMs}ms, HTTP ${it.httpStatus})" }}"
                        }
                        onCopy(text)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy YouTube", fontSize = 11.sp)
                }
            }
        }
        items(diagnostics) { diag ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${diag.track.title} — ${diag.track.artist}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = AccentWhite, fontSize = 13.5.sp)
                            )
                            Text(
                                text = "Search: ${diag.searchDurationMs}ms | HTTP ${diag.searchHttpStatus} | Results: ${diag.searchResultCount} | VideoId: ${diag.matchedVideoId ?: "NONE"}",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                            )
                        }
                        BadgeView(
                            text = diag.overallStatus.name,
                            isPass = diag.overallStatus == DiagnosticStatus.PASS
                        )
                    }

                    if (diag.primaryFailureCategory != FailureCategory.NONE) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Failure: ${diag.primaryFailureCategory.name} - ${diag.failureMessage.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall.copy(color = StatusRed, fontSize = 11.sp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("InnerTube Clients Tested:", style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))

                    diag.clientResults.forEach { client ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1B1B20))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = client.clientName,
                                    style = MaterialTheme.typography.bodySmall.copy(color = AccentWhite, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                                )
                                Text(
                                    text = "Status: ${client.playabilityStatus ?: "UNKNOWN"} | HTTP ${client.httpStatus} | ${client.latencyMs}ms | Usable Formats: ${client.usableAudioFormatsCount}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.5.sp)
                                )
                            }
                            BadgeView(text = if (client.hasDirectUrl) "PASS" else "FAIL", isPass = client.hasDirectUrl)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JioSaavnDiagnosticsSection(
    diagnostics: List<JioSaavnSongDiagnostic>,
    onCopy: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "JIOSAAVN DIRECT RESOLVER",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AccentWhite)
                )
                OutlinedButton(
                    onClick = {
                        val text = diagnostics.joinToString("\n\n") { diag ->
                            "Track: ${diag.track.title} - ${diag.track.artist}\nStatus: ${diag.overallStatus.name}\nSearch: HTTP ${diag.searchHttpStatus} (Candidates: ${diag.searchCandidateCount}, Score: ${diag.matchScore}, Match: ${diag.matchReason})\nDecrypted 320k Available: ${diag.decryptedUrlAvailable} (Host: ${diag.cdnHost}, ${diag.bitrateKbps}kbps)\nValidation Pass: ${diag.streamValidationPass} (${diag.latencyMs}ms)"
                        }
                        onCopy(text)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy JioSaavn", fontSize = 11.sp)
                }
            }
        }
        items(diagnostics) { diag ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${diag.track.title} — ${diag.track.artist}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = AccentWhite, fontSize = 13.5.sp)
                            )
                            Text(
                                text = "Search Candidates: ${diag.searchCandidateCount} | Match Score: ${diag.matchScore} | ${diag.matchReason}",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                            )
                        }
                        BadgeView(text = diag.overallStatus.name, isPass = diag.overallStatus == DiagnosticStatus.PASS)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Decrypted 320kbps Available: ${diag.decryptedUrlAvailable} (CDN: ${diag.cdnHost ?: "N/A"})",
                        style = MaterialTheme.typography.bodySmall.copy(color = StatusCyan, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Validation: ${if (diag.streamValidationPass) "PASS" else "FAIL"} | Duration: ${diag.latencyMs}ms | Bitrate: ${diag.bitrateKbps}kbps",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.5.sp)
                    )
                }
            }
        }
    }
}



@Composable
private fun PipedDiagnosticsSection(
    diagnostics: List<PipedInstanceDiagnostic>,
    onCopy: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PIPED MIRRORS",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AccentWhite)
                )
                OutlinedButton(
                    onClick = {
                        val text = diagnostics.joinToString("\n") {
                            "${it.instanceUrl}: ${if (it.isHealthy) "HEALTHY" else "DOWN"} (HTTP ${it.httpStatus}, ${it.latencyMs}ms, Results: ${it.resultCount})"
                        }
                        onCopy(text)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Piped", fontSize = 11.sp)
                }
            }
        }
        items(diagnostics) { diag ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = diag.instanceUrl,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = AccentWhite, fontSize = 13.sp)
                        )
                        Text(
                            text = "HTTP ${diag.httpStatus} | ${diag.latencyMs}ms | Results: ${diag.resultCount}",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                        )
                    }
                    BadgeView(text = if (diag.isHealthy) "HEALTHY" else "DOWN", isPass = diag.isHealthy)
                }
            }
        }
    }
}

@Composable
private fun LyricsDiagnosticsSection(
    diagnostics: List<LyricsSongDiagnostic>,
    onCopy: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LYRICS PROVIDERS (LRCLIB & KUGOU)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AccentWhite)
                )
                OutlinedButton(
                    onClick = {
                        val text = diagnostics.joinToString("\n\n") {
                            "Track: ${it.track.title}\nLRCLIB: Matched=${it.lrclibMatched} (${it.lrclibLatencyMs}ms, Synced=${it.lrclibHasSynced})\nKugou: Matched=${it.kugouMatched} (${it.kugouLatencyMs}ms, LRC=${it.kugouHasLrc})"
                        }
                        onCopy(text)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Lyrics", fontSize = 11.sp)
                }
            }
        }
        items(diagnostics) { diag ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "${diag.track.title} — ${diag.track.artist}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = AccentWhite, fontSize = 13.5.sp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "LRCLIB: ${if (diag.lrclibMatched) "MATCHED" else "NO MATCH"} (${diag.lrclibLatencyMs}ms, Synced=${diag.lrclibHasSynced})",
                        style = MaterialTheme.typography.bodySmall.copy(color = if (diag.lrclibMatched) StatusGreen else StatusYellow, fontSize = 11.sp)
                    )
                    Text(
                        text = "Kugou: ${if (diag.kugouMatched) "MATCHED" else "NO MATCH"} (${diag.kugouLatencyMs}ms, LRC=${diag.kugouHasLrc})",
                        style = MaterialTheme.typography.bodySmall.copy(color = if (diag.kugouMatched) StatusGreen else StatusYellow, fontSize = 11.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineDiagnosticsSection(
    diagnostics: List<PipelineSongDiagnostic>,
    onCopy: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "FULL RESOLVER FALLBACK PIPELINE",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AccentWhite)
                )
                OutlinedButton(
                    onClick = {
                        val text = diagnostics.joinToString("\n\n") {
                            "Track: ${it.track.title}\nStatus: ${it.overallStatus.name}\nWinning Provider: ${it.winningProvider}\nDuration: ${it.resolutionTimeMs}ms\nStream Validated: ${it.streamValidated}\nOutcome: ${it.outcome}"
                        }
                        onCopy(text)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Pipeline", fontSize = 11.sp)
                }
            }
        }
        items(diagnostics) { diag ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${diag.track.title} — ${diag.track.artist}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = AccentWhite, fontSize = 13.5.sp)
                            )
                            Text(
                                text = "Provider: ${diag.winningProvider ?: "NONE"} | ${diag.resolutionTimeMs}ms | Validated: ${diag.streamValidated}",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                            )
                        }
                        BadgeView(text = diag.overallStatus.name, isPass = diag.overallStatus == DiagnosticStatus.PASS)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Outcome: ${diag.outcome}",
                        style = MaterialTheme.typography.bodySmall.copy(color = StatusCyan, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun Media3DiagnosticsSection(
    diagnostics: List<Media3PlaybackDiagnostic>,
    onCopy: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "MEDIA3 PHYSICAL PLAYBACK",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AccentWhite)
                )
                OutlinedButton(
                    onClick = {
                        val text = diagnostics.joinToString("\n\n") {
                            "Track: ${it.track.title}\nStatus: ${it.overallStatus.name}\nPrepare Duration: ${it.prepareDurationMs}ms\nPlayback Started: ${it.playbackStarted}\nPlayer Error: ${it.playerError}"
                        }
                        onCopy(text)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Media3", fontSize = 11.sp)
                }
            }
        }
        items(diagnostics) { diag ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${diag.track.title} — ${diag.track.artist}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = AccentWhite, fontSize = 13.5.sp)
                        )
                        Text(
                            text = "Prepare: ${diag.prepareDurationMs}ms | Reached Ready: ${diag.reachedReady} | Started: ${diag.playbackStarted}",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                        )
                    }
                    BadgeView(text = diag.overallStatus.name, isPass = diag.overallStatus == DiagnosticStatus.PASS)
                }
            }
        }
    }
}

@Composable
private fun SummaryReportSection(
    reportText: String,
    onCopy: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "EXECUTIVE SUMMARY REPORT",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AccentWhite)
            )
            OutlinedButton(
                onClick = { onCopy(reportText) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy Summary", fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(TerminalBg)
                .border(1.dp, CardBorderColor, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = reportText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFD4D4D8),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SuiteLogsSection(
    logs: List<String>,
    onCopy: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TEST SUITE EXECUTION LOGS (${logs.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AccentWhite)
            )
            OutlinedButton(
                onClick = { onCopy(logs.joinToString("\n")) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy Suite Logs", fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(TerminalBg)
                .border(1.dp, CardBorderColor, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logs) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = if (line.contains("FAIL") || line.contains("ERROR")) StatusRed else if (line.contains("PASS") || line.contains("OK")) StatusGreen else Color(0xFFA1A1AA),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCopy(line) }
                            .padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRuntimeLogsView(
    logs: List<LofiQLogger.LogEntry>,
    searchQuery: String,
    selectedCategory: LofiQLogger.Category?,
    onlyErrors: Boolean,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (LofiQLogger.Category?) -> Unit,
    onToggleErrors: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onCopyText: (String, String) -> Unit
) {
    val filteredLogs = remember(logs, searchQuery, selectedCategory, onlyErrors) {
        logs.filter { entry ->
            val matchesCategory = selectedCategory == null || entry.category == selectedCategory
            val matchesError = !onlyErrors || entry.level == LofiQLogger.Level.ERROR || entry.level == LofiQLogger.Level.WARN
            val matchesSearch = searchQuery.isBlank() || entry.message.contains(searchQuery, ignoreCase = true) || entry.category.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesError && matchesSearch
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Search & Controls Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search runtime logs...", fontSize = 11.5.sp, color = TextTertiary) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp)) },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = AccentWhite, fontSize = 11.5.sp),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandPink,
                    unfocusedBorderColor = CardBorderColor
                )
            )

            IconButton(
                onClick = {
                    val fullLogText = filteredLogs.joinToString("\n") { it.formattedText() }
                    onCopyText(fullLogText, "All Filtered Runtime Logs")
                }
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Logs", tint = AccentWhite, modifier = Modifier.size(20.dp))
            }

            IconButton(onClick = onClearLogs) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear Logs", tint = StatusRed, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null && !onlyErrors,
                onClick = {
                    onCategorySelect(null)
                    onToggleErrors(false)
                },
                label = { Text("ALL", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
            )

            FilterChip(
                selected = onlyErrors,
                onClick = { onToggleErrors(!onlyErrors) },
                label = { Text("ERRORS ONLY", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (onlyErrors) StatusRed else TextSecondary) }
            )

            val categories = listOf(
                LofiQLogger.Category.RESOLVER,
                LofiQLogger.Category.PROVIDER,
                LofiQLogger.Category.PLAYBACK,
                LofiQLogger.Category.NETWORK,
                LofiQLogger.Category.CACHE,
                LofiQLogger.Category.SEARCH
            )

            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onCategorySelect(if (selectedCategory == cat) null else cat) },
                    label = { Text(cat.name, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Live Log Terminal Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(TerminalBg)
                .border(1.dp, CardBorderColor, RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No runtime logs captured yet.\nPlay songs in the app to observe live resolution & playback events.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary, fontSize = 11.5.sp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        val color = when (entry.level) {
                            LofiQLogger.Level.ERROR -> StatusRed
                            LofiQLogger.Level.WARN -> StatusYellow
                            LofiQLogger.Level.INFO -> StatusCyan
                            LofiQLogger.Level.DEBUG -> Color(0xFFA1A1AA)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCopyText(entry.formattedText(), "Log Entry")
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "[${entry.timestamp}]",
                                color = TextTertiary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = entry.category.prefix,
                                color = BrandPink,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = entry.message,
                                color = color,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeView(text: String, isPass: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isPass) StatusGreen.copy(alpha = 0.18f) else StatusRed.copy(alpha = 0.18f)
    ) {
        Text(
            text = text,
            color = if (isPass) StatusGreen else StatusRed,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
