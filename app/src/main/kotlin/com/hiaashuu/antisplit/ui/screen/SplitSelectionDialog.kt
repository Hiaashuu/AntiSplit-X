package com.hiaashuu.antisplit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hiaashuu.antisplit.viewmodel.SplitInfo

@Composable
fun SplitSelectionDialog(
    fileName   : String,
    splits     : List<SplitInfo>,
    autoSelect : Boolean,
    onConfirm  : (List<String>?) -> Unit,
    onDismiss  : () -> Unit
) {
    val checkedState = remember(splits, autoSelect) {
        mutableStateMapOf<String, Boolean>().also { map ->
            splits.forEach { split -> map[split.name] = if (autoSelect) split.isRelevantForDevice else true }
        }
    }

    val noneSelected = checkedState.values.none { it }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier       = Modifier.fillMaxWidth(0.92f).wrapContentHeight(),
            shape          = RoundedCornerShape(24.dp),
            border         = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            tonalElevation = 6.dp,
            color          = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                Text("Select Splits to Include", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)

                Spacer(Modifier.height(12.dp))

                if (autoSelect) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.DevicesOther, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text("Auto-selected splits matching your device's ABI / density", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { splits.forEach { checkedState[it.name] = true } }) {
                        Icon(Icons.Filled.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("All")
                    }
                    TextButton(onClick = { splits.forEach { checkedState[it.name] = false } }) { Text("None") }
                }

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(splits, key = { it.name }) { split ->
                        val isChecked = checkedState[split.name] ?: false
                        SplitItemRow(split = split, isChecked = isChecked, onToggle = { checkedState[split.name] = !isChecked })
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onConfirm(null) }) { Text("Merge All") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val selected = checkedState.filter { it.value }.keys.toList()
                            onConfirm(selected.ifEmpty { null })
                        },
                        enabled = !noneSelected,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val count = checkedState.values.count { it }
                        Text("Merge ($count)")
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitItemRow(split: SplitInfo, isChecked: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha=0.3f)),
        color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(checked = isChecked, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
            Column(modifier = Modifier.weight(1f)) {
                Text(split.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                if (split.isRelevantForDevice) {
                    Text("Matches this device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}