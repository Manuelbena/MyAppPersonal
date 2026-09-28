package com.syncro.data.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.syncro.R
import com.syncro.domain.model.DigestMessage
import com.syncro.domain.model.DigestMoment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Publica los avisos diarios. Tocar la notificación abre la app. */
@Singleton
class DigestNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Si el usuario no ha dado permiso (Android 13+) o ha silenciado la app, no se publica nada. */
    fun canNotify(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // comprobado en canNotify()
    fun show(moment: DigestMoment, message: DigestMessage) {
        if (!canNotify()) return
        ensureChannel()

        // Desplegada: la frase principal y, debajo, el detalle (tareas, eventos, mañana)
        val expanded = buildString {
            append(message.text)
            if (message.lines.isNotEmpty()) append("\n\n").append(message.lines.joinToString("\n"))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT_COLOR)
            .setContentTitle(message.title)
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setContentIntent(openAppIntent(moment))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Un id por momento: el de la noche no pisa al de la mañana si sigue sin leer
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + moment.ordinal, notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Resumen diario", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Lo que te espera cada mañana (9:00) y cómo ha ido el día (21:00)"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openAppIntent(moment: DigestMoment): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return null
        return PendingIntent.getActivity(
            context,
            moment.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "daily_digest"
        const val NOTIFICATION_ID_BASE = 9_000
        private const val ACCENT_COLOR = 0xFF22D3EE.toInt() // Cyan400, el acento de la app
    }
}
