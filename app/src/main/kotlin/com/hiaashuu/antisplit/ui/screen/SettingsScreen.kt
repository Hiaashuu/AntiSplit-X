package com.hiaashuu.antisplit.ui.screen

import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.hiaashuu.antisplit.data.OutputDirMode
import com.hiaashuu.antisplit.data.SigningMode
import com.hiaashuu.antisplit.data.ThemeMode
import com.hiaashuu.antisplit.util.FileUtil
import com.hiaashuu.antisplit.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController : NavController,
    viewModel     : MainViewModel
) {
    val context        = LocalContext.current
    val scrollState    = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val themeMode           by viewModel.themeMode.collectAsStateWithLifecycle()
    val signApk             by viewModel.signApkFlow.collectAsStateWithLifecycle(true)
    val signingMode         by viewModel.signingModeFlow.collectAsStateWithLifecycle(SigningMode.DEBUG)
    val keystoreUri         by viewModel.keystoreUriFlow.collectAsStateWithLifecycle("")
    val keystoreAlias       by viewModel.keystoreAliasFlow.collectAsStateWithLifecycle("")
    val pk8Uri              by viewModel.pk8UriFlow.collectAsStateWithLifecycle("")
    val pemUri              by viewModel.pemUriFlow.collectAsStateWithLifecycle("")
    val outputDirMode       by viewModel.outputDirModeFlow.collectAsStateWithLifecycle(OutputDirMode.DOWNLOADS)
    val customOutputDir     by viewModel.customOutputDirFlow.collectAsStateWithLifecycle("")
    val suffix              by viewModel.suffixFlow.collectAsStateWithLifecycle("")
    val autoSelectSplits    by viewModel.autoSelectSplitsFlow.collectAsStateWithLifecycle(false)
    val autoMergeState      by viewModel.autoMergeFlow.collectAsStateWithLifecycle(false)
    val logUiStyleState     by viewModel.logUiStyleFlow.collectAsStateWithLifecycle("DIALOG")
    val compressionLevelState by viewModel.compressionLevelFlow.collectAsStateWithLifecycle("DEFAULT")
    val removeLicenseCheck  by viewModel.removeLicenseCheckFlow.collectAsStateWithLifecycle(false)
    val backupBeforeUninstall by viewModel.backupBeforeUninstallFlow.collectAsStateWithLifecycle(true)

    var showThemeDialog       by remember { mutableStateOf(false) }
    var showSigningDialog     by remember { mutableStateOf(false) }
    var showOutputDirDialog   by remember { mutableStateOf(false) }
    var showSuffixDialog      by remember { mutableStateOf(false) }
    var showCompressionDialog by remember { mutableStateOf(false) }
    var showLogStyleDialog    by remember { mutableStateOf(false) }

    var showKeystoreDialog by remember { mutableStateOf(false) }
    var tempUri            by remember { mutableStateOf<Uri?>(null) }
    var aliasDraft         by remember { mutableStateOf("") }
    var ksPassDraft        by remember { mutableStateOf("") }
    var keyPassDraft       by remember { mutableStateOf("") }
    var verifyLoading      by remember { mutableStateOf(false) }

    val packageInfo = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "Unknown"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode?.toString() ?: "0"
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toString() ?: "0"
    }

    val keystorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            tempUri = it; aliasDraft = ""; ksPassDraft = ""; keyPassDraft = ""; showKeystoreDialog = true
        }
    }
    val pk8Picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            viewModel.setPk8Uri(it.toString())
            viewModel.setSigningMode(SigningMode.KEY_FILES)
        }
    }
    val pemPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            viewModel.setPemUri(it.toString())
            viewModel.setSigningMode(SigningMode.KEY_FILES)
        }
    }
    val customDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {}
            val path = uri.path ?: ""
            val resolvedPath = when {
                path.startsWith("/tree/primary:") -> {
                    "/storage/emulated/0/${path.substringAfter("/tree/primary:")}"
                }
                path.startsWith("/tree/") && path.contains(":") -> {
                    val segment = path.substringAfter("/tree/")
                    val parts = segment.split(":")
                    "/storage/${parts[0]}/${parts[1]}"
                }
                else -> ""
            }
            if (resolvedPath.isNotBlank()) {
                viewModel.setCustomOutputDir(resolvedPath)
                Toast.makeText(context, "Custom folder set. Files will always save here.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Could not resolve physical path.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            GitHubCreditCard(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Hiaashuu/AntiSplit-X"))
                context.startActivity(intent)
            })

            CategoryHeader("ABOUT & UPDATES")
            SettingsCard {
                SettingsItem(
                    icon     = Icons.Filled.Info,
                    title    = "AntiSplit-X Version",
                    subtitle = "$versionName (Build $versionCode)"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SettingsItem(
                    icon     = Icons.Filled.SystemUpdate,
                    title    = "Check for Updates",
                    subtitle = "View the latest releases on GitHub",
                    onClick  = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Hiaashuu/AntiSplit-X/releases"))
                        context.startActivity(intent)
                    }
                )
            }

            CategoryHeader("PREFERENCES")
            SettingsCard {
                SettingsItem(
                    icon     = Icons.Filled.Palette,
                    title    = "Appearance",
                    subtitle = themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                    onClick  = { showThemeDialog = true }
                )
            }

            CategoryHeader("SIGNING")
            SettingsCard {
                SettingsItem(
                    icon     = Icons.Filled.Security,
                    title    = "Sign Output APK",
                    subtitle = "Applies V1+V2 signature to merged APK",
                    trailing = { Switch(checked = signApk, onCheckedChange = { viewModel.setSignApk(it) }) }
                )
                if (signApk) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    val signingSubtitle = when (signingMode) {
                        SigningMode.DEBUG    -> "Default Debug Keystore"
                        SigningMode.KEYSTORE -> "Custom Keystore (.jks, .p12)"
                        SigningMode.KEY_FILES -> "Custom Keys (.pk8, .pem, .key)"
                    }
                    SettingsItem(
                        icon     = Icons.Filled.VpnKey,
                        title    = "Signing Method",
                        subtitle = signingSubtitle,
                        onClick  = { showSigningDialog = true }
                    )
                    AnimatedVisibility(signingMode == SigningMode.KEYSTORE) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            val isKsSelected = keystoreAlias.isNotBlank() && keystoreUri.isNotBlank()
                            val ksName = if (keystoreUri.isNotBlank()) FileUtil.getFileName(context, Uri.parse(keystoreUri)) else ""
                            SettingsItem(
                                icon     = Icons.Filled.Lock,
                                title    = if (isKsSelected) "Keystore: $ksName" else "Select Keystore File",
                                subtitle = if (isKsSelected) "Alias: $keystoreAlias (Tap to change)" else "Tap to browse for .jks or .p12",
                                onClick  = { keystorePicker.launch(arrayOf("*/*")) }
                            )
                        }
                    }
                    AnimatedVisibility(signingMode == SigningMode.KEY_FILES) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            val pk8Name = if (pk8Uri.isNotBlank()) FileUtil.getFileName(context, Uri.parse(pk8Uri)) else ""
                            SettingsItem(
                                icon     = Icons.Filled.Key,
                                title    = if (pk8Name.isNotBlank()) "Private Key: $pk8Name" else "Select Private Key (.pk8, .key)",
                                subtitle = if (pk8Name.isNotBlank()) "Tap to change file" else "Tap to browse",
                                onClick  = { pk8Picker.launch(arrayOf("*/*")) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            val pemName = if (pemUri.isNotBlank()) FileUtil.getFileName(context, Uri.parse(pemUri)) else ""
                            SettingsItem(
                                icon     = Icons.Filled.WorkspacePremium,
                                title    = if (pemName.isNotBlank()) "Certificate: $pemName" else "Select Certificate (.pem)",
                                subtitle = if (pemName.isNotBlank()) "Tap to change file" else "Tap to browse",
                                onClick  = { pemPicker.launch(arrayOf("*/*")) }
                            )
                        }
                    }
                }
            }

            CategoryHeader("PROCESS & OUTPUT")
            SettingsCard {
                SettingsItem(
                    icon     = Icons.Filled.AutoMode,
                    title    = "Auto Merge",
                    subtitle = "Merge instantly when file is picked",
                    trailing = { Switch(checked = autoMergeState, onCheckedChange = { viewModel.setAutoMerge(it) }) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                SettingsItem(
                    icon     = Icons.Filled.Checklist,
                    title    = "Auto-Select Device Splits",
                    subtitle = "Pre-select compatible splits automatically",
                    trailing = { Switch(checked = autoSelectSplits, onCheckedChange = { viewModel.setAutoSelectSplits(it) }) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                val outputDirLabel = when (outputDirMode) {
                    OutputDirMode.SAME_AS_SOURCE -> "Same as Source File"
                    OutputDirMode.DOWNLOADS      -> "AntiSplit Default Folder"
                    OutputDirMode.CUSTOM         -> "Custom Directory"
                    OutputDirMode.ASK_EACH_TIME  -> "Ask Each Time"
                }
                SettingsItem(
                    icon     = Icons.Filled.Folder,
                    title    = "Output Directory",
                    subtitle = outputDirLabel,
                    onClick  = { showOutputDirDialog = true }
                )

                AnimatedVisibility(outputDirMode == OutputDirMode.CUSTOM) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SettingsItem(
                            icon     = Icons.Filled.CreateNewFolder,
                            title    = "Custom Path",
                            subtitle = if (customOutputDir.isNotBlank()) customOutputDir else "Tap to set — will always save here",
                            onClick  = { customDirPicker.launch(null) }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                SettingsItem(
                    icon     = Icons.Filled.TextFields,
                    title    = "Filename Suffix",
                    subtitle = suffix.ifBlank { "None" },
                    onClick  = { showSuffixDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                val compressionSubtitle = when (compressionLevelState) {
                    "STORED"           -> "Fastest — No Compression"
                    "BEST_SPEED"       -> "Speed — Level 1"
                    "BEST_COMPRESSION" -> "Maximum — Level 9 (Smallest)"
                    else               -> "Balanced — Level 6 (Recommended)"
                }
                SettingsItem(
                    icon     = Icons.Filled.Compress,
                    title    = "Compression Level",
                    subtitle = compressionSubtitle,
                    onClick  = { showCompressionDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                SettingsItem(
                    icon     = Icons.Filled.Terminal,
                    title    = "Log Display Style",
                    subtitle = if (logUiStyleState == "INLINE") "On Screen (Terminal)" else "Popup Dialog",
                    onClick  = { showLogStyleDialog = true }
                )
            }

            CategoryHeader("APK MANIPULATION")
            SettingsCard {
                SettingsItem(
                    icon     = Icons.Filled.AdminPanelSettings,
                    title    = "Remove License Check",
                    subtitle = if (removeLicenseCheck) {
                        "Will strip DRM/license entries from AndroidManifest.xml after merge"
                    } else {
                        "Strip CHECK_LICENSE, PairIP & PlayCore entries from merged APK"
                    },
                    trailing = {
                        Switch(
                            checked         = removeLicenseCheck,
                            onCheckedChange = { viewModel.setRemoveLicenseCheck(it) }
                        )
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SettingsItem(
                    icon     = Icons.Filled.Save,
                    title    = "Backup Before Uninstall",
                    subtitle = "Save current installed app as .apks to Downloads/AntiSplit before uninstalling for signature conflict",
                    trailing = {
                        Switch(
                            checked         = backupBeforeUninstall,
                            onCheckedChange = { viewModel.setBackupBeforeUninstall(it) }
                        )
                    }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Appearance") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSigningDialog) {
        AlertDialog(
            onDismissRequest = { showSigningDialog = false },
            title = { Text("Signing Method") },
            text = {
                Column {
                    SigningMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setSigningMode(mode)
                                    showSigningDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = signingMode == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                when (mode) {
                                    SigningMode.DEBUG     -> "Default Debug Keystore"
                                    SigningMode.KEYSTORE  -> "Custom Keystore (.jks, .p12)"
                                    SigningMode.KEY_FILES -> "Custom Keys (.pk8, .pem, .key)"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSigningDialog = false }) { Text("Cancel") } }
        )
    }

    if (showOutputDirDialog) {
        val options = listOf(
            OutputDirMode.DOWNLOADS     to "AntiSplit Default (Downloads/AntiSplit)",
            OutputDirMode.SAME_AS_SOURCE to "Same as Source File (file-based APKs only)",
            OutputDirMode.CUSTOM        to "Custom Directory (always saves here)",
            OutputDirMode.ASK_EACH_TIME to "Ask Each Time"
        )
        AlertDialog(
            onDismissRequest = { showOutputDirDialog = false },
            title = { Text("Output Directory") },
            text = {
                Column {
                    options.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setOutputDirMode(mode)
                                    showOutputDirDialog = false
                                    if (mode == OutputDirMode.CUSTOM && customOutputDir.isBlank()) {
                                        customDirPicker.launch(null)
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = outputDirMode == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showOutputDirDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCompressionDialog) {
        val options = listOf(
            "STORED"           to "⚡ Fastest — No Compression (larger file)",
            "BEST_SPEED"       to "🚀 Speed — Level 1 (minimal compression)",
            "DEFAULT"          to "⚖ Balanced — Level 6 (Recommended)",
            "BEST_COMPRESSION" to "📦 Maximum — Level 9 (smallest file, slower)"
        )
        AlertDialog(
            onDismissRequest = { showCompressionDialog = false },
            title = { Text("Compression Level") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setCompressionLevel(mode)
                                    showCompressionDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = compressionLevelState == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCompressionDialog = false }) { Text("Cancel") } }
        )
    }

    if (showLogStyleDialog) {
        val options = listOf(
            "DIALOG" to "Popup Dialog",
            "INLINE" to "On Screen (Terminal)"
        )
        AlertDialog(
            onDismissRequest = { showLogStyleDialog = false },
            title = { Text("Log Display Style") },
            text = {
                Column {
                    options.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setLogUiStyle(mode)
                                    showLogStyleDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = logUiStyleState == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLogStyleDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSuffixDialog) {
        var suffixInput by remember { mutableStateOf(suffix) }
        AlertDialog(
            onDismissRequest = { showSuffixDialog = false },
            title = { Text("Filename Suffix") },
            text = {
                OutlinedTextField(
                    value         = suffixInput,
                    onValueChange = { suffixInput = it },
                    label         = { Text("Suffix") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSuffix(suffixInput)
                    showSuffixDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSuffixDialog = false }) { Text("Cancel") } }
        )
    }

    if (showKeystoreDialog) {
        Dialog(onDismissRequest = { if (!verifyLoading) showKeystoreDialog = false }) {
            Surface(
                shape  = RoundedCornerShape(20.dp),
                color  = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Keystore Verification", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Enter credentials for: ${FileUtil.getFileName(context, tempUri ?: Uri.EMPTY)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value         = aliasDraft,
                        onValueChange = { aliasDraft = it },
                        label         = { Text("Key Alias") },
                        singleLine    = true,
                        shape         = RoundedCornerShape(12.dp),
                        modifier      = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value                  = ksPassDraft,
                        onValueChange          = { ksPassDraft = it },
                        label                  = { Text("Keystore Password") },
                        singleLine             = true,
                        visualTransformation   = PasswordVisualTransformation(),
                        shape                  = RoundedCornerShape(12.dp),
                        modifier               = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value                  = keyPassDraft,
                        onValueChange          = { keyPassDraft = it },
                        label                  = { Text("Key Password") },
                        singleLine             = true,
                        visualTransformation   = PasswordVisualTransformation(),
                        shape                  = RoundedCornerShape(12.dp),
                        modifier               = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick  = { showKeystoreDialog = false },
                            enabled  = !verifyLoading
                        ) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                verifyLoading = true
                                coroutineScope.launch {
                                    val result = viewModel.verifyAndSaveKeystore(
                                        tempUri.toString(), aliasDraft, ksPassDraft, keyPassDraft
                                    )
                                    verifyLoading = false
                                    if (result == "SUCCESS") {
                                        Toast.makeText(context, "Keystore Validated & Saved!", Toast.LENGTH_SHORT).show()
                                        showKeystoreDialog = false
                                    } else {
                                        Toast.makeText(context, "Error: $result", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !verifyLoading && aliasDraft.isNotBlank(),
                            shape   = RoundedCornerShape(12.dp)
                        ) {
                            if (verifyLoading) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(16.dp),
                                    color       = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Verify & Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text       = title,
        style      = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.primary,
        modifier   = Modifier.padding(start = 24.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape    = RoundedCornerShape(20.dp),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsItem(
    icon     : ImageVector,
    title    : String,
    subtitle : String? = null,
    onClick  : (() -> Unit)? = null,
    trailing : @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun GitHubCreditCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape    = RoundedCornerShape(20.dp),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            contentColor   = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "GitHub",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Support AntiSplit-X", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("Developed by Hiaashuu. Tap to drop a star on GitHub!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}