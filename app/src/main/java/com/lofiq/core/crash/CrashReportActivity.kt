package com.lofiq.core.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lofiq.MainActivity

class CrashReportActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: "Unknown startup exception"
        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "No stack trace recorded"

        setContent {
            CrashReportScreen(
                errorMessage = errorMessage,
                stackTrace = stackTrace,
                onRestart = {
                    val restartIntent = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(restartIntent)
                    finish()
                }
            )
        }
    }
}

@Composable
fun CrashReportScreen(
    errorMessage: String,
    stackTrace: String,
    onRestart: () -> Unit
) {
    val context = LocalContext.current
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val diagnosticInfo = buildString {
        appendLine("--- LOFIQ DIAGNOSTICS ---")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        appendLine("Error: $errorMessage")
        appendLine("\n--- STACK TRACE ---")
        appendLine(stackTrace)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScroll),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.2f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "!",
                            color = Color(0xFFEF4444),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Column {
                    Text(
                        text = "LofiQ Startup Diagnostics",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "The app encountered an unhandled exception",
                        color = Color(0xFF9E9E9E),
                        fontSize = 13.sp
                    )
                }
            }

            // Error summary badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E1E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Exception Message",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            // Device details
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF161616),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.SDK_INT})",
                        color = Color(0xFFB0B0B0),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Stack trace box
            Text(
                text = "Stack Trace Details",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 340.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = stackTrace,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.horizontalScroll(horizontalScroll)
                )
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("LofiQ Crash Log", diagnosticInfo)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Diagnostic info copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Copy Log", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14B8A6)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Restart App", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
