package com.hiaashuu.antisplit.util

import java.io.File
import java.lang.reflect.InvocationTargetException

sealed class MergeResult {
    data class Success(val outputFile: File) : MergeResult()
    data class Failure(val error: String, val cause: Throwable? = null) : MergeResult()
}

enum class LogLevel { INFO, WARNING, ERROR }

data class LogEntry(val level: LogLevel, val message: String)

object MergerUtil {

    fun merge(
        inputFile        : File,
        outputFile       : File,
        compressionLevel : String,
        logListener      : (String) -> Unit
    ): MergeResult {
        return try {
            logListener("Preparing output directory...")
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) { outputFile.delete() }

            logListener("Starting APKEditor Merger...")
            logListener("Input  → ${inputFile.name} (${inputFile.length() / 1024} KB)")
            logListener("Compression: ${compressionLevelLabel(compressionLevel)}")

            performMerge(inputFile, outputFile, compressionLevel, logListener)

            if (outputFile.exists() && outputFile.length() > 0L) {
                logListener("Merge engine completed successfully.")
                MergeResult.Success(outputFile)
            } else {
                MergeResult.Failure(
                    "Merger ran but output file was not produced. " +
                    "Verify the input is a valid split APK archive."
                )
            }
        } catch (e: InvocationTargetException) {
            val target = e.targetException ?: e.cause
            val msg    = target?.message ?: target?.javaClass?.simpleName ?: e.message ?: "Unknown error"
            logListener("ERROR inside Merger: $msg")
            MergeResult.Failure(msg, target)
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            logListener("ERROR: $msg")
            MergeResult.Failure(msg, e)
        }
    }

    private fun compressionLevelLabel(level: String): String {
        return when (level.uppercase()) {
            "STORED"           -> "Fastest (No Compression)"
            "BEST_SPEED"       -> "Speed Mode (Level 1)"
            "BEST_COMPRESSION" -> "Maximum (Level 9)"
            else               -> "Balanced (Level 6)"
        }
    }

    private fun performMerge(
        inputFile        : File,
        outputFile       : File,
        compressionLevel : String,
        logListener      : (String) -> Unit
    ) {
        val cl = MergerUtil::class.java.classLoader
            ?: ClassLoader.getSystemClassLoader()

        val mergerCandidates = listOf(
            "com.reandroid.apkeditor.merge.Merger",
            "com.reandroid.Merger",
            "com.reandroid.apkeditor.Merger",
            "com.reandroid.apkeditor.APKEditor"
        )
        val mergerClass = mergerCandidates.firstNotNullOfOrNull { className ->
            try { Class.forName(className, true, cl) } catch (e: ClassNotFoundException) { null }
        } ?: throw RuntimeException("APKEditor Merger class not found.")
        logListener("Merger class loaded.")

        val optionsCandidates = listOf(
            "com.reandroid.apkeditor.merge.MergerOptions",
            "com.reandroid.apkeditor.merge.MergeOptions",
            "com.reandroid.apkeditor.common.APKEditorOptions",
            "com.reandroid.apkeditor.Options",
            "com.reandroid.apkeditor.APKEditorOptions"
        )
        val optionsClass = optionsCandidates.firstNotNullOfOrNull { className ->
            try { Class.forName(className, true, cl) } catch (e: ClassNotFoundException) { null }
        }

        val merger = buildMergerInstance(mergerClass, optionsClass, inputFile, outputFile, compressionLevel, logListener)

        val runMethod = mergerClass.methods.firstOrNull { method ->
            method.name == "run" || method.name == "execute" || method.name == "merge"
        } ?: throw RuntimeException("No execution method found in ${mergerClass.name}.")

        logListener("Executing merge process...")
        runMethod.invoke(merger)
    }

    private fun buildMergerInstance(
        mergerClass      : Class<*>,
        optionsClass     : Class<*>?,
        inputFile        : File,
        outputFile       : File,
        compressionLevel : String,
        logListener      : (String) -> Unit
    ): Any {

        if (optionsClass != null) {
            try {
                val options = optionsClass.getDeclaredConstructor().newInstance()

                trySetField(optionsClass, options, inputFile, "inputFile", "input", "mInputFile", "inFile", "sourceFile")
                trySetField(optionsClass, options, outputFile, "outputFile", "output", "mOutputFile", "outFile", "destFile")
                trySetBooleanField(optionsClass, options, true, "cleanMeta", "mCleanMeta", "repackage", "cleanMetaData")

                when (compressionLevel.uppercase()) {
                    "STORED" -> {
                        logListener("Applying Fastest mode (No Compression)...")
                        trySetBooleanField(optionsClass, options, true, "store", "mStore", "uncompressed", "noCompression")
                    }
                    "BEST_SPEED" -> {
                        logListener("Applying Speed mode (Level 1)...")
                        trySetIntField(optionsClass, options, 1, "compressionLevel", "mCompressionLevel", "level", "mLevel", "deflateLevel")
                    }
                    "BEST_COMPRESSION" -> {
                        logListener("Applying Maximum Compression (Level 9)...")
                        trySetIntField(optionsClass, options, 9, "compressionLevel", "mCompressionLevel", "level", "mLevel", "deflateLevel")
                    }
                    else -> {

                        logListener("Using balanced compression (Level 6)...")
                    }
                }

                val ctor = mergerClass.getDeclaredConstructor(optionsClass)
                return ctor.newInstance(options)
            } catch (e: Exception) {
                logListener("Fallback: Options instantiation failed, trying direct parameters.")
            }
        }

        try {
            val ctor = mergerClass.getDeclaredConstructor(File::class.java, File::class.java)
            return ctor.newInstance(inputFile, outputFile)
        } catch (e: Exception) {}

        try {
            val ctor = mergerClass.getDeclaredConstructor(String::class.java, String::class.java)
            return ctor.newInstance(inputFile.absolutePath, outputFile.absolutePath)
        } catch (e: Exception) {}

        try {
            val instance = mergerClass.getDeclaredConstructor().newInstance()
            trySetField(mergerClass, instance, inputFile, "inputFile", "input", "mInputFile", "inFile")
            trySetField(mergerClass, instance, outputFile, "outputFile", "output", "mOutputFile", "outFile")
            return instance
        } catch (e: Exception) {}

        throw RuntimeException("Could not instantiate ${mergerClass.name}. Add the correct pattern.")
    }

    private fun trySetField(clazz: Class<*>, instance: Any, value: Any, vararg names: String): Boolean {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            for (name in names) {
                try {
                    val field = currentClass.getDeclaredField(name)
                    field.isAccessible = true
                    field.set(instance, value)
                    return true
                } catch (e: NoSuchFieldException) {}
            }
            currentClass = currentClass.superclass
        }
        for (name in names) {
            val setterName = "set" + name.replaceFirstChar { it.uppercase() }
            try {
                val method = clazz.getMethod(setterName, value.javaClass)
                method.invoke(instance, value)
                return true
            } catch (e: Exception) {}
        }
        return false
    }

    private fun trySetBooleanField(clazz: Class<*>, instance: Any, value: Boolean, vararg names: String): Boolean {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            for (name in names) {
                try {
                    val field = currentClass.getDeclaredField(name)
                    field.isAccessible = true
                    field.set(instance, value)
                    return true
                } catch (e: NoSuchFieldException) {}
            }
            currentClass = currentClass.superclass
        }
        for (name in names) {
            val setterName = "set" + name.replaceFirstChar { it.uppercase() }
            try {
                val method = clazz.getMethod(setterName, Boolean::class.java)
                method.invoke(instance, value)
                return true
            } catch (e: Exception) {}
        }
        return false
    }

    private fun trySetIntField(clazz: Class<*>, instance: Any, value: Int, vararg names: String): Boolean {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            for (name in names) {
                try {
                    val field = currentClass.getDeclaredField(name)
                    field.isAccessible = true
                    field.set(instance, value)
                    return true
                } catch (e: NoSuchFieldException) {}
            }
            currentClass = currentClass.superclass
        }
        for (name in names) {
            val setterName = "set" + name.replaceFirstChar { it.uppercase() }
            try {
                val method = clazz.getMethod(setterName, Int::class.java)
                method.invoke(instance, value)
                return true
            } catch (e: Exception) {}
            try {
                val method = clazz.getMethod(setterName, Integer::class.java)
                method.invoke(instance, value)
                return true
            } catch (e: Exception) {}
        }
        return false
    }
}