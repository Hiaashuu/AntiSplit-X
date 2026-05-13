package com.hiaashuu.antisplit.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

object PermissionUtil {

    fun hasStoragePermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Environment.isExternalStorageManager()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                true
            }
        }
    }

    fun getStoragePermissionToRequest(): String? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                null
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            else -> {
                null
            }
        }
    }

    fun requiresSettingsScreen(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun buildManageStorageIntent(packageName: String): Intent {
        return try {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
        } catch (e: Exception) {

            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }

    fun canRequestInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildInstallPermissionIntent(packageName: String): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:$packageName")
        )
    }
}