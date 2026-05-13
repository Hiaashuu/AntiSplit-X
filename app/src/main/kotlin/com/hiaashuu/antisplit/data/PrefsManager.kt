package com.hiaashuu.antisplit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "antisplit_prefs")

enum class OutputDirMode { SAME_AS_SOURCE, DOWNLOADS, CUSTOM, ASK_EACH_TIME }
enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class SigningMode { DEBUG, KEYSTORE, KEY_FILES }

class PrefsManager(private val context: Context) {

    companion object {
        private val KEY_SIGN_APK = booleanPreferencesKey("sign_apk")
        private val KEY_OUTPUT_DIR_MODE = stringPreferencesKey("output_dir_mode")
        private val KEY_CUSTOM_OUTPUT_DIR = stringPreferencesKey("custom_output_dir")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_SHOW_SPLIT_DIALOG = booleanPreferencesKey("show_split_dialog")
        private val KEY_AUTO_SELECT_SPLITS = booleanPreferencesKey("auto_select_splits")
        private val KEY_FORCE_MERGE = booleanPreferencesKey("force_merge")
        private val KEY_SUFFIX = stringPreferencesKey("suffix")
        private val KEY_COMPRESSION_LEVEL = stringPreferencesKey("compression_level")
        private val KEY_AUTO_MERGE = booleanPreferencesKey("auto_merge")
        private val KEY_LOG_UI_STYLE = stringPreferencesKey("log_ui_style")
        private val KEY_REMOVE_LICENSE_CHECK = booleanPreferencesKey("remove_license_check")
        private val KEY_BACKUP_BEFORE_UNINSTALL = booleanPreferencesKey("backup_before_uninstall")

        private val KEY_SIGNING_MODE = stringPreferencesKey("signing_mode")
        private val KEY_KEYSTORE_URI = stringPreferencesKey("keystore_uri")
        private val KEY_KEYSTORE_ALIAS = stringPreferencesKey("keystore_alias")
        private val KEY_KEYSTORE_PASS = stringPreferencesKey("keystore_pass")
        private val KEY_KEY_PASS = stringPreferencesKey("key_pass")
        private val KEY_PK8_URI = stringPreferencesKey("pk8_uri")
        private val KEY_PEM_URI = stringPreferencesKey("pem_uri")
    }

    val signApk: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_SIGN_APK] ?: true }
    val outputDirMode: Flow<OutputDirMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_OUTPUT_DIR_MODE] ?: OutputDirMode.DOWNLOADS.name
        runCatching { OutputDirMode.valueOf(raw) }.getOrDefault(OutputDirMode.DOWNLOADS)
    }
    val customOutputDir: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_CUSTOM_OUTPUT_DIR] ?: "" }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_THEME_MODE] ?: ThemeMode.DARK.name
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.DARK)
    }
    val showSplitDialog: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_SHOW_SPLIT_DIALOG] ?: false }
    val autoSelectSplits: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_AUTO_SELECT_SPLITS] ?: true }
    val forceMerge: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_FORCE_MERGE] ?: false }
    val suffix: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_SUFFIX] ?: "_antisplit" }
    val compressionLevel: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_COMPRESSION_LEVEL] ?: "DEFAULT" }
    val autoMerge: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_AUTO_MERGE] ?: true }
    val logUiStyle: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_LOG_UI_STYLE] ?: "INLINE" }
    val removeLicenseCheck: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_REMOVE_LICENSE_CHECK] ?: false }
    val backupBeforeUninstall: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_BACKUP_BEFORE_UNINSTALL] ?: true }

    val signingMode: Flow<SigningMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_SIGNING_MODE] ?: SigningMode.DEBUG.name
        runCatching { SigningMode.valueOf(raw) }.getOrDefault(SigningMode.DEBUG)
    }
    val keystoreUri: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_KEYSTORE_URI] ?: "" }
    val keystoreAlias: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_KEYSTORE_ALIAS] ?: "" }
    val keystorePass: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_KEYSTORE_PASS] ?: "" }
    val keyPass: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_KEY_PASS] ?: "" }
    val pk8Uri: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_PK8_URI] ?: "" }
    val pemUri: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_PEM_URI] ?: "" }

    suspend fun setSignApk(value: Boolean) { context.dataStore.edit { it[KEY_SIGN_APK] = value } }
    suspend fun setOutputDirMode(value: OutputDirMode) { context.dataStore.edit { it[KEY_OUTPUT_DIR_MODE] = value.name } }
    suspend fun setCustomOutputDir(path: String) { context.dataStore.edit { it[KEY_CUSTOM_OUTPUT_DIR] = path } }
    suspend fun setThemeMode(value: ThemeMode) { context.dataStore.edit { it[KEY_THEME_MODE] = value.name } }
    suspend fun setShowSplitDialog(value: Boolean) { context.dataStore.edit { it[KEY_SHOW_SPLIT_DIALOG] = value } }
    suspend fun setAutoSelectSplits(value: Boolean) { context.dataStore.edit { it[KEY_AUTO_SELECT_SPLITS] = value } }
    suspend fun setForceMerge(value: Boolean) { context.dataStore.edit { it[KEY_FORCE_MERGE] = value } }
    suspend fun setSuffix(value: String) { context.dataStore.edit { it[KEY_SUFFIX] = value } }
    suspend fun setCompressionLevel(value: String) { context.dataStore.edit { it[KEY_COMPRESSION_LEVEL] = value } }
    suspend fun setAutoMerge(value: Boolean) { context.dataStore.edit { it[KEY_AUTO_MERGE] = value } }
    suspend fun setLogUiStyle(value: String) { context.dataStore.edit { it[KEY_LOG_UI_STYLE] = value } }
    suspend fun setRemoveLicenseCheck(value: Boolean) { context.dataStore.edit { it[KEY_REMOVE_LICENSE_CHECK] = value } }
    suspend fun setBackupBeforeUninstall(value: Boolean) { context.dataStore.edit { it[KEY_BACKUP_BEFORE_UNINSTALL] = value } }

    suspend fun setSigningMode(mode: SigningMode) { context.dataStore.edit { it[KEY_SIGNING_MODE] = mode.name } }
    suspend fun setKeystoreUri(value: String) { context.dataStore.edit { it[KEY_KEYSTORE_URI] = value } }
    suspend fun setKeystoreAlias(value: String) { context.dataStore.edit { it[KEY_KEYSTORE_ALIAS] = value } }
    suspend fun setKeystorePass(value: String) { context.dataStore.edit { it[KEY_KEYSTORE_PASS] = value } }
    suspend fun setKeyPass(value: String) { context.dataStore.edit { it[KEY_KEY_PASS] = value } }
    suspend fun setPk8Uri(value: String) { context.dataStore.edit { it[KEY_PK8_URI] = value } }
    suspend fun setPemUri(value: String) { context.dataStore.edit { it[KEY_PEM_URI] = value } }
}