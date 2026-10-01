package dev.cfmobile.app.core.transfers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.cfmobile.app.R

object TransferNotifications {
    const val CHANNEL_PROGRESS = "transfers"
    const val CHANNEL_RESULTS = "transfer_results"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, "Transfers in progress", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_RESULTS, "Transfer results", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun progress(context: Context, title: String, transferred: Long, total: Long) =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_transfer)
            .setContentTitle(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .apply {
                if (total > 0) {
                    val pct = ((transferred * 100) / total).toInt().coerceIn(0, 100)
                    setProgress(100, pct, false).setContentText("$pct%")
                } else setProgress(0, 0, true)
            }
            .build()

    fun result(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_stat_transfer)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}
