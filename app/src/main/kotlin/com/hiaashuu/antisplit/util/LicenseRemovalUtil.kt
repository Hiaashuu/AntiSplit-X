package com.hiaashuu.antisplit.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Removes DRM / license-check entries from AndroidManifest.xml inside a merged APK.
 *
 * Strategy:
 *  1. Primary: APKEditor's ApkModule reflection API (Structural Deletion).
 *     This perfectly deletes the ENTIRE XML sections (e.g. <uses-permission ... /> or <activity ... />)
 *     exactly as requested, leaving no invalid empty quotes behind.
 *  2. Fallback: Plain Text Regex. If the manifest is plain XML, we use regex to delete the full tags.
 *  (Note: Binary string blanking is removed because it leaves empty invalid quotes causing install failures).
 */
object LicenseRemovalUtil {

    // Strings we want to neutralise inside AndroidManifest.xml
    private val LICENSE_MARKERS = listOf(
        "com.android.vending.CHECK_LICENSE",
        "androidx.room.MultiInstanceInvalidationService",
        "com.google.android.play.core.common.PlayCoreDialogWrapperActivity",
        "com.pairip.licensecheck.LicenseActivity",
        "com.pairip.licensecheck.LicenseContentProvider",
        "com.pairip.licensecheck",
        "Theme.PlayCore.Transparent",
        "play.core.common.PlayCoreDialogWrapperActivity",
        "com.android.stamp.source",
        "com.android.stamp.type",
        "com.android.vending.splits",
        "com.android.vending.derived.apk.id",
        "com.android.dynamic.apk.fused.modules"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    fun removeLicenseEntries(apkFile: File, logListener: (String) -> Unit): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            logListener("⚠ License removal: APK file not found or empty")
            return false
        }

        // Strategy 1 — APKEditor reflection (Completely deletes the entire XML tags)
        if (tryApkEditorApproach(apkFile, logListener)) {
            return true
        }

        // Strategy 2 — Regex Approach (Works if the merger left the XML as plain text)
        if (tryPlainTextRegexApproach(apkFile, logListener)) {
            return true
        }

        logListener("⚠ Could not delete entire XML sections. APKEditor structural parser not found.")
        return false
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Strategy 1: APKEditor reflection (Full Section Deletion)
    // ─────────────────────────────────────────────────────────────────────────

    private fun tryApkEditorApproach(apkFile: File, logListener: (String) -> Unit): Boolean {
        val cl = LicenseRemovalUtil::class.java.classLoader ?: return false
        var apkModuleClass: Class<*>? = null

        val candidates = listOf(
            "com.reandroid.apk.ApkModule",
            "com.reandroid.lib.apk.ApkModule",
            "com.reandroid.apkeditor.ApkModule",
            "com.reandroid.apkeditor.common.ApkModule"
        )

        for (name in candidates) {
            try {
                apkModuleClass = Class.forName(name, true, cl)
                break
            } catch (e: Throwable) {}
        }

        if (apkModuleClass == null) {
            logListener("  APKEditor ApkModule class not found. Trying regex fallback...")
            return false
        }

        return try {
            // Find load method (static, takes File or String)
            var apkModule: Any? = null
            for (m in apkModuleClass.methods) {
                if (java.lang.reflect.Modifier.isStatic(m.modifiers) && m.returnType == apkModuleClass) {
                    if (m.parameterCount == 1 && m.parameterTypes[0] == File::class.java) {
                        apkModule = m.invoke(null, apkFile)
                        break
                    } else if (m.parameterCount == 1 && m.parameterTypes[0] == String::class.java) {
                        apkModule = m.invoke(null, apkFile.absolutePath)
                        break
                    }
                }
            }

            if (apkModule == null) {
                logListener("  Failed to find static loadApkFile method.")
                return false
            }

            // Get manifest block
            val manifestMethod = apkModuleClass.methods.firstOrNull { m ->
                m.parameterCount == 0 && (m.name == "getAndroidManifestBlock" || m.name == "getAndroidManifest" || m.name == "getManifest")
            } ?: return false

            val manifestBlock = manifestMethod.invoke(apkModule) ?: return false

            // Get root element (ResXmlElement)
            val docMethod = manifestBlock.javaClass.methods.firstOrNull { m ->
                m.parameterCount == 0 && (m.name == "getManifestElement" || m.name == "getResXmlElement" || m.name == "getDocumentElement" || m.name == "getRootElement")
            } ?: return false

            val rootElement = docMethod.invoke(manifestBlock) ?: return false

            // Recursively delete all matching XML tags completely
            val removed = removeTagsDeep(rootElement, logListener)

            if (removed > 0) {
                // Refresh the manifest block to sync changes
                try {
                    val refreshMethod = manifestBlock.javaClass.methods.firstOrNull { it.name == "refresh" }
                    refreshMethod?.invoke(manifestBlock)
                } catch (e: Exception) {}

                // Write modified APK back
                val writeMethod = apkModuleClass.methods.firstOrNull { m ->
                    m.parameterCount == 1 && m.parameterTypes[0] == File::class.java && (m.name == "writeApk" || m.name == "write" || m.name == "save")
                }
                
                if (writeMethod != null) {
                    writeMethod.invoke(apkModule, apkFile)
                    logListener("✓ Structurally deleted $removed ENTIRE XML sections from AndroidManifest.xml")
                    return true
                }
            }
            false
        } catch (e: Exception) {
            logListener("  APKEditor structural patch error: ${e.message}")
            false
        }
    }

    private fun removeTagsDeep(element: Any, logListener: (String) -> Unit): Int {
        var count = 0
        try {
            var children: Iterable<*>? = null
            for (m in element.javaClass.methods) {
                if (m.parameterCount == 0 && (m.name == "listElements" || m.name == "getElements" || m.name == "getChildElements")) {
                    val res = m.invoke(element)
                    if (res is Iterable<*>) {
                        children = res
                    } else if (res is Iterator<*>) {
                        @Suppress("UNCHECKED_CAST")
                        children = (res as Iterator<Any>).asSequence().asIterable()
                    } else if (res is Array<*>) {
                        children = res.asIterable()
                    }
                    if (children != null) break
                }
            }

            if (children == null && element is Iterable<*>) {
                children = element
            }

            if (children == null) return 0

            val toRemove = mutableListOf<Any>()

            for (child in children) {
                if (child == null) continue
                if (isLicenseElement(child)) {
                    toRemove.add(child)
                } else {
                    count += removeTagsDeep(child, logListener)
                }
            }

            for (child in toRemove) {
                val nameMeth = child.javaClass.methods.firstOrNull { it.name == "getName" || it.name == "getTag" }
                val tagName = nameMeth?.invoke(child)?.toString() ?: "element"

                var removed = false

                // 1. Try child.removeSelf(), child.remove(), child.destroy()
                val selfMethods = listOf("removeSelf", "remove", "destroy", "delete")
                for (m in child.javaClass.methods) {
                    if (selfMethods.contains(m.name) && m.parameterCount == 0) {
                        try { m.invoke(child); removed = true; break } catch(e: Exception) {}
                    }
                }

                // 2. Try parent.removeElement(child), parent.removeChild(child), parent.remove(child)
                if (!removed) {
                    val parentMethods = listOf("removeElement", "removeChild", "remove", "deleteElement")
                    for (m in element.javaClass.methods) {
                        if (parentMethods.contains(m.name) && m.parameterCount == 1) {
                            try { 
                                val res = m.invoke(element, child)
                                if (res is Boolean && !res) continue
                                removed = true; break 
                            } catch(e: Exception) {}
                        }
                    }
                }

                // 3. Try parent.getElements().remove(child)
                if (!removed) {
                    try {
                        val getElems = element.javaClass.methods.firstOrNull { it.name == "getElements" && it.parameterCount == 0 }
                        val elemList = getElems?.invoke(element)
                        if (elemList != null) {
                            val listRemove = elemList.javaClass.methods.firstOrNull { (it.name == "remove" || it.name == "removeElement") && it.parameterCount == 1 }
                            if (listRemove != null) {
                                val res = listRemove.invoke(elemList, child)
                                if (res is Boolean && !res) { /* failed */ }
                                else { removed = true }
                            }
                        }
                    } catch(e: Exception) {}
                }

                if (removed) {
                    logListener("  → Entire XML section deleted: <$tagName ...>")
                    count++
                } else {
                    // 4. Fallback: NEUTRALIZATION. (Rename tag to "removed_tag" and clear attributes)
                    try {
                        val setName = child.javaClass.methods.firstOrNull { (it.name == "setName" || it.name == "setTag") && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
                        if (setName != null) {
                            setName.invoke(child, "removed_tag")
                            
                            val clearAttrs = child.javaClass.methods.firstOrNull { it.name == "clearAttributes" || it.name == "removeAttributes" }
                            if (clearAttrs != null) {
                                clearAttrs.invoke(child)
                            } else {
                                val getAttrs = child.javaClass.methods.firstOrNull { it.name == "listAttributes" || it.name == "getAttributes" }
                                val attrs = getAttrs?.invoke(child) as? Iterable<*>
                                val removeAttr = child.javaClass.methods.firstOrNull { it.name == "removeAttribute" && it.parameterCount == 1 }
                                attrs?.toList()?.forEach { attr ->
                                    try { removeAttr?.invoke(child, attr) } catch(e: Exception) {}
                                }
                            }
                            logListener("  → XML section neutralized (renamed to <removed_tag> and attributes stripped): <$tagName>")
                            removed = true
                            count++
                        }
                    } catch(e: Exception) {}
                }

                if (!removed) {
                    logListener("  [!] Failed to fully delete XML section: <$tagName>")
                }
            }
        } catch (e: Exception) {
            logListener("  [!] Error traversing XML tree: ${e.message}")
        }
        return count
    }

    private fun isLicenseElement(element: Any): Boolean {
        val elementStr = element.toString()
        if (LICENSE_MARKERS.any { elementStr.contains(it, true) }) return true

        // Deep inspect attributes
        try {
            val methods = element.javaClass.methods
            val listAttrs = methods.firstOrNull { it.name.contains("Attribute") && it.parameterCount == 0 }
            if (listAttrs != null) {
                val attrs = listAttrs.invoke(element)
                val attrIterable = when (attrs) {
                    is Iterable<*> -> attrs
                    is Iterator<*> -> attrs.asSequence().asIterable()
                    is Array<*> -> attrs.asIterable()
                    else -> null
                }
                attrIterable?.forEach { attr ->
                    if (attr != null) {
                        val attrStr = attr.toString()
                        if (LICENSE_MARKERS.any { attrStr.contains(it, true) }) return true
                        
                        val valMethod = attr.javaClass.methods.firstOrNull { it.name == "getValueAsString" || it.name == "getValue" }
                        val attrVal = valMethod?.invoke(attr)?.toString()
                        if (attrVal != null && LICENSE_MARKERS.any { attrVal.contains(it, true) }) return true
                    }
                }
            }
        } catch (e: Exception) {}

        return false
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Strategy 2: Plain Text Regex Fallback (If manifest is decoded to plain text)
    // ─────────────────────────────────────────────────────────────────────────

    private fun tryPlainTextRegexApproach(apkFile: File, logListener: (String) -> Unit): Boolean {
        val tempFile = File(apkFile.parentFile, ".tmp_lic_regex_${apkFile.name}")
        var modified = false
        var isPlainText = false

        try {
            ZipInputStream(FileInputStream(apkFile)).use { zin ->
                ZipOutputStream(FileOutputStream(tempFile)).use { zout ->
                    var entry = zin.nextEntry
                    while (entry != null) {
                        val rawData = zin.readBytes()
                        var outData = rawData

                        if (entry.name == "AndroidManifest.xml") {
                            val str = String(rawData, Charsets.UTF_8)
                            if (str.contains("<?xml") || str.contains("<manifest")) {
                                isPlainText = true
                                var xmlStr = str
                                for (marker in LICENSE_MARKERS) {
                                    val safeMarker = Regex.escape(marker)
                                    // Remove self-closing tags: <activity ... marker ... />
                                    val pattern1 = Regex("<([a-zA-Z0-9_:-]+)[^>]*?$safeMarker[^>]*?/>", RegexOption.DOT_MATCHES_ALL)
                                    xmlStr = pattern1.replace(xmlStr, "")
                                    
                                    // Remove open-close tags: <activity ... marker ... > ... </activity>
                                    val pattern2 = Regex("<([a-zA-Z0-9_:-]+)[^>]*?$safeMarker[^>]*?>.*?</\\1>", RegexOption.DOT_MATCHES_ALL)
                                    xmlStr = pattern2.replace(xmlStr, "")
                                }
                                if (xmlStr != str) {
                                    outData = xmlStr.toByteArray(Charsets.UTF_8)
                                    modified = true
                                }
                            }
                        }

                        val newEntry = ZipEntry(entry.name).apply { method = ZipEntry.DEFLATED }
                        zout.putNextEntry(newEntry)
                        zout.write(outData)
                        zout.closeEntry()
                        zin.closeEntry()
                        entry = zin.nextEntry
                    }
                }
            }

            if (modified) {
                apkFile.delete()
                tempFile.renameTo(apkFile)
                logListener("✓ Entire XML sections deleted using Regex matching.")
                return true
            } else {
                tempFile.delete()
                if (isPlainText) logListener("  Regex scanner found no matching XML sections.")
                return false
            }
        } catch (e: Exception) {
            tempFile.delete()
            return false
        }
    }
}