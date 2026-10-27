package com.github.yumelira.yumebox.service

import android.app.Application
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.util.PollingTimerSpec
import com.github.yumelira.yumebox.data.gateway.LogRecordGateway
import com.github.yumelira.yumebox.runtime.api.LogObserver
import com.github.yumelira.yumebox.runtime.api.LogSubscription
import com.github.yumelira.yumebox.runtime.client.access.RuntimeAccess
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** Adapts the original log recording UI to the upstream runtime log stream. */
class LogRecordServiceGateway : LogRecordGateway {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var subscription: LogSubscription? = null
    private val lock = Any()
    @Volatile override var isRecording = false
        private set
    @Volatile override var currentLogFileName: String? = null
        private set
    override val logPrefix = ""
    override val logSuffix = ".log"
    override val stopWaitSpec = PollingTimerSpec("log_record_stop", 50L, 0L)
    override fun getLogDir(application: Application) = File(application.filesDir, "logs").apply { mkdirs() }

    override fun start(application: Application) {
        synchronized(lock) {
            if (isRecording) return
            val file = File(getLogDir(application), "$logPrefix${System.currentTimeMillis()}$logSuffix")
            file.createNewFile()
            currentLogFileName = file.name
            isRecording = true
            job = scope.launch {
                try {
                    RuntimeAccess.connect(application)
                    val next = RuntimeAccess.core().subscribeLogs(object : LogObserver {
                        override fun newItem(log: LogMessage) {
                            synchronized(lock) {
                                if (!isRecording || currentLogFileName != file.name) return
                                val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(log.time)
                                file.appendText("[$time] [${log.level.name}] ${log.message}\n")
                            }
                        }
                        override fun onError(error: Throwable) { Timber.w(error, "Log stream interrupted") }
                    })
                    synchronized(lock) {
                        if (isRecording && currentLogFileName == file.name) subscription = next
                        else next.close()
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { synchronized(lock) { isRecording = false }; Timber.e(e) }
            }
        }
    }

    override fun stop(application: Application) {
        synchronized(lock) {
            isRecording = false
            job?.cancel(); job = null
            subscription?.close(); subscription = null
        }
    }
}
