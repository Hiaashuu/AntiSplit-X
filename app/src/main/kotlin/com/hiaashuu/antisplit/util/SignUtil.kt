package com.hiaashuu.antisplit.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import com.hiaashuu.antisplit.data.SigningMode
import java.io.File
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

object SignUtil {

    fun signApk(
        context: Context,
        inputApk: File,
        outputApk: File,
        signingMode: SigningMode,
        customKeystoreUri: String,
        pk8Uri: String, pemUri: String,
        alias: String, ksPass: String, keyPass: String,
        logListener: (String) -> Unit
    ): Boolean {
        return try {
            val keyData = when (signingMode) {
                SigningMode.DEBUG -> loadSigningKey(context, isCustom = false, customUri = "", alias, ksPass, keyPass, logListener)
                SigningMode.KEYSTORE -> loadSigningKey(context, isCustom = true, customUri = customKeystoreUri, alias, ksPass, keyPass, logListener)
                SigningMode.KEY_FILES -> loadKeysFromFiles(context, pk8Uri, pemUri, logListener)
            }

            if (keyData == null) {
                logListener("⚠ Signing failed — could not load keys. Unsigned APK will be saved.")
                if (outputApk.exists()) outputApk.delete()
                inputApk.copyTo(outputApk, overwrite = true)
                return false
            }

            val (privateKey, certs) = keyData
            logListener("Signing APK (v1 + v2 scheme)...")

            invokeApkSigner(inputApk, outputApk, privateKey, certs, logListener)

            logListener("APK signed ✓")
            true

        } catch (e: Throwable) {
            val cause = if (e is java.lang.reflect.InvocationTargetException) e.targetException ?: e.cause ?: e else e.cause ?: e
            val errorMsg = cause?.message ?: cause?.javaClass?.simpleName ?: "Unknown reflection error"
            logListener("Signing error: $errorMsg")
            try {
                if (inputApk.exists()) {
                    if (outputApk.exists()) outputApk.delete()
                    inputApk.copyTo(outputApk, overwrite = true)
                    logListener("Unsigned APK saved as fallback.")
                }
            } catch (copyEx: Exception) {
                logListener("Fallback copy also failed: ${copyEx.message}")
            }
            false
        }
    }

    fun verifyKeystore(context: Context, customUri: String, alias: String, ksPass: String, keyPass: String): String {
        try {
            val bytes = context.contentResolver.openInputStream(Uri.parse(customUri))?.readBytes() ?: return "Could not read file from storage"
            
            val headStr = bytes.take(30).toByteArray().toString(Charsets.UTF_8)
            if (headStr.contains("BEGIN") || headStr.contains("PRIVATE KEY")) {
                return "This looks like a raw Key (.key, .pem). Please use 'Custom Keys' section instead of Keystore."
            }
            
            val errors = mutableSetOf<String>()
            val types = listOf("PKCS12", "JKS", "BKS")
            val providers = java.security.Security.getProviders()

            for (type in types) {
                try {
                    val ks = KeyStore.getInstance(type)
                    context.contentResolver.openInputStream(Uri.parse(customUri))?.use {
                        ks.load(it, ksPass.toCharArray())
                    }
                    val key = ks.getKey(alias, keyPass.toCharArray()) as? PrivateKey
                    if (key != null) return "SUCCESS"
                } catch (e: Throwable) {
                    val msg = e.message ?: e.javaClass.simpleName
                    if (msg.isNotBlank() && !msg.contains("not supported", true)) {
                        errors.add("$type(default): $msg")
                    }
                }

                for (provider in providers) {
                    try {
                        val ks = KeyStore.getInstance(type, provider)
                        context.contentResolver.openInputStream(Uri.parse(customUri))?.use {
                            ks.load(it, ksPass.toCharArray())
                        }
                        val key = ks.getKey(alias, keyPass.toCharArray()) as? PrivateKey
                        if (key != null) return "SUCCESS"
                    } catch (e: Throwable) {
                        val msg = e.message ?: e.javaClass.simpleName
                        if (msg.isNotBlank() && !msg.contains("not supported", true)) {
                            errors.add("$type(${provider.name}): $msg")
                        }
                    }
                }
            }
            
            val combinedError = errors.joinToString(" | ")
            
            if (combinedError.contains("Wrong version of key store") || combinedError.contains("not a PKCS12")) {
                return "Incompatible format. Android natively limits JKS support. Convert to PKCS12 (.p12) or try using 'Custom Keys'."
            }
            if (combinedError.contains("password was incorrect") || combinedError.contains("failed to decrypt")) {
                return "Incorrect Keystore password."
            }
            if (combinedError.contains("Cannot recover key") || combinedError.contains("Get Key failed")) {
                return "Incorrect Key password or Alias."
            }
            
            return combinedError.take(200).takeIf { it.isNotBlank() } ?: "Invalid keystore format or corrupted file"
        } catch (e: Throwable) {
            return "Fatal verification error: ${e.message}"
        }
    }

    private fun loadKeysFromFiles(context: Context, pk8Uri: String, pemUri: String, logListener: (String) -> Unit): Pair<PrivateKey, List<X509Certificate>>? {
        try {
            val pk8Bytes = context.contentResolver.openInputStream(Uri.parse(pk8Uri))?.readBytes() ?: return null
            val keySpec = PKCS8EncodedKeySpec(pk8Bytes)
            
            var privateKey: PrivateKey? = null
            val algorithms = listOf("RSA", "EC", "DSA")
            
            for (algo in algorithms) {
                try {
                    privateKey = KeyFactory.getInstance(algo).generatePrivate(keySpec)
                    if (privateKey != null) break
                } catch (e: Throwable) {
                    // Try next algorithm
                }
            }
            
            if (privateKey == null) {
                logListener("Failed to parse private key (unsupported algorithm or invalid PKCS8 format).")
                return null
            }

            val cf = CertificateFactory.getInstance("X.509")
            val cert = context.contentResolver.openInputStream(Uri.parse(pemUri))?.use {
                cf.generateCertificate(it) as X509Certificate
            } ?: return null
            
            return privateKey to listOf(cert)
        } catch (e: Throwable) {
            logListener("Error loading key files: ${e.message}")
            return null
        }
    }

    private fun loadSigningKey(
        context: Context,
        isCustom: Boolean,
        customUri: String,
        alias: String, ksPass: String, keyPass: String,
        logListener: (String) -> Unit
    ): Pair<PrivateKey, List<X509Certificate>>? {
        val useDefault = !isCustom || customUri.isBlank()
        val types = listOf("PKCS12", "JKS", "BKS")
        val providers = java.security.Security.getProviders()
        
        for (type in types) {
            try {
                val ks = KeyStore.getInstance(type)
                val stream = if (useDefault) {
                    context.assets.open("debug.keystore")
                } else {
                    context.contentResolver.openInputStream(Uri.parse(customUri))
                }
                
                stream?.use { 
                    val pass = if (useDefault) "android" else ksPass
                    ks.load(it, pass.toCharArray()) 
                } ?: continue
                
                val currentAlias = if (useDefault) "androiddebugkey" else alias
                val currentKeyPass = if (useDefault) "android" else keyPass
                
                val key = ks.getKey(currentAlias, currentKeyPass.toCharArray()) as? PrivateKey ?: continue
                val chain = ks.getCertificateChain(currentAlias)?.map { it as X509Certificate }?.takeIf { it.isNotEmpty() } ?: continue
                    
                return Pair(key, chain)
            } catch (e: Throwable) {
                if (!useDefault) {
                    logListener("Keystore load attempt ($type): ${e.message}")
                }
            }

            for (provider in providers) {
                try {
                    val ks = KeyStore.getInstance(type, provider)
                    val stream = if (useDefault) {
                        context.assets.open("debug.keystore")
                    } else {
                        context.contentResolver.openInputStream(Uri.parse(customUri))
                    }
                    
                    stream?.use { 
                        val pass = if (useDefault) "android" else ksPass
                        ks.load(it, pass.toCharArray()) 
                    } ?: continue
                    
                    val currentAlias = if (useDefault) "androiddebugkey" else alias
                    val currentKeyPass = if (useDefault) "android" else keyPass
                    
                    val key = ks.getKey(currentAlias, currentKeyPass.toCharArray()) as? PrivateKey ?: continue
                    val chain = ks.getCertificateChain(currentAlias)?.map { it as X509Certificate }?.takeIf { it.isNotEmpty() } ?: continue
                        
                    return Pair(key, chain)
                } catch (e: Throwable) {
                    // Ignore explicit provider errors to prevent console spam
                }
            }
        }
        return null
    }

    private fun invokeApkSigner(
        inputApk: File, outputApk: File, privateKey: PrivateKey, certs: List<X509Certificate>,
        logListener: (String) -> Unit
    ) {
        val cl = SignUtil::class.java.classLoader!!

        val apkSignerClass = listOf("com.android.apksig.ApkSigner", "io.github.muntashirakon.apksig.ApkSigner")
            .firstNotNullOfOrNull { name -> try { Class.forName(name, true, cl) } catch (e: ClassNotFoundException) { null } }
            ?: throw RuntimeException("ApkSigner class not found. Verify apksig-android is in your dependencies.")

        val signerConfigClass = apkSignerClass.classes.firstOrNull { it.simpleName == "SignerConfig" }
            ?: Class.forName("${apkSignerClass.name}\$SignerConfig", true, cl)
        val builderClass = apkSignerClass.classes.firstOrNull { it.simpleName == "Builder" }
            ?: Class.forName("${apkSignerClass.name}\$Builder", true, cl)
        val signerConfigBuilderClass = signerConfigClass.classes.firstOrNull { it.simpleName == "Builder" }
            ?: Class.forName("${signerConfigClass.name}\$Builder", true, cl)

        val signerConfigBuilder = signerConfigBuilderClass
            .getDeclaredConstructor(String::class.java, PrivateKey::class.java, List::class.java)
            .newInstance("CERT", privateKey, certs)
        val buildMethod = signerConfigBuilderClass.getMethod("build")
        val signerConfig = buildMethod.invoke(signerConfigBuilder)

        val apkSignerBuilder = builderClass.getDeclaredConstructor(List::class.java).newInstance(listOf(signerConfig))

        // Delete output APK if it exists to completely prevent FileAlreadyExistsException (EEXIST)
        if (outputApk.exists()) {
            outputApk.delete()
        }

        builderClass.getMethod("setInputApk", File::class.java).invoke(apkSignerBuilder, inputApk)
        builderClass.getMethod("setOutputApk", File::class.java).invoke(apkSignerBuilder, outputApk)

        tryInvoke(builderClass, apkSignerBuilder, "setV1SigningEnabled", true)
        tryInvoke(builderClass, apkSignerBuilder, "setV2SigningEnabled", true)
        tryInvoke(builderClass, apkSignerBuilder, "setV3SigningEnabled", false)
        tryInvokeInt(builderClass, apkSignerBuilder, "setMinSdkVersion", 21)

        val apkSigner = builderClass.getMethod("build").invoke(apkSignerBuilder)
        apkSignerClass.getMethod("sign").invoke(apkSigner)
    }

    private fun tryInvoke(clazz: Class<*>, instance: Any, method: String, value: Boolean) {
        try { clazz.getMethod(method, Boolean::class.java).invoke(instance, value) } catch (e: Throwable) {}
    }
    private fun tryInvokeInt(clazz: Class<*>, instance: Any, method: String, value: Int) {
        try { clazz.getMethod(method, Int::class.java).invoke(instance, value) } catch (e: Throwable) {}
    }

    private fun getSignatureFromInfo(packageInfo: PackageInfo?): Signature? {
        if (packageInfo == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.firstOrNull()
        }
    }

    private fun getSignatureHash(signature: Signature?): String? {
        if (signature == null) return null
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(signature.toByteArray())
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun getInstalledAppSignatureHash(context: Context, packageName: String): String? {
        val pm = context.packageManager
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val packageInfo = pm.getPackageInfo(packageName, flags)
            getSignatureHash(getSignatureFromInfo(packageInfo))
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun getApkFileSignatureHash(context: Context, apkFile: File): String? {
        if (!apkFile.exists()) return null
        val pm = context.packageManager
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            getSignatureHash(getSignatureFromInfo(packageInfo))
        } catch (e: Exception) {
            null
        }
    }
}