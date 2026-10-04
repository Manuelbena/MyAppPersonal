package com.syncro.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.syncro.domain.model.DueReminder
import com.syncro.domain.model.nextAfter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alarmas de los avisos de tareas y eventos. Solo hay una programada: la del siguiente aviso.
 * Cuando suena, [ReminderAlarmReceiver] publica los que tocan y programa el siguiente; y cada vez
 * que cambia una tarea o evento con aviso, SyncroApp la vuelve a programar. Así no hay alarmas
 * huérfanas de avisos borrados o cambiados. Mismo tipo de alarma exacta que el resumen diario.
 */
@Singleton
class ReminderAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /** Programa la alarma del siguiente aviso de [reminders], o la quita si no queda ninguno (idempotente). */
    fun scheduleNext(reminders: List<DueReminder>) {
        val next = reminders.nextAfter(LocalDateTime.now(clock))
        val intent = PendingIntent.getBroadcast(
            context,
            REQUEST_NEXT,
            Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_REMINDER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (next == null) {
            alarmManager.cancel(intent)
            return
        }
        setAlarm(next.at, intent)
    }

    /** "Posponer": vuelve a avisar de [itemId] dentro de [minutes] minutos. */
    fun snooze(itemId: String, minutes: Long) {
        val intent = PendingIntent.getBroadcast(
            context,
            itemId.hashCode(),
            Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(ReminderAlarmReceiver.ACTION_SNOOZED)
                .putExtra(ReminderAlarmReceiver.EXTRA_ITEM_ID, itemId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(LocalDateTime.now(clock).plusMinutes(minutes), intent)
    }

    private fun setAlarm(at: LocalDateTime, intent: PendingIntent) {
        val triggerAt = at.atZone(clock.zone).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            // Sin permiso de alarma exacta: una ventana corta tras la hora
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, FALLBACK_WINDOW_MS, intent)
        }
    }

    private companion object {
        const val REQUEST_NEXT = 0
        const val FALLBACK_WINDOW_MS = 60 * 1000L
    }
}
