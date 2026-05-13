package com.hiaashuu.antisplit.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.DisplayMetrics

object DeviceSpecsUtil {

    fun getDeviceAbis(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.toList()
        } else {
            @Suppress("DEPRECATION")
            listOfNotNull(Build.CPU_ABI, Build.CPU_ABI2)
                .filter { it.isNotBlank() }
        }
    }

    fun getDensityQualifier(densityDpi: Int): String {
        return when {
            densityDpi <= DisplayMetrics.DENSITY_LOW -> "ldpi"
            densityDpi <= DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
            densityDpi <= DisplayMetrics.DENSITY_TV -> "tvdpi"
            densityDpi <= DisplayMetrics.DENSITY_HIGH -> "hdpi"
            densityDpi <= DisplayMetrics.DENSITY_XHIGH -> "xhdpi"
            densityDpi <= DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
            else -> "xxxhdpi"
        }
    }

    fun isSplitRelevantForDevice(splitName: String, densityDpi: Int): Boolean {
        val lower = splitName.lowercase()

        if (lower == "base" || lower == "base.apk") {
            return true
        }

        val abiMarkers = listOf(
            "arm64_v8a", "arm64-v8a",
            "armeabi_v7a", "armeabi-v7a",
            "armeabi",
            "x86_64",
            "x86",
            "mips64",
            "mips"
        )
        val isAbiSplit = abiMarkers.any { lower.contains(it) }
        if (isAbiSplit) {
            val deviceAbis = getDeviceAbis()
            return deviceAbis.any { abi ->
                lower.contains(abi.replace("-", "_").lowercase())
            }
        }

        val densityMarkers = listOf("ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        val isDensitySplit = densityMarkers.any { lower.contains(it) }
        if (isDensitySplit) {
            val targetDensity = getDensityQualifier(densityDpi)
            return lower.contains(targetDensity)
        }

        return true
    }

    fun getInstalledSplitApps(packageManager: PackageManager): List<ApplicationInfo> {
        val allApps: List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        }

        return allApps.filter { appInfo ->
            isSplitApp(appInfo)
        }
    }

    private fun isSplitApp(appInfo: ApplicationInfo): Boolean {
        return try {
            !appInfo.splitSourceDirs.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun getAllApkPathsForApp(appInfo: ApplicationInfo): List<String> {
        val paths = mutableListOf<String>()
        appInfo.sourceDir?.let { basePath ->
            paths.add(basePath)
        }
        appInfo.splitSourceDirs?.forEach { splitPath ->
            paths.add(splitPath)
        }
        return paths
    }

    fun getSplitNamesForApp(appInfo: ApplicationInfo): List<String> {
        val names = mutableListOf<String>()
        appInfo.splitNames?.forEach { splitName ->
            names.add(splitName)
        }
        if (names.isEmpty()) {

            appInfo.splitSourceDirs?.forEach { path ->
                val fileName = path.substringAfterLast('/')
                val nameWithoutExt = fileName.substringBeforeLast('.')

                val cleanName = nameWithoutExt
                    .removePrefix("split_")
                    .removePrefix("base")
                if (cleanName.isNotBlank()) {
                    names.add(cleanName)
                }
            }
        }
        return names
    }
}