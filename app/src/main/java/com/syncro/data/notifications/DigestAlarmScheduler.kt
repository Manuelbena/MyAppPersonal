package com.syncro.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.syncro.domain.model.DigestMoment
import com.syncro.domain.model.nextAfter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programa los avisos de las 9:00 y las 21:00 con AlarmManager. Se usa alarma exacta que suena
 * aunque el móvil esté en reposo (WorkManager podría retrasarla horas). Cada alarma programa la
 * siguiente al sonar, y [DigestRescheduleReceiver] las repone tras reiniciar o cambiar la hora.
 * Programar es idempotente: la misma alarma se sustituye, no se duplica.
 */
@Singleton
class DigestAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleAll() = DigestMoment.entries.forEach(::schedule)

    fun schedule(moment: DigestMoment) {
        val next = moment.nextAfter(LocalDateTime.now(clock))
        val triggerAt = next.atZone(clock.zone).toInstant().toEpochMilli()
        val intent = alarmIntent(moment)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            // Sin permiso de alarma exacta: una ventana corta tras la hora (puede esperar al reposo)
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, FALLBACK_WINDOW_MS, intent)
        }
    }

    private fun alarmIntent(moment: DigestMoment): PendingIntent = PendingIntent.getBroadcast(
        context,
        moment.ordinal,
        Intent(context, DigestAlarmReceiver::class.java)
            .setAction(DigestAlarmReceiver.ACTION_DIGEST)
            .putExtra(DigestAlarmReceiver.EXTRA_MOMENT, moment.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val FALLBACK_WINDOW_MS = 10 * 60 * 1000L
    }
}
