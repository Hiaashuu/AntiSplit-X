package com.hiaashuu.antisplit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hiaashuu.antisplit.ui.screen.copyLogToClipboard
import com.hiaashuu.antisplit.ui.screen.copyPathToClipboard
import com.hiaashuu.antisplit.ui.screen.shareApk
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ResultDialog(
    outputFile : File,
    wasSigned  : Boolean,
    outputName : String,
    logs       : List<String> = emptyList(),
    onInstallClick: () -> Unit,
    onDismiss  : () -> Unit
) {
    val context    = LocalContext.current
    val sizeMb     = "%.2f".format(outputFile.length() / 1_048_576f)
    val filePath   = outputFile.absolutePath
    val listState  = rememberLazyListState()
    val corScope   = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (logs.isNotEmpty()) {
            corScope.launch { listState.animateScrollToItem(logs.size - 1) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier       = Modifier.fillMaxWidth(0.93f),
            shape          = RoundedCornerShape(24.dp),
            border         = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            tonalElevation = 8.dp,
            color          = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Row(
                    verticalAlignment      = Alignment.CenterVertically,
                    horizontalArrangement  = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(36.dp)
                    )
                    Column {
                        Text(
                            "Merge Complete",
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text  = if (wasSigned) "APK merged & signed ✓" else "APK merged (unsigned)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (wasSigned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("File", outputName)
                    InfoRow("Size", "$sizeMb MB")
                    InfoRow("Path", filePath, maxLines = 3)
                    InfoRow("Signed", if (wasSigned) "Yes ✓" else "No")
                }

                HorizontalDivider()

                if (logs.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1C2033), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment      = Alignment.CenterVertically,
                            horizontalArrangement  = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(Modifier.size(10.dp).background(Color(0xFFFF5F57), CircleShape))
                            Box(Modifier.size(10.dp).background(Color(0xFFFFBD2E), CircleShape))
                            Box(Modifier.size(10.dp).background(Color(0xFF28C840), CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "MERGE LOG",
                                style         = MaterialTheme.typography.labelSmall,
                                color         = Color(0xFF8B949E),
                                fontWeight    = FontWeight.Medium,
                                letterSpacing = 1.2.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .background(
                                    Color(0xFF0D1117),
                                    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                                )
                                .border(
                                    1.dp,
                                    Color(0xFF30363D),
                                    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                                )
                        ) {
                            LazyColumn(
                                state    = listState,
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(logs) { line ->
                                    Text(
                                        text  = line,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = when {
                                            line.contains("ERROR", true) || line.contains("failed", true) -> Color(0xFFFF7B72)
                                            line.contains("✓") || line.contains("Done", true) || line.contains("Signed", true) -> Color(0xFF3FB950)
                                            line.contains("⚠") || line.contains("Warning", true) -> Color(0xFFD2A8FF)
                                            line.startsWith("═") -> Color(0xFF8B949E)
                                            line.startsWith("🔍") || line.contains("Scanning", true) -> Color(0xFF79C0FF)
                                            else -> Color(0xFFC9D1D9)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = { shareApk(context, outputFile) },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share APK")
                    }

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick  = { copyPathToClipboard(context, filePath) },
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Path", style = MaterialTheme.typography.labelMedium)
                        }
                        if (logs.isNotEmpty()) {
                            OutlinedButton(
                                onClick  = { copyLogToClipboard(context, logs) },
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Log", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Button(
                        onClick  = onInstallClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Install APK", fontWeight = FontWeight.SemiBold)
                    }
                }

                TextButton(
                    onClick  = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Merge other file", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, maxLines: Int = 1) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text       = "$label:",
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier   = Modifier.width(50.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text     = value,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}