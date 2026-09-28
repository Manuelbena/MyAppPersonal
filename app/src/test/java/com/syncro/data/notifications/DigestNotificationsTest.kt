package com.syncro.data.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.syncro.domain.model.DigestMessage
import com.syncro.domain.model.DigestMoment
import com.syncro.domain.model.DigestSettings
import com.syncro.testutil.DAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Programación de las alarmas diarias y publicación de la notificación (Robolectric).
 * Riesgos: alarmas a otra hora o duplicadas al reprogramar, y notificar sin permiso.
 */
@RunWith(RobolectricTestRunner::class)
class DigestNotificationsTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val alarmManager = app.getSystemService(AlarmManager::class.java)
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val clock = Clock.fixed(DAY.atTime(8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private fun millisAt(hour: Int, dayOffset: Long = 0) =
        DAY.plusDays(dayOffset).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `programa los avisos de las 9 y las 21 sin duplicarlos al reprogramar`() {
        val scheduler = DigestAlarmScheduler(app, clock)

        scheduler.scheduleAll(DigestSettings())
        scheduler.scheduleAll(DigestSettings()) // p. ej. al abrir la app otra vez

        val triggers = shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtMs }.sorted()
        assertEquals(listOf(millisAt(9), millisAt(21)), triggers)
    }

    @Test
    fun `usa la hora elegida en Ajustes`() {
        DigestAlarmScheduler(app, clock).scheduleAll(DigestSettings(morningTime = LocalTime.of(7, 30), eveningTime = LocalTime.of(20, 0)))

        val triggers = shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtMs }.sorted()
        // A las 8:00 (reloj del test) las 7:30 ya han pasado: el de la mañana queda para mañana
        val expectedMorning = DAY.plusDays(1).atTime(7, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(listOf(millisAt(20), expectedMorning), triggers)
    }

    @Test
    fun `apagar un aviso en Ajustes cancela su alarma`() {
        val scheduler = DigestAlarmScheduler(app, clock)
        scheduler.scheduleAll(DigestSettings())

        scheduler.scheduleAll(DigestSettings(eveningEnabled = false))

        assertEquals(listOf(millisAt(9)), shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtMs })
    }

    @Test
    fun `pasada la hora de la manana el aviso es para manana`() {
        val lateClock = Clock.fixed(DAY.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

        DigestAlarmScheduler(app, lateClock).schedule(DigestMoment.MORNING, DigestSettings())

        assertEquals(millisAt(9, dayOffset = 1), shadowOf(alarmManager).scheduledAlarms.single().triggerAtMs)
    }

    @Test
    fun `con permiso publica la notificacion con el resumen desplegable`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        DigestNotifier(app).show(
            DigestMoment.EVENING,
            DigestMessage("¡Día completado, Ana! 🎉", "Has hecho las 2 tareas de hoy.", listOf("🔜 Mañana tienes la agenda libre."))
        )

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals("¡Día completado, Ana! 🎉", notification.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals(
            "Has hecho las 2 tareas de hoy.\n\n🔜 Mañana tienes la agenda libre.",
            notification.extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT).toString()
        )
        assertEquals(DigestNotifier.CHANNEL_ID, notification.channelId)
    }

    @Test
    fun `sin permiso de notificaciones no publica nada`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        DigestNotifier(app).show(DigestMoment.MORNING, DigestMessage("Hola", "Texto", emptyList()))

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }
}
