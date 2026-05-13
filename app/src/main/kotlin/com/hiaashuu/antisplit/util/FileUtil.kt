package com.hiaashuu.antisplit.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.hiaashuu.antisplit.data.OutputDirMode
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object FileUtil {

    private val SUPPORTED_EXTENSIONS = setOf("apks", "xapk", "apkm", "zip")

    private fun isWritable(dir: File): Boolean {
        return try {
            val test = File(dir, ".test_${System.currentTimeMillis()}")
            test.createNewFile()
            test.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getFileName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: "unknown"
        }
        var name = ""
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        name = cursor.getString(idx) ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (name.isBlank()) {
            name = uri.lastPathSegment ?: "unknown"
        }
        return name
    }

    fun getExtension(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot != -1 && dot < fileName.length - 1) {
            fileName.substring(dot + 1).lowercase()
        } else {
            ""
        }
    }

    fun isSupportedFile(fileName: String): Boolean {
        return getExtension(fileName) in SUPPORTED_EXTENSIONS
    }

    fun openInputStream(context: Context, uri: Uri): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun uriToExtractedDir(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val cacheDir = File(context.cacheDir, "antisplit_input_${System.currentTimeMillis()}")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            var apkCount = 0
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        // Exclude META-INF and strictly extract only actual split .apk files
                        if (!entry.isDirectory && name.endsWith(".apk", ignoreCase = true) && !name.contains("META-INF")) {
                            val safeName = name.substringAfterLast('/')
                            val outFile = File(cacheDir, safeName)
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            apkCount++
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            
            if (apkCount > 0) {
                return cacheDir
            } else {
                // Fallback for direct plain APKs or zip files not matching split architecture
                cacheDir.deleteRecursively()
                val directCacheDir = File(context.cacheDir, "antisplit_input_direct_${System.currentTimeMillis()}")
                directCacheDir.mkdirs()
                val dest = File(directCacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
                return dest
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun buildOutputName(sourceFileName: String, suffix: String = "_antisplit"): String {
        val nameWithoutExt = sourceFileName.substringBeforeLast('.').trimEnd()
        return "$nameWithoutExt$suffix.apk"
    }

    fun resolveOutputFile(
        context: Context,
        sourceUri: Uri?,
        sourceFileName: String,
        outputDirMode: OutputDirMode,
        customOutputDirPath: String,
        suffix: String = "_antisplit"
    ): File {
        val outputName = buildOutputName(sourceFileName, suffix)
        
        val defaultDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AntiSplit")
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        
        return when (outputDirMode) {
            OutputDirMode.DOWNLOADS -> File(defaultDir, outputName)

            OutputDirMode.CUSTOM -> {
                if (customOutputDirPath.isNotEmpty()) {
                    val customDir = File(customOutputDirPath)
                    if (customDir.exists() || customDir.mkdirs()) {
                        File(customDir, outputName)
                    } else {
                        File(defaultDir, outputName)
                    }
                } else {
                    File(defaultDir, outputName)
                }
            }

            OutputDirMode.ASK_EACH_TIME -> {
                fallbackToCache(context, outputName)
            }

            OutputDirMode.SAME_AS_SOURCE -> {
                if (sourceUri != null) {
                    val realPath = getRealPathFromUri(context, sourceUri)
                    if (realPath != null) {
                        val parent = File(realPath).parentFile
                        if (parent != null && (parent.exists() || parent.mkdirs())) {
                            File(parent, outputName)
                        } else {
                            File(defaultDir, outputName)
                        }
                    } else {
                        File(defaultDir, outputName)
                    }
                } else {
                    File(defaultDir, outputName) // fallback for installed apps
                }
            }
        }
    }

    fun fallbackToCache(context: Context, fileName: String): File {
        val cacheDir = File(context.cacheDir, "antisplit_output")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return File(cacheDir, fileName)
    }

    fun getRealPathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        return try {
            context.contentResolver
                .query(uri, arrayOf("_data"), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex("_data")
                        if (idx != -1) cursor.getString(idx) else null
                    } else {
                        null
                    }
                }
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache(context: Context) {
        try {
            // Delete all dynamically generated extraction caches and outputs
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("antisplit_")) {
                    file.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}