package com.syncro.data.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.syncro.R
import com.syncro.domain.model.DueReminder
import com.syncro.domain.model.toMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publica el aviso de una tarea o evento: con el color de su categoría, "Posponer 10 min" y, en las
 * tareas, "Hecha" para completarla sin abrir la app. Tocarlo abre la app.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val digestNotifier: DigestNotifier,
    private val clock: Clock
) {
    @SuppressLint("MissingPermission") // comprobado en canNotify()
    fun show(reminder: DueReminder) {
        // Mismo criterio que el resumen diario: sin permiso o con la app silenciada, nada
        if (!digestNotifier.canNotify()) return
        ensureChannel()

        val message = reminder.toMessage(LocalDateTime.now(clock))
        val id = notificationId(reminder.itemId)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(reminder.color?.argb ?: ACCENT_COLOR)
            .setContentTitle(message.title)
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setContentIntent(openAppIntent(id))
            .setAutoCancel(true)
            .setCategory(if (reminder.isTask) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP)
            .addAction(0, "Posponer 10 min", actionIntent(ReminderActionReceiver.ACTION_SNOOZE, reminder, id))
        if (reminder.isTask) builder.addAction(0, "Hecha", actionIntent(ReminderActionReceiver.ACTION_DONE, reminder, id))

        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun cancel(itemId: String) = NotificationManagerCompat.from(context).cancel(notificationId(itemId))

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Avisos de tareas y eventos", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Los avisos que pones al crear una tarea o un evento"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openAppIntent(requestCode: Int): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return null
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(action: String, reminder: DueReminder, notificationId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        // Distinto por botón y por aviso: si no, Android reutilizaría el mismo PendingIntent
        31 * notificationId + action.hashCode(),
        Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderAlarmReceiver.EXTRA_ITEM_ID, reminder.itemId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notificationId(itemId: String) = NOTIFICATION_ID_BASE + (itemId.hashCode() and 0xFFFFF)

    private companion object {
        const val CHANNEL_ID = "item_reminders"
        const val GROUP = "com.syncro.REMINDERS"
        const val NOTIFICATION_ID_BASE = 100_000
        const val ACCENT_COLOR = 0xFF22D3EE.toInt()
    }
}
