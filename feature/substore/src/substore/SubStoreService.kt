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
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

package com.github.yumelira.yumebox.substore

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.github.yumelira.yumebox.substore.engine.NativeLibraryManager
import timber.log.Timber

class SubStoreService : Service() {
    private var caseEngine: CaseEngine? = null
    private var activeRequest: SubStoreServiceRequest? = null
    private var isRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = SubStoreServiceController.requestFrom(intent)
        if (isRunning && activeRequest == request) {
            SubStoreServiceController.markRunning()
            return START_STICKY
        }
        if (isRunning) {
            cleanupService()
        }

        return runCatching {
            if (
                NetworkUtil.isPortInUse(request.frontendPort) ||
                NetworkUtil.isPortInUse(request.backendPort)
            ) {
                error("端口 ${request.frontendPort} 或 ${request.backendPort} 已被占用")
            }

            if (!ensureJavetLibraryLoaded()) {
                error("Javet native 库加载失败")
            }

            lateinit var engine: CaseEngine
            engine =
                CaseEngine(
                    backendPort = request.backendPort,
                    frontendPort = request.frontendPort,
                    allowLan = request.allowLan,
                    onTerminated = {
                        mainHandler.post { handleEngineTermination(engine, startId) }
                    },
                )
            caseEngine = engine

            if (!engine.isInitialized()) {
                error("CaseEngine 初始化失败")
            }

            engine.startServer()
            activeRequest = request
            isRunning = true
            SubStoreServiceController.markRunning()

            START_STICKY
        }
            .getOrElse { error ->
                Timber.e(error, "Sub-Store service start failed")
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
        val javetLibBaseName = NativeLibraryManager.JAVET_LIBRARY_NAME

        if (!NativeLibraryManager.isLibraryAvailable(javetLibBaseName)) {
            Timber.e("Javet library is not downloaded")
            return false
        }

        val loaded = NativeLibraryManager.loadJniLibrary(javetLibBaseName)
        if (!loaded) {
            Timber.e(
                "Javet load failed: ${NativeLibraryManager.getLibraryStatus(javetLibBaseName)}"
            )
        }
        loaded
    }
        .getOrElse { error ->
            Timber.e(error, "Javet load error")
            false
        }

    private fun handleEngineTermination(engine: CaseEngine, startId: Int) {
        if (caseEngine !== engine) return
        caseEngine = null
        activeRequest = null
        isRunning = false
        SubStoreServiceController.markStopped()
        stopSelfResult(startId)
    }

    private fun cleanupService() {
        runCatching { caseEngine?.stopServer() }
            .onFailure { error -> Timber.e(error, "Failed to stop Sub-Store service") }
        caseEngine = null
        activeRequest = null
        isRunning = false
        SubStoreServiceController.markStopped()
    }
}
