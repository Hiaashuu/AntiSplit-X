package com.hiaashuu.antisplit.ui.screen

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hiaashuu.antisplit.util.DeviceSpecsUtil
import com.hiaashuu.antisplit.viewmodel.MainViewModel
import com.hiaashuu.antisplit.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun AppListScreen(
    navController : NavController,
    viewModel     : MainViewModel
) {
    val context   = LocalContext.current
    val pm        = context.packageManager
    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()

    var userApps      by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var systemApps    by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var showUserApps  by remember { mutableStateOf(true) }
    var isLoading     by remember { mutableStateOf(true) }
    var isRefreshing  by remember { mutableStateOf(false) }
    var query         by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            refreshTrigger++
        }
    )

    LaunchedEffect(refreshTrigger) {
        if (userApps.isEmpty() && systemApps.isEmpty()) {
            isLoading = true
        }
        withContext(Dispatchers.IO) {
            val allApps = DeviceSpecsUtil.getInstalledSplitApps(pm)

            val sortedApps = allApps.sortedByDescending { appInfo ->
                runCatching { pm.getPackageInfo(appInfo.packageName, 0).firstInstallTime }.getOrDefault(0L)
            }

            userApps = sortedApps.filter {
                (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            }
            systemApps = sortedApps.filter {
                (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }
        }
        isLoading = false
        isRefreshing = false
    }

    LaunchedEffect(uiState) {
        if (uiState is UiState.Loading || uiState is UiState.Processing) {
            navController.popBackStack()
        }
    }

    val currentList = remember(showUserApps, userApps, systemApps) {
        if (showUserApps) userApps else systemApps
    }

    val filtered = remember(currentList, query) {
        if (query.isBlank()) currentList
        else currentList.filter { app ->
            pm.getApplicationLabel(app).toString()
                .contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Installed Split Apps", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isRefreshing = true
                        refreshTrigger++
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                OutlinedTextField(
                    value         = query,
                    onValueChange = { query = it },
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder   = { Text("Search apps…") },
                    leadingIcon   = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine    = true,
                    shape         = RoundedCornerShape(14.dp)
                )

                TabRow(
                    selectedTabIndex = if (showUserApps) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = showUserApps,
                        onClick = { showUserApps = true },
                        text = { Text("User Apps (${userApps.size})", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = !showUserApps,
                        onClick = { showUserApps = false },
                        text = { Text("Internal Apps (${systemApps.size})", fontWeight = FontWeight.SemiBold) }
                    )
                }

                when {
                    isLoading && !isRefreshing -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator()
                                Text("Scanning installed apps…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    filtered.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (query.isBlank()) "No split apps found in this category." else "No apps match \"$query\"", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> {
                        Text("${filtered.size} app(s) shown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.packageName }) { appInfo ->
                                AppItem(
                                    appInfo = appInfo,
                                    pm      = pm,
                                    onClick = {
                                        viewModel.processInstalledApp(appInfo)
                                        navController.popBackStack()
                                    }
                                )
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AppItem(appInfo: ApplicationInfo, pm: PackageManager, onClick: () -> Unit) {
    val label = remember(appInfo.packageName) { pm.getApplicationLabel(appInfo).toString() }
    val splitCount = remember(appInfo.packageName) { appInfo.splitSourceDirs?.size ?: 0 }

    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val iconDrawable = remember(appInfo.packageName) { runCatching { pm.getApplicationIcon(appInfo) }.getOrNull() }

            if (iconDrawable != null) {
                AsyncImage(model = iconDrawable, contentDescription = label, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
            } else {
                Surface(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Android, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(appInfo.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$splitCount split APK(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Select to merge", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}