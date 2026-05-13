package com.hiaashuu.antisplit.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.hiaashuu.antisplit.Screen
import com.hiaashuu.antisplit.data.ThemeMode
import com.hiaashuu.antisplit.ui.components.ProcessingDialog
import com.hiaashuu.antisplit.ui.components.ResultDialog
import com.hiaashuu.antisplit.ui.components.SplitSelectionDialog
import com.hiaashuu.antisplit.util.SignUtil
import com.hiaashuu.antisplit.viewmodel.MainViewModel
import com.hiaashuu.antisplit.viewmodel.SplitInfo
import com.hiaashuu.antisplit.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController : NavController,
    viewModel     : MainViewModel,
    pendingUri    : Uri?,
    onUriConsumed : () -> Unit
) {
    val uiState          by viewModel.uiState.collectAsStateWithLifecycle()
    val logs             by viewModel.logs.collectAsStateWithLifecycle()
    val logUiStyle       by viewModel.logUiStyleFlow.collectAsStateWithLifecycle("DIALOG")
    val autoSelectSplits by viewModel.autoSelectSplitsFlow.collectAsStateWithLifecycle(false)
    val themeMode        by viewModel.themeMode.collectAsStateWithLifecycle()
    val backupBeforeUninstall by viewModel.backupBeforeUninstallFlow.collectAsStateWithLifecycle(true)

    val snackbarState  = remember { SnackbarHostState() }
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showSplitDialog by remember { mutableStateOf(false) }
    var pendingSplits   by remember { mutableStateOf<List<SplitInfo>>(emptyList()) }
    var pendingFileName by remember { mutableStateOf("") }

    var hasPromptedForSave by remember { mutableStateOf(false) }
    var pendingSaveFile    by remember { mutableStateOf<File?>(null) }

    var showSignatureConflictDialog by remember { mutableStateOf(false) }
    var appToUninstall by remember { mutableStateOf<Pair<String, File>?>(null) }

    var pendingInstallFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingInstallPkgName  by rememberSaveable { mutableStateOf<String?>(null) }

    var resumeEventCount by remember { mutableIntStateOf(0) }
    var backPressTime by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeEventCount++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumeEventCount) {
        val filePath = pendingInstallFilePath
        val pkgName  = pendingInstallPkgName
        if (resumeEventCount > 0 && filePath != null && pkgName != null) {
            pendingInstallFilePath = null
            pendingInstallPkgName  = null

            val isStillInstalled = withContext(Dispatchers.IO) {
                try {
                    context.packageManager.getPackageInfo(pkgName, 0)
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (!isStillInstalled) {
                installApk(context, File(filePath))
            }
        }
    }

    val showInlineLog = logUiStyle == "INLINE" && (uiState is UiState.Processing || uiState is UiState.Done)

    BackHandler(enabled = showInlineLog) {
        viewModel.cancelMerge()
        viewModel.clearState()
    }

    BackHandler(enabled = !showInlineLog) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressTime < 2000) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressTime = currentTime
            coroutineScope.launch {
                snackbarState.currentSnackbarData?.dismiss()
                snackbarState.showSnackbar(
                    message = "Press back again to exit",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        if (uri != null && pendingSaveFile != null) {
            viewModel.saveFileToUri(pendingSaveFile!!, uri)
        }
    }

    fun handleInstallClick(file: File) {
        val pm = context.packageManager

        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0L))
        } else {
            pm.getPackageArchiveInfo(file.absolutePath, 0)
        }
        val packageName = packageInfo?.packageName

        if (packageName == null) {
            installApk(context, file)
            return
        }

        val installedSignatureHash = SignUtil.getInstalledAppSignatureHash(context, packageName)
        if (installedSignatureHash == null) {
            installApk(context, file)
            return
        }

        val apkSignatureHash = SignUtil.getApkFileSignatureHash(context, file)

        when {
            apkSignatureHash != null && apkSignatureHash == installedSignatureHash -> {
                installApk(context, file)
            }
            apkSignatureHash != null && apkSignatureHash != installedSignatureHash -> {
                appToUninstall = packageName to file
                showSignatureConflictDialog = true
            }
            else -> {
                appToUninstall = packageName to file
                showSignatureConflictDialog = true
            }
        }
    }

    LaunchedEffect(pendingUri) {
        if (pendingUri != null) {
            viewModel.processUri(pendingUri)
            onUriConsumed()
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UiState.Idle -> {
                hasPromptedForSave = false
                pendingSaveFile = null
            }
            is UiState.FileReady -> {
                if (state.showSplitDialog && state.splits.isNotEmpty()) {
                    pendingSplits   = state.splits
                    pendingFileName = state.fileName
                    showSplitDialog = true
                }
            }
            is UiState.Done -> {
                if (state.askEachTime && !hasPromptedForSave) {
                    hasPromptedForSave = true
                    pendingSaveFile = state.outputFile
                    Toast.makeText(context, "Merged successfully, select where to save", Toast.LENGTH_LONG).show()
                    createDocumentLauncher.launch(state.outputName)
                }
            }
            is UiState.Error -> snackbarState.showSnackbar(message = state.message, duration = SnackbarDuration.Long)
            else -> Unit
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.processUri(it) } }

    val onMergeClick = {
        val fileReady = uiState as? UiState.FileReady
        if (fileReady != null) {
            if (fileReady.showSplitDialog && fileReady.splits.isNotEmpty()) {
                pendingSplits   = fileReady.splits
                pendingFileName = fileReady.fileName
                showSplitDialog = true
            } else {
                viewModel.startMerge()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AntiSplit-X",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.setThemeMode(
                                if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
                            )
                        }
                    ) {
                        Icon(
                            imageVector = if (themeMode == ThemeMode.DARK) Icons.Filled.WbSunny else Icons.Filled.Bedtime,
                            contentDescription = if (themeMode == ThemeMode.DARK) "Switch to Light Mode" else "Switch to Dark Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        MainContentView(
            viewModel        = viewModel,
            pickFileLauncher = pickFileLauncher,
            navController    = navController,
            onMergeClick     = onMergeClick,
            onInstallClick   = { handleInstallClick(it) },
            modifier         = Modifier.padding(innerPadding)
        )
    }

    if (showSplitDialog) {
        SplitSelectionDialog(
            fileName  = pendingFileName,
            splits    = pendingSplits,
            autoSelect = autoSelectSplits,
            onConfirm = { selected -> showSplitDialog = false; viewModel.startMerge(selected) },
            onDismiss = { showSplitDialog = false }
        )
    }

    if (showSignatureConflictDialog) {
        var isBackingUp by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isBackingUp) {
                    showSignatureConflictDialog = false
                    appToUninstall = null
                }
            },
            title = { Text("Warning") },
            text = { Text("The signature of the APK is inconsistent with the installed application. Do you want to uninstall the existing version first?") },
            confirmButton = {
                Button(
                    enabled = !isBackingUp,
                    onClick = {
                        val pkgName   = appToUninstall?.first
                        val fileToUse = appToUninstall?.second

                        if (pkgName != null && fileToUse != null) {
                            coroutineScope.launch {
                                if (backupBeforeUninstall) {
                                    isBackingUp = true
                                    viewModel.backupInstalledApp(pkgName)
                                    isBackingUp = false
                                }
                                showSignatureConflictDialog = false
                                appToUninstall = null

                                pendingInstallFilePath = fileToUse.absolutePath
                                pendingInstallPkgName  = pkgName

                                val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkgName"))
                                context.startActivity(intent)
                            }
                        }
                    }
                ) {
                    if (isBackingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Backing up...")
                    } else {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (!isBackingUp) {
                    TextButton(onClick = {
                        showSignatureConflictDialog = false
                        appToUninstall = null
                    }) { Text("Cancel") }
                }
            }
        )
    }

    if (logUiStyle == "DIALOG") {
        (uiState as? UiState.Processing)?.let {
            ProcessingDialog(progress = it.progress, logs = logs, onCancel = { viewModel.cancelMerge() })
        }
        (uiState as? UiState.Done)?.let {
            ResultDialog(
                outputFile = it.outputFile,
                wasSigned = it.wasSigned,
                outputName = it.outputName,
                logs = logs,
                onInstallClick = { handleInstallClick(it.outputFile) },
                onDismiss = { viewModel.clearState() }
            )
        }
    }
}

@Composable
private fun MainContentView(
    viewModel        : MainViewModel,
    pickFileLauncher : androidx.activity.result.ActivityResultLauncher<Array<String>>,
    navController    : NavController,
    onMergeClick     : () -> Unit,
    onInstallClick   : (File) -> Unit,
    modifier         : Modifier = Modifier
) {
    val uiState    by viewModel.uiState.collectAsStateWithLifecycle()
    val logs       by viewModel.logs.collectAsStateWithLifecycle()
    val logUiStyle by viewModel.logUiStyleFlow.collectAsStateWithLifecycle("DIALOG")
    val autoMerge  by viewModel.autoMergeFlow.collectAsStateWithLifecycle(false)

    val isInlineProcessingOrDone = logUiStyle == "INLINE" && (uiState is UiState.Processing || uiState is UiState.Done)

    Crossfade(targetState = isInlineProcessingOrDone, label = "MainViewCrossfade") { isProcessingView ->
        if (isProcessingView) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                InlineLogSection(
                    uiState        = uiState,
                    logs           = logs,
                    viewModel      = viewModel,
                    onInstallClick = onInstallClick,
                    modifier       = Modifier.fillMaxWidth()
                )
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(visible = uiState is UiState.FileReady) {
                    (uiState as? UiState.FileReady)?.let {
                        FileReadyCard(it.fileName, it.splits) { viewModel.clearState() }
                    }
                }
                if (uiState is UiState.FileReady) {
                    Spacer(Modifier.height(16.dp))
                }

                AnimatedVisibility(
                    visible = logUiStyle == "INLINE" && logs.isNotEmpty() && uiState !is UiState.Idle
                ) {
                    LogDisplay(logs = logs, modifier = Modifier.fillMaxWidth().height(240.dp))
                }
                if (logUiStyle == "INLINE" && logs.isNotEmpty() && uiState !is UiState.Idle) {
                    Spacer(Modifier.height(16.dp))
                }

                // ── IMPROVED DropZoneCard ──────────────────────────────────────────
                DropZoneCard(uiState = uiState) { pickFileLauncher.launch(arrayOf("*/*")) }

                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        " or ",
                        Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }
                Spacer(Modifier.height(20.dp))

                // ── IMPROVED "Choose from Installed" Button ────────────────────────
                Button(
                    onClick   = { navController.navigate(Screen.AppList.route) },
                    modifier  = Modifier.fillMaxWidth().height(62.dp),
                    shape     = RoundedCornerShape(18.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    colors    = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Choose from Installed",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                AnimatedVisibility(
                    visible = !autoMerge && uiState is UiState.FileReady,
                    enter   = fadeIn(tween(250)) + expandVertically(),
                    exit    = fadeOut(tween(200)) + shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick  = onMergeClick,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape    = RoundedCornerShape(16.dp),
                            border   = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.CompareArrows, null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Merge Splits → Single APK",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//  IMPROVED DropZoneCard  (uniform card bg — no inner rectangle artifact)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
private fun DropZoneCard(uiState: UiState, onPickFile: () -> Unit) {
    val borderColor by animateColorAsState(
        targetValue = if (uiState is UiState.FileReady)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        animationSpec = tween(300),
        label = "borderColor"
    )

    // One uniform surface — no inner gradient that creates a rectangle artifact
    val cardBg  = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    val chipsBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(28.dp),
        border   = BorderStroke(1.5.dp, borderColor),
        colors   = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Tap area — NO extra background, inherits card colour ──────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickFile() }
                    .padding(top = 32.dp, bottom = 28.dp, start = 20.dp, end = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState is UiState.Loading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(36.dp))
                        Text(
                            "Reading file…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Icon in a soft circle
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(38.dp)
                            )
                        }
                        Text(
                            text       = "Tap to pick a split APK file",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text  = "or share directly to this app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Format chips — slightly tinted strip at bottom of card ────────
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(chipsBg)   // Card clips this to its own rounded corners
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                listOf(".apks", ".xapk", ".apkm", ".zip").forEach { fmt ->
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                RoundedCornerShape(20.dp)
                            )
                            .border(
                                0.8.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text       = fmt,
                            textAlign  = TextAlign.Center,
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogDisplay(logs: List<String>, modifier: Modifier = Modifier) {
    val listState      = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            coroutineScope.launch { listState.animateScrollToItem(logs.size - 1) }
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C2033), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(10.dp).background(Color(0xFFFF5F57), CircleShape))
            Box(Modifier.size(10.dp).background(Color(0xFFFFBD2E), CircleShape))
            Box(Modifier.size(10.dp).background(Color(0xFF28C840), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                text  = "TERMINAL OUTPUT",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8B949E),
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0D1117), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
        ) {
            LazyColumn(
                state    = listState,
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
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

@Composable
private fun InlineLogSection(
    uiState        : UiState,
    logs           : List<String>,
    viewModel      : MainViewModel,
    onInstallClick : (File) -> Unit,
    modifier       : Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LogDisplay(logs = logs, modifier = Modifier.fillMaxWidth().height(380.dp))

        if (uiState is UiState.Processing || uiState is UiState.Loading) {
            val progress = (uiState as? UiState.Processing)?.progress ?: 0f
            LinearProgressIndicator(
                progress  = { if (progress > 0f) progress else 0f },
                modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color     = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            OutlinedButton(
                onClick  = { viewModel.cancelMerge() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp),
                border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Filled.Stop, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Cancel Process", color = MaterialTheme.colorScheme.error)
            }
        } else if (uiState is UiState.Done) {
            val file = uiState.outputFile
            Button(
                onClick  = { shareApk(context, file) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp),
                border   = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    contentColor   = MaterialTheme.colorScheme.primary
                )
            ) { Text("Share APK", fontWeight = FontWeight.SemiBold) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick  = { copyPathToClipboard(context, file.absolutePath) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape    = RoundedCornerShape(16.dp),
                    border   = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) { Text("Copy Path") }
                OutlinedButton(
                    onClick  = { copyLogToClipboard(context, logs) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape    = RoundedCornerShape(16.dp),
                    border   = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) { Text("Copy Log") }
            }
            Button(
                onClick  = { onInstallClick(file) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp),
                border   = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) { Text("Install APK", fontWeight = FontWeight.SemiBold) }

            OutlinedButton(
                onClick  = { viewModel.clearState() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp),
                border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) { Text("Done & Clear", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun FileReadyCard(fileName: String, splits: List<SplitInfo>, onClear: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Filled.Archive,
                    null,
                    tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = fileName,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        text  = "${splits.size} split APK(s) found — ready to merge",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, "Clear file", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            if (splits.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color    = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 110.dp)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(splits, key = { it.name }) { split ->
                        Row {
                            Text(
                                text       = "• ${split.name}",
                                style      = MaterialTheme.typography.labelSmall,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = if (split.isRelevantForDevice) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

fun installApk(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open installer: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun shareApk(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share APK via"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun copyPathToClipboard(context: Context, path: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("APK path", path))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}

fun copyLogToClipboard(context: Context, logs: List<String>) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Merge Log", logs.joinToString("\n")))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}