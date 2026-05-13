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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun ProcessingDialog(
    progress : Float,
    logs     : List<String>,
    onCancel : () -> Unit
) {
    val listState      = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            coroutineScope.launch { listState.animateScrollToItem(logs.size - 1) }
        }
    }

    Dialog(
        onDismissRequest = { },
        properties       = DialogProperties(
            dismissOnBackPress      = false,
            dismissOnClickOutside   = false,
            usePlatformDefaultWidth = false
        )
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                Text(
                    text       = "Terminal Output",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pct = (progress * 100).toInt()
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text  = if (progress < 1f) "Executing process..." else "Finalising...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text       = "$pct%",
                            style      = MaterialTheme.typography.labelMedium,
                            color      = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    LinearProgressIndicator(
                        progress   = { progress },
                        modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color      = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1C2033), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(10.dp).background(Color(0xFFFF5F57), CircleShape))
                        Box(Modifier.size(10.dp).background(Color(0xFFFFBD2E), CircleShape))
                        Box(Modifier.size(10.dp).background(Color(0xFF28C840), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text          = "TERMINAL OUTPUT",
                            style         = MaterialTheme.typography.labelSmall,
                            color         = Color(0xFF8B949E),
                            fontWeight    = FontWeight.Medium,
                            letterSpacing = 1.2.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
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
                            modifier = Modifier.fillMaxSize().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(logs) { line ->
                                val color = when {
                                    line.startsWith("ERROR") || line.contains("failed", ignoreCase = true) -> Color(0xFFFF7B72)
                                    line.startsWith("✓") || line.contains("Done", true) || line.contains("Signed", true) -> Color(0xFF3FB950)
                                    line.startsWith("⚠") || line.contains("Warning", true) -> Color(0xFFD2A8FF)
                                    line.startsWith("═") -> Color(0xFF8B949E)
                                    line.startsWith("🔍") || line.contains("Scanning", true) -> Color(0xFF79C0FF)
                                    else -> Color(0xFFC9D1D9)
                                }
                                Text(
                                    text  = line,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = color
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick  = onCancel,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    shape    = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Abort Process")
                }
            }
        }
    }
}