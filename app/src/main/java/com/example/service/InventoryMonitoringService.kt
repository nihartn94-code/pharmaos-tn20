package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.PharmaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Service that manages automated inventory monitoring and triggers
 * push alerts when critical medicines approach stock depletion thresholds.
 */
class InventoryMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? PharmaApplication
        val monitorManager = app?.inventoryMonitorManager

        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                monitorManager?.stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_SCAN -> {
                serviceScope.launch {
                    monitorManager?.runManualDepletionCheck()
                }
            }
            ACTION_START_MONITORING, null -> {
                monitorManager?.startMonitoring()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START_MONITORING = "com.example.service.ACTION_START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.service.ACTION_STOP_MONITORING"
        const val ACTION_TRIGGER_SCAN = "com.example.service.ACTION_TRIGGER_SCAN"

        fun startService(context: Context) {
            try {
                val intent = Intent(context, InventoryMonitoringService::class.java).apply {
                    action = ACTION_START_MONITORING
                }
                context.startService(intent)
            } catch (_: Exception) {
                // Background start restriction fallback: In-process monitor still active via PharmaApplication
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, InventoryMonitoringService::class.java).apply {
                    action = ACTION_STOP_MONITORING
                }
                context.startService(intent)
            } catch (_: Exception) {
                // Graceful fallback
            }
        }

        fun triggerScan(context: Context) {
            try {
                val intent = Intent(context, InventoryMonitoringService::class.java).apply {
                    action = ACTION_TRIGGER_SCAN
                }
                context.startService(intent)
            } catch (_: Exception) {
                // Graceful fallback
            }
        }
    }
}
