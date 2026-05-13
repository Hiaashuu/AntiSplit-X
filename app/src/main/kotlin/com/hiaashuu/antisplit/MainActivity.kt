package com.hiaashuu.antisplit

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hiaashuu.antisplit.ui.screen.AppListScreen
import com.hiaashuu.antisplit.ui.screen.HomeScreen
import com.hiaashuu.antisplit.ui.screen.SettingsScreen
import com.hiaashuu.antisplit.ui.theme.AntiSplitTheme
import com.hiaashuu.antisplit.util.PermissionUtil
import com.hiaashuu.antisplit.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object AppList  : Screen("app_list")
    object Settings : Screen("settings")
}

class MainActivity : ComponentActivity() {

    private val pendingUriState: MutableState<Uri?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingUriState.value = resolveIncomingUri(intent)

        setContent {

            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val pendingUri by pendingUriState
            val navController = rememberNavController()

            AntiSplitTheme(themeMode = themeMode) {
                val context = LocalContext.current
                val hasStorage = remember { mutableStateOf(PermissionUtil.hasStoragePermission(context)) }

                val storagePermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted -> hasStorage.value = isGranted }

                val manageStorageLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { _ -> hasStorage.value = PermissionUtil.hasStoragePermission(context) }

                val notificationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (!hasStorage.value) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                                manageStorageLauncher.launch(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                manageStorageLauncher.launch(intent)
                            }
                        } else {
                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                }

                AntiSplitNavHost(
                    navController  = navController,
                    viewModel      = viewModel,
                    pendingUri     = pendingUri,
                    onUriConsumed  = { pendingUriState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingUriState.value = resolveIncomingUri(intent)
    }

    private fun resolveIncomingUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND ->
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else               -> null
        }
    }
}

@Composable
private fun AntiSplitNavHost(
    navController : NavHostController,
    viewModel     : MainViewModel,
    pendingUri    : Uri?,
    onUriConsumed : () -> Unit
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                navController = navController,
                viewModel     = viewModel,
                pendingUri    = pendingUri,
                onUriConsumed = onUriConsumed
            )
        }
        composable(Screen.AppList.route) {
            AppListScreen(
                navController = navController,
                viewModel     = viewModel
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                viewModel     = viewModel
            )
        }
    }
}