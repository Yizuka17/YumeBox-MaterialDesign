/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeLira 2025 - Present
 *
 */



package com.github.yumelira.yumebox.substore.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.caoccao.javet.interop.loader.JavetLibLoader
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

@SuppressLint("StaticFieldLeak")
object NativeLibraryManager {
    private const val LIBS_DIR_NAME = "libs"
    private var libsBaseDir: File? = null
    private var context: Context? = null
    private var isInitialized = false

    enum class LibraryType {
        JNI_LOAD,
        PROCESS_EXEC
    }

    enum class LibrarySource {
        MAIN_APK,
        EXTENSION_APK
    }

    data class LibraryInfo(
        val name: String,
        val type: LibraryType,
        val source: LibrarySource,
        val packageName: String? = null,
        val version: String? = null
    )

    private val managedLibraries = mutableMapOf<String, LibraryInfo>()

    fun initialize(context: Context) {
        if (isInitialized) return

        this.context = context
        libsBaseDir = File(context.filesDir, LIBS_DIR_NAME)
        libsBaseDir?.mkdirs()
        registerDefaultLibraries()
        isInitialized = true
        extractAllLibraries()
    }

    @SuppressLint("StaticFieldLeak")
    private fun registerDefaultLibraries() {
        registerLibrary(
            LibraryInfo(
                name = "libjavet-node-android",
                type = LibraryType.JNI_LOAD,
                source = LibrarySource.MAIN_APK,
            )
        )
    }

    fun registerLibrary(info: LibraryInfo) {
        managedLibraries[info.name] = info
    }

    fun extractAllLibraries(): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        managedLibraries.forEach { (name, info) ->
            results[name] = extractLibrary(info)
        }
        return results
    }

    fun extractLibrary(info: LibraryInfo): Boolean {
        val targetDir = libsBaseDir ?: run {
            Timber.w("Library manager not initialized")
            return false
        }
        targetDir.mkdirs()
        val targetFile = File(targetDir, info.name)

        if (targetFile.exists() && targetFile.canRead()) {
            if (info.type == LibraryType.PROCESS_EXEC && !targetFile.canExecute()) {
                targetFile.setExecutable(true, false)
            }
            return true
        }

        return runCatching {
            when (info.source) {
                LibrarySource.MAIN_APK -> extractFromMainApk(info, targetFile)
                LibrarySource.EXTENSION_APK -> extractFromExtensionApk(info, targetFile)
            }
        }.getOrElse { e ->
            Timber.w(e, "Library extract failed: ${info.name}")
            false
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun extractFromMainApk(info: LibraryInfo, targetFile: File): Boolean {
        val apkPath = context?.applicationInfo?.sourceDir
            ?: throw RuntimeException("Context not initialized")

        val expected = if (info.name == "libjavet-node-android") "${info.name}.v.${JavetLibLoader.LIB_VERSION}.so" else info.name
        if (bundledPath(expected) != null) {
            actualLibraryNames[info.name] = expected
            return true
        }
        val actualTargetFile = File(targetFile.parentFile, expected)
        val abi = getSupportedAbi()
        ZipFile(apkPath).use { zip ->
            var libEntry = zip.getEntry("lib/$abi/$expected")
            if (libEntry == null) {
                val supportedAbis = Build.SUPPORTED_ABIS
                for (tryAbi in supportedAbis) {
                    libEntry = zip.getEntry("lib/$tryAbi/$expected")
                    if (libEntry != null) break
                }
            }

            if (libEntry == null) {
                throw RuntimeException("Library not found in APK: ${info.name}")
            }

            zip.getInputStream(libEntry).use { input ->
                if (actualTargetFile.exists()) check(actualTargetFile.delete())
                FileOutputStream(actualTargetFile).use { output ->
                    input.copyTo(output)
                }
            }

            actualLibraryNames[info.name] = expected
            actualTargetFile.setReadable(true, false)
            actualTargetFile.setReadOnly()
            if (info.type == LibraryType.PROCESS_EXEC) {
                actualTargetFile.setExecutable(true, false)
            }

            return true
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun extractFromExtensionApk(info: LibraryInfo, targetFile: File): Boolean {
        if (info.packageName == null) {
            throw RuntimeException("Package name required for extension APK source")
        }

        val extensionApk = getExtensionApk(info.packageName)
        if (extensionApk == null) {
            Timber.w("Extension APK missing: ${info.packageName}")
            return false
        }

        val abi = getSupportedAbi()

        ZipFile(extensionApk).use { zip ->
            // Loading another Javet JNI version aborts the VM before Kotlin can catch it.
            val expected = "${info.name}.v.${JavetLibLoader.LIB_VERSION}.so"
            val entry = (listOf(abi) + Build.SUPPORTED_ABIS).distinct()
                .firstNotNullOfOrNull { zip.getEntry("lib/$it/$expected") }

            if (entry == null) {
                actualLibraryNames.remove(info.name)
                Timber.w("Compatible library missing in extension APK: $expected")
                return false
            }

            val actualFileName = entry.name.substringAfterLast("/")
            val actualTargetFile = File(targetFile.parentFile, actualFileName)

            actualLibraryNames[info.name] = actualFileName

            zip.getInputStream(entry).use { input ->
                FileOutputStream(actualTargetFile).use { output ->
                    input.copyTo(output)
                }
            }

            actualTargetFile.setReadable(true, false)
            if (info.type == LibraryType.PROCESS_EXEC) {
                actualTargetFile.setExecutable(true, false)
            }

            return true
        }
    }

    private val actualLibraryNames = mutableMapOf<String, String>()

    private fun getExtensionApk(packageName: String): File? = runCatching {
        val pm = context?.packageManager ?: return null
        val info = pm.getApplicationInfo(packageName, 0)
        File(info.sourceDir)
    }.getOrNull()

    fun getLibraryPath(name: String): String? {
        if (!isInitialized) return null

        val actualName = actualLibraryNames[name] ?: name
        if (name == "libjavet-node-android" && actualName != "$name.v.${JavetLibLoader.LIB_VERSION}.so") return null
        if (managedLibraries[name]?.source == LibrarySource.MAIN_APK) {
            bundledPath(actualName)?.let { return it }
        }
        val libraryFile = File(libsBaseDir, actualName)
        return if (libraryFile.exists()) libraryFile.absolutePath else null
    }

    fun isLibraryAvailable(name: String): Boolean {
        val path = getLibraryPath(name) ?: return false
        val file = File(path)
        val info = managedLibraries[name]
        return when (info?.type) {
            LibraryType.JNI_LOAD -> file.exists() && file.canRead()
            LibraryType.PROCESS_EXEC -> file.exists() && file.canRead() && file.canExecute()
            null -> false
        }
    }

    private fun bundledPath(fileName: String): String? {
        val app = context ?: return null
        val installed = File(app.applicationInfo.nativeLibraryDir, fileName)
        if (installed.isFile && installed.canRead()) return installed.absolutePath
        return runCatching {
            val bridge = Class.forName("com.github.yumelira.yumebox.loader.PayloadRuntime", false, app.classLoader)
            (bridge.getMethod("findNativeLibrary", String::class.java).invoke(null, fileName) as? String)
                ?.takeIf { File(it).isFile && File(it).canRead() }
        }.getOrNull()
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun loadJniLibrary(name: String): Boolean {
        val info = managedLibraries[name]
        if (info?.type != LibraryType.JNI_LOAD) {
            return false
        }

        val path = getLibraryPath(name) ?: return false

        return runCatching {
            System.load(path)
            true
        }.getOrElse { e ->
            Timber.e(e, "JNI load failed: $name")
            false
        }
    }

    fun getLibraryStatus(name: String): String {
        if (!isInitialized) return "Library manager not initialized"
        val info = managedLibraries[name] ?: return "Library not registered: $name"
        val path = getLibraryPath(name)

        return when {
            path == null -> "Library not extracted: $name"
            !File(path).exists() -> "Library file not found: $path"
            info.type == LibraryType.PROCESS_EXEC && !File(path).canExecute() ->
                "Library exists but not executable: $path"

            info.type == LibraryType.JNI_LOAD && !File(path).canRead() ->
                "Library exists but not readable: $path"

            else -> "Library ready: $name (${info.type}) at $path"
        }
    }

    private fun getSupportedAbi(): String {
        val supportedABIs = Build.SUPPORTED_ABIS
        return when {
            supportedABIs.contains("arm64-v8a") -> "arm64-v8a"
            supportedABIs.contains("x86_64") -> "x86_64"
            supportedABIs.contains("armeabi-v7a") -> "armeabi-v7a"
            supportedABIs.contains("x86") -> "x86"
            else -> supportedABIs.firstOrNull() ?: "arm64-v8a"
        }
    }
}
