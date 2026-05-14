package com.hiaashuu.antisplit.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hiaashuu.antisplit.data.OutputDirMode
import com.hiaashuu.antisplit.data.PrefsManager
import com.hiaashuu.antisplit.data.SigningMode
import com.hiaashuu.antisplit.data.ThemeMode
import com.hiaashuu.antisplit.util.DeviceSpecsUtil
import com.hiaashuu.antisplit.util.FileUtil
import com.hiaashuu.antisplit.util.LicenseRemovalUtil
import com.hiaashuu.antisplit.util.MergeResult
import com.hiaashuu.antisplit.util.MergerUtil
import com.hiaashuu.antisplit.util.SignUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

data class SplitInfo(val name: String, val isRelevantForDevice: Boolean)

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class FileReady(val fileName: String, val splits: List<SplitInfo>, val showSplitDialog: Boolean) : UiState()
    data class Processing(val progress: Float = 0f, val isCancellable: Boolean = true) : UiState()
    data class Done(val outputFile: File, val wasSigned: Boolean, val outputName: String, val askEachTime: Boolean = false) : UiState()
    data class Error(val message: String) : UiState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PrefsManager(application)

    val themeMode: StateFlow<ThemeMode> = prefs.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    val signApkFlow: Flow<Boolean> = prefs.signApk
    val showSplitDialogFlow: Flow<Boolean> = prefs.showSplitDialog
    val autoSelectSplitsFlow: Flow<Boolean> = prefs.autoSelectSplits
    val forceMergeFlow: Flow<Boolean> = prefs.forceMerge
    val suffixFlow: Flow<String> = prefs.suffix
    val outputDirModeFlow: Flow<OutputDirMode> = prefs.outputDirMode
    val customOutputDirFlow: Flow<String> = prefs.customOutputDir
    val compressionLevelFlow: Flow<String> = prefs.compressionLevel
    val autoMergeFlow: Flow<Boolean> = prefs.autoMerge
    val logUiStyleFlow: Flow<String> = prefs.logUiStyle
    val removeLicenseCheckFlow: Flow<Boolean> = prefs.removeLicenseCheck
    val backupBeforeUninstallFlow: Flow<Boolean> = prefs.backupBeforeUninstall

    val signingModeFlow: Flow<SigningMode> = prefs.signingMode
    val keystoreUriFlow: Flow<String> = prefs.keystoreUri
    val keystoreAliasFlow: Flow<String> = prefs.keystoreAlias
    val pk8UriFlow: Flow<String> = prefs.pk8Uri
    val pemUriFlow: Flow<String> = prefs.pemUri

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var currentUri: Uri? = null
    private var currentCacheFile: File? = null
    private var currentFileName: String = ""
    private var mergeJob: Job? = null

    private fun isInstalledApp(): Boolean {
        return currentUri == null
    }

    fun processUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            resetLogs()
            val context = getApplication<Application>()
            val fileName = FileUtil.getFileName(context, uri)
            if (!FileUtil.isSupportedFile(fileName)) {
                _uiState.value = UiState.Error("Unsupported format: $fileName\nExpected .apks .xapk .apkm .zip")
                return@launch
            }
            addLog("Reading & Extracting: $fileName")

            val cacheFile = FileUtil.uriToExtractedDir(context, uri, fileName)
            if (cacheFile == null || !cacheFile.exists()) {
                _uiState.value = UiState.Error("Failed to read file — check storage permission.")
                return@launch
            }
            currentUri = uri
            currentFileName = fileName
            currentCacheFile = cacheFile
            val splits = extractSplitNames(cacheFile)
            val density = context.resources.displayMetrics.densityDpi

            val autoSelect = prefs.autoSelectSplits.first()
            val isAutoMerge = prefs.autoMerge.first()
            val alwaysShowDialogPref = prefs.showSplitDialog.first()

            val shouldShowDialog = !isAutoMerge && (alwaysShowDialogPref || !autoSelect)

            val splitInfos = splits.map { name ->
                SplitInfo(name = name, isRelevantForDevice = DeviceSpecsUtil.isSplitRelevantForDevice(name, density))
            }
            addLog("Splits found: ${splits.size}")
            if (autoSelect && splits.isNotEmpty()) {
                addLog("Device-relevant: ${splitInfos.count { it.isRelevantForDevice }} split(s)")
            }
            _uiState.value = UiState.FileReady(fileName, splitInfos, shouldShowDialog)

            if (isAutoMerge) {
                val splitsToMerge = if (autoSelect) {
                    splitInfos.filter { it.isRelevantForDevice }.map { it.name }.ifEmpty { null }
                } else {
                    null
                }
                startMerge(splitsToMerge)
            }
        }
    }

    fun processInstalledApp(appInfo: ApplicationInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            resetLogs()
            val context = getApplication<Application>()
            val appLabel = runCatching { context.packageManager.getApplicationLabel(appInfo).toString() }.getOrDefault(appInfo.packageName)
            addLog("Loading installed splits for: $appLabel")

            val sourceDir = File(appInfo.sourceDir ?: "")
            val parentDir = sourceDir.parentFile
            if (parentDir == null || !parentDir.exists() || !parentDir.isDirectory) {
                _uiState.value = UiState.Error("Cannot access installed app directory.")
                return@launch
            }

            val apkName = "${appLabel.replace(" ", "_")}.apk"
            currentUri = null
            currentFileName = apkName
            currentCacheFile = parentDir
            val splits = extractSplitNames(parentDir)
            val density = context.resources.displayMetrics.densityDpi

            val autoSelect = prefs.autoSelectSplits.first()
            val isAutoMerge = prefs.autoMerge.first()
            val alwaysShowDialogPref = prefs.showSplitDialog.first()

            val shouldShowDialog = !isAutoMerge && (alwaysShowDialogPref || !autoSelect)

            val splitInfos = splits.map { name ->
                SplitInfo(name = name, isRelevantForDevice = DeviceSpecsUtil.isSplitRelevantForDevice(name, density))
            }
            addLog("Ready — ${splits.size} splits")
            _uiState.value = UiState.FileReady(apkName, splitInfos, shouldShowDialog)

            if (isAutoMerge) {
                val splitsToMerge = if (autoSelect) {
                    splitInfos.filter { it.isRelevantForDevice }.map { it.name }.ifEmpty { null }
                } else {
                    null
                }
                startMerge(splitsToMerge)
            }
        }
    }

    fun startMerge(selectedSplitNames: List<String>? = null) {
        mergeJob = viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val cacheFile = currentCacheFile
            if (cacheFile == null || !cacheFile.exists()) {
                _uiState.value = UiState.Error("No file loaded.")
                return@launch
            }
            _uiState.value = UiState.Processing(0f)
            addLog("════════════════════════════════════════")
            addLog("  AntiSplit-X — Merging")
            addLog("════════════════════════════════════════")
            addLog("Source: $currentFileName")

            val signEnabled = prefs.signApk.first()
            val suffixPref = prefs.suffix.first()
            val cleanSuffix = if (suffixPref == "_antisplit") "" else suffixPref
            val statusSuffix = if (signEnabled) "_signed" else "_unsigned"
            val compressionLevel = prefs.compressionLevel.first()
            val outputDirMode = prefs.outputDirMode.first()
            val customOutputDir = prefs.customOutputDir.first()
            val removeLicenseCheck = prefs.removeLicenseCheck.first()

            val outputName = FileUtil.buildOutputName(currentFileName, cleanSuffix + statusSuffix)

            val shouldPromptSave: Boolean
            val outputFile: File

            when (outputDirMode) {
                OutputDirMode.ASK_EACH_TIME -> {
                    outputFile = FileUtil.fallbackToCache(context, outputName)
                    shouldPromptSave = true
                    addLog("Output: (will prompt for save location)")
                }
                OutputDirMode.CUSTOM -> {
                    val dir = if (customOutputDir.isNotBlank()) {
                        File(customOutputDir).also { if (!it.exists()) it.mkdirs() }
                    } else {
                        File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "AntiSplit"
                        ).also { if (!it.exists()) it.mkdirs() }
                    }
                    outputFile = File(dir, outputName)
                    shouldPromptSave = false
                }
                OutputDirMode.SAME_AS_SOURCE -> {
                    if (isInstalledApp()) {
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "AntiSplit"
                        ).also { if (!it.exists()) it.mkdirs() }
                        outputFile = File(dir, outputName)
                        addLog("Note: Source folder unavailable for installed apps → using Downloads/AntiSplit")
                    } else {
                        val realPath = FileUtil.getRealPathFromUri(context, currentUri!!)
                        if (realPath != null) {
                            val parent = File(realPath).parentFile
                            if (parent != null && (parent.exists() || parent.mkdirs())) {
                                outputFile = File(parent, outputName)
                            } else {
                                val dir = File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                    "AntiSplit"
                                ).also { if (!it.exists()) it.mkdirs() }
                                outputFile = File(dir, outputName)
                            }
                        } else {
                            val dir = File(
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                "AntiSplit"
                            ).also { if (!it.exists()) it.mkdirs() }
                            outputFile = File(dir, outputName)
                        }
                    }
                    shouldPromptSave = false
                }
                OutputDirMode.DOWNLOADS -> {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "AntiSplit"
                    ).also { if (!it.exists()) it.mkdirs() }
                    outputFile = File(dir, outputName)
                    shouldPromptSave = false
                }
            }

            var mergeInput = cacheFile
            var createdTempDir = false

            if (selectedSplitNames != null) {
                if (isInstalledApp()) {
                    addLog("Copying selected splits to temporary cache...")
                    val tempDir = File(context.cacheDir, "antisplit_selected_${System.currentTimeMillis()}").also { it.mkdirs() }
                    cacheFile.listFiles()?.forEach { f ->
                        if (f.isFile && f.name.endsWith(".apk", true)) {
                            val splitName = f.name.substringBeforeLast(".")
                            if (selectedSplitNames.contains(splitName)) {
                                f.copyTo(File(tempDir, f.name), overwrite = true)
                            } else {
                                addLog("Skipping: ${f.name} (unselected)")
                            }
                        }
                    }
                    mergeInput = tempDir
                    createdTempDir = true
                } else {
                    if (cacheFile.isDirectory) {
                        cacheFile.listFiles()?.forEach { f ->
                            if (f.isFile && f.name.endsWith(".apk", true)) {
                                val splitName = f.name.substringBeforeLast(".")
                                if (!selectedSplitNames.contains(splitName)) {
                                    addLog("Skipping: ${f.name} (unselected)")
                                    f.delete()
                                }
                            }
                        }
                    }
                }
            }

            val tempDir = File(context.cacheDir, "antisplit_output").also { it.mkdirs() }
            val tempMergedApk = File(tempDir, "tmp_merge_$outputName")
            if (tempMergedApk.exists()) tempMergedApk.delete()

            _uiState.value = UiState.Processing(0.15f)

            val mergeResult = MergerUtil.merge(mergeInput, tempMergedApk, compressionLevel) { log ->
                addLog(log)
                _uiState.value = UiState.Processing(0.5f)
            }

            if (mergeResult is MergeResult.Failure) {
                if (createdTempDir) mergeInput.deleteRecursively()
                if (tempMergedApk.exists()) tempMergedApk.delete()
                _uiState.value = UiState.Error("Merge failed:\n${mergeResult.error}")
                return@launch
            }

            _uiState.value = UiState.Processing(0.65f)

            if (removeLicenseCheck) {
                addLog("Scanning AndroidManifest.xml for license entries...")
                LicenseRemovalUtil.removeLicenseEntries(tempMergedApk) { log -> addLog(log) }
            }

            _uiState.value = UiState.Processing(0.75f)

            var wasSigned = false
            val tempSignedApk = File(tempDir, "tmp_signed_$outputName")
            var finalReadyApk = tempMergedApk

            if (signEnabled) {
                if (tempSignedApk.exists()) tempSignedApk.delete()
                val signingMode = prefs.signingMode.first()
                val ksUri = prefs.keystoreUri.first()
                val pk8Uri = prefs.pk8Uri.first()
                val pemUri = prefs.pemUri.first()
                val alias = prefs.keystoreAlias.first()
                val ksPass = prefs.keystorePass.first()
                val keyPass = prefs.keyPass.first()
                wasSigned = try {
                    SignUtil.signApk(
                        context, tempMergedApk, tempSignedApk,
                        signingMode, ksUri, pk8Uri, pemUri,
                        alias, ksPass, keyPass
                    ) { log -> addLog(log) }
                } catch (e: Exception) {
                    addLog("Signing exception: ${e.message}")
                    false
                }

                if (wasSigned && tempSignedApk.exists()) {
                    finalReadyApk = tempSignedApk
                } else {
                    addLog("Signing skipped or failed, saving unsigned APK.")
                }
            } else {
                val tempUnsignedApk = File(tempDir, "tmp_unsigned_$outputName")
                if (tempUnsignedApk.exists()) tempUnsignedApk.delete()
                val unsigned = SignUtil.unsignApk(tempMergedApk, tempUnsignedApk) { log -> addLog(log) }
                if (unsigned && tempUnsignedApk.exists()) {
                    finalReadyApk = tempUnsignedApk
                } else {
                    addLog("Unsigning skipped or failed, saving original merged APK.")
                }
            }

            var actualOutputFile = outputFile
            try {
                if (actualOutputFile.exists()) actualOutputFile.delete()
                finalReadyApk.copyTo(actualOutputFile, overwrite = true)
                if (outputDirMode != OutputDirMode.ASK_EACH_TIME) {
                    addLog("Output saved to: ${actualOutputFile.absolutePath}")
                }
            } catch (e: Exception) {
                addLog("⚠ Permission denied or error saving to chosen directory: ${e.message}")
                addLog("Saving to internal cache instead...")
                actualOutputFile = FileUtil.fallbackToCache(context, outputName)
                if (actualOutputFile.exists()) actualOutputFile.delete()
                try {
                    finalReadyApk.copyTo(actualOutputFile, overwrite = true)
                } catch (e2: Exception) {
                    addLog("Fatal error saving fallback file: ${e2.message}")
                }
            }

            if (createdTempDir) mergeInput.deleteRecursively()
            if (tempMergedApk.exists() && tempMergedApk != actualOutputFile) tempMergedApk.delete()
            if (tempSignedApk.exists() && tempSignedApk != actualOutputFile) tempSignedApk.delete()
            val tempUnsignedApkCleanup = File(tempDir, "tmp_unsigned_$outputName")
            if (tempUnsignedApkCleanup.exists() && tempUnsignedApkCleanup != actualOutputFile) tempUnsignedApkCleanup.delete()

            _uiState.value = UiState.Processing(1f)

            if (!isInstalledApp()) {
                currentCacheFile?.deleteRecursively()
            }
            currentCacheFile = null

            val sizeMb = "%.2f".format(actualOutputFile.length() / 1_048_576f)
            addLog("════════════════════════════════════════")
            addLog("Done → ${actualOutputFile.name}")
            addLog("Size: $sizeMb MB")
            addLog(if (wasSigned) "Signed: ✓" else "Unsigned")
            if (removeLicenseCheck) addLog("License entries: removed ✓")
            addLog("════════════════════════════════════════")

            _uiState.value = UiState.Done(actualOutputFile, wasSigned, actualOutputFile.name, shouldPromptSave)
        }
    }

    suspend fun backupInstalledApp(packageName: String) {
        withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appLabel = pm.getApplicationLabel(appInfo).toString().replace(" ", "_")
                val apkPaths = DeviceSpecsUtil.getAllApkPathsForApp(appInfo)

                val defaultDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AntiSplit")
                if (!defaultDir.exists()) defaultDir.mkdirs()

                var zipFile = File(defaultDir, "$appLabel.apks")
                var counter = 1
                while (zipFile.exists()) {
                    zipFile = File(defaultDir, "${appLabel}_$counter.apks")
                    counter++
                }

                java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
                    apkPaths.forEach { path ->
                        val f = File(path)
                        if (f.exists() && f.canRead()) {
                            zos.putNextEntry(java.util.zip.ZipEntry(f.name))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
                addLog("✓ Backed up existing app to: ${zipFile.absolutePath}")
            } catch (e: Exception) {
                addLog("⚠ Failed to backup app before uninstall: ${e.message}")
            }
        }
    }

    fun saveFileToUri(sourceFile: File, targetUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                    sourceFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                addLog("File successfully saved to user-selected location.")
                sourceFile.delete()
            } catch (e: Exception) {
                addLog("Failed to save to selected location: ${e.message}")
            }
        }
    }

    suspend fun verifyAndSaveKeystore(uri: String, alias: String, ksPass: String, keyPass: String): String {
        return withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val result = SignUtil.verifyKeystore(context, uri, alias, ksPass, keyPass)
            if (result == "SUCCESS") {
                prefs.setKeystoreUri(uri)
                prefs.setKeystoreAlias(alias)
                prefs.setKeystorePass(ksPass)
                prefs.setKeyPass(keyPass)
                prefs.setSigningMode(SigningMode.KEYSTORE)
            }
            result
        }
    }

    fun cancelMerge() {
        mergeJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) { FileUtil.clearCache(getApplication()) }
        addLog("Cancelled by user.")
        _uiState.value = UiState.Idle
    }

    fun clearState() {
        currentUri = null
        currentFileName = ""
        currentCacheFile = null
        resetLogs()
        _uiState.value = UiState.Idle
    }

    fun setThemeMode(value: ThemeMode) { viewModelScope.launch { prefs.setThemeMode(value) } }
    fun setSignApk(value: Boolean) { viewModelScope.launch { prefs.setSignApk(value) } }
    fun setShowSplitDialog(value: Boolean) { viewModelScope.launch { prefs.setShowSplitDialog(value) } }
    fun setAutoSelectSplits(value: Boolean) { viewModelScope.launch { prefs.setAutoSelectSplits(value) } }
    fun setForceMerge(value: Boolean) { viewModelScope.launch { prefs.setForceMerge(value) } }
    fun setSuffix(value: String) { viewModelScope.launch { prefs.setSuffix(value) } }
    fun setOutputDirMode(value: OutputDirMode) { viewModelScope.launch { prefs.setOutputDirMode(value) } }
    fun setCustomOutputDir(path: String) { viewModelScope.launch { prefs.setCustomOutputDir(path) } }
    fun setCompressionLevel(value: String) { viewModelScope.launch { prefs.setCompressionLevel(value) } }
    fun setAutoMerge(value: Boolean) { viewModelScope.launch { prefs.setAutoMerge(value) } }
    fun setLogUiStyle(value: String) { viewModelScope.launch { prefs.setLogUiStyle(value) } }
    fun setRemoveLicenseCheck(value: Boolean) { viewModelScope.launch { prefs.setRemoveLicenseCheck(value) } }
    fun setBackupBeforeUninstall(value: Boolean) { viewModelScope.launch { prefs.setBackupBeforeUninstall(value) } }
    fun setSigningMode(mode: SigningMode) { viewModelScope.launch { prefs.setSigningMode(mode) } }
    fun setPk8Uri(uri: String) { viewModelScope.launch { prefs.setPk8Uri(uri) } }
    fun setPemUri(uri: String) { viewModelScope.launch { prefs.setPemUri(uri) } }

    private fun extractSplitNames(file: File): List<String> {
        val splits = mutableListOf<String>()
        try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { f ->
                    if (f.isFile && f.name.endsWith(".apk", ignoreCase = true)) {
                        splits.add(f.name.substringBeforeLast("."))
                    }
                }
            } else {
                ZipInputStream(FileInputStream(file)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && name.endsWith(".apk", ignoreCase = true) && !name.contains("META-INF")) {
                            val shortName = name.substringAfterLast('/').removeSuffix(".apk")
                            if (shortName.isNotBlank()) splits.add(shortName)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            addLog("Warning: split extraction error — ${e.message}")
        }
        return splits
    }

    private fun addLog(message: String) { _logs.value = _logs.value + message }
    private fun resetLogs() { _logs.value = emptyList() }
}