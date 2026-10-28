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



package com.github.yumelira.yumebox.substore

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.github.yumelira.yumebox.substore.engine.NativeLibraryManager
import dev.oom_wg.purejoy.mlang.MLang
import timber.log.Timber

class SubStoreService : Service() {
    private var caseEngine: CaseEngine? = null
    private var isRunning = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoClose = Runnable { stopSelf() }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = SubStoreServiceController.requestFrom(intent)
        if (intent?.action == SubStoreServiceController.ACTION_UPDATE_AUTO_CLOSE) {
            if (isRunning) scheduleAutoClose(request.autoCloseMinutes) else stopSelf(startId)
            return START_NOT_STICKY
        }
        if (isRunning) return START_STICKY
        return runCatching {
            if (
                NetworkUtil.isPortInUse(request.frontendPort) ||
                NetworkUtil.isPortInUse(request.backendPort)
            ) {
                throw Exception(
                    MLang.Feature.SubStore.PortInUse.format(request.frontendPort, request.backendPort)
                )
            }

            if (!ensureJavetLibraryLoaded()) {
                throw Exception(MLang.Feature.SubStore.JavetLoadFailed)
            }

            val engine = CaseEngine(
                backendPort = request.backendPort,
                frontendPort = request.frontendPort,
                allowLan = request.allowLan,
            )
            caseEngine = engine

            if (!engine.isInitialized()) {
                throw Exception(MLang.Feature.SubStore.CaseEngineInitFailed)
            }

            engine.startServer()
            isRunning = true
            SubStoreServiceController.markRunning()
            scheduleAutoClose(request.autoCloseMinutes)

            START_STICKY
        }.getOrElse { e ->
            Timber.e(e, "Sub-Store service start failed")
            cleanupService()
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupService()
    }

    private fun ensureJavetLibraryLoaded(): Boolean = runCatching {
        NativeLibraryManager.initialize(applicationContext)
        val javetLibBaseName = "libjavet-node-android"

        if (!NativeLibraryManager.isLibraryAvailable(javetLibBaseName)) {
            val results = NativeLibraryManager.extractAllLibraries()
            if (results[javetLibBaseName] != true) {
                Timber.e("Javet extract failed")
                return false
            }
        }

        val loaded = NativeLibraryManager.loadJniLibrary(javetLibBaseName)
        if (!loaded) {
            Timber.e("Javet load failed: ${NativeLibraryManager.getLibraryStatus(javetLibBaseName)}")
        }
        loaded
    }.getOrElse { e ->
        Timber.e(e, "Javet load error")
        false
    }

    private fun cleanupService() {
        handler.removeCallbacks(autoClose)
        runCatching {
            caseEngine?.stopServer()
        }.onFailure { e ->
            Timber.e(e, "Failed to stop Sub-Store service")
        }
        caseEngine = null
        isRunning = false
        SubStoreServiceController.markStopped()
    }

    private fun scheduleAutoClose(minutes: Int?) {
        handler.removeCallbacks(autoClose)
        if (minutes != null) handler.postDelayed(autoClose, minutes * 60_000L)
    }
}
