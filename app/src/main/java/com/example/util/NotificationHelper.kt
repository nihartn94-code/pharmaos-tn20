package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    const val CHANNEL_ID = "pharmaos_alerts"
    private const val CHANNEL_NAME = "PharmaOS Supply & Trade Alerts"
    private const val CHANNEL_DESC = "Notifications for orders, expiry alerts, recalls, and shortages"

    const val CHANNEL_DEPLETION_ID = "pharmaos_critical_depletion"
    private const val CHANNEL_DEPLETION_NAME = "Critical Stock Depletion Alerts"
    private const val CHANNEL_DEPLETION_DESC = "Immediate urgent push notifications when critical medicines reach stock depletion thresholds"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // General alerts channel
            val generalChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(generalChannel)

            // High-priority Critical Depletion Channel
            val depletionChannel = NotificationChannel(
                CHANNEL_DEPLETION_ID,
                CHANNEL_DEPLETION_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DEPLETION_DESC
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(depletionChannel)
        }
    }

    fun showSystemNotification(context: Context, notificationId: Int, title: String, message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            notificationManager.notify(notificationId, builder.build())
        } catch (_: Exception) {
            // Permission might not be granted; in-app notification still functions
        }
    }

    /**
     * Triggers high-priority automated push notification when a critical medicine reaches depletion threshold.
     */
    fun showCriticalStockNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        productId: Long? = null
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "inventory_dashboard")
                if (productId != null) {
                    putExtra("product_id", productId)
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_DEPLETION_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 300, 200, 300))

            notificationManager.notify(notificationId, builder.build())
        } catch (_: Exception) {
            // Permission might not be granted; in-app notification still functions
        }
    }
}
