package com.syncro.data.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.DueReminder
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
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Alarmas y notificaciones de los avisos de tareas y eventos (Robolectric). Riesgos: más de una
 * alarma (avisos duplicados o huérfanos al editar), no cancelar la alarma al quitar el último
 * aviso, y una notificación sin los botones o sin el color de su categoría.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderNotificationsTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val alarmManager = app.getSystemService(AlarmManager::class.java)
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val clock = Clock.fixed(DAY.atTime(8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private fun on(hour: Int, minute: Int = 0) = DAY.atTime(hour, minute)
    private fun millis(at: LocalDateTime) = at.toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun reminder(id: String, at: LocalDateTime, isTask: Boolean = false) =
        DueReminder(id, isTask, "Gimnasio", at, at.plusMinutes(15), false, "Sala 2", ArgbColor(0xFFEC4899))

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `solo hay una alarma, la del siguiente aviso, y se mueve al reprogramar`() {
        val scheduler = ReminderAlarmScheduler(app, clock)

        scheduler.scheduleNext(listOf(reminder("pasado", on(7)), reminder("a", on(10)), reminder("b", on(9))))
        assertEquals(listOf(millis(on(9))), shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtMs })

        // Se borra el evento de las 9: la alarma pasa al de las 10, sin dejar la vieja
        scheduler.scheduleNext(listOf(reminder("a", on(10))))
        assertEquals(listOf(millis(on(10))), shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtMs })
    }

    @Test
    fun `sin avisos pendientes no queda ninguna alarma`() {
        val scheduler = ReminderAlarmScheduler(app, clock)
        scheduler.scheduleNext(listOf(reminder("a", on(10))))

        scheduler.scheduleNext(emptyList())

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `posponer vuelve a avisar en 10 minutos sin quitar la alarma del siguiente`() {
        val scheduler = ReminderAlarmScheduler(app, clock)
        scheduler.scheduleNext(listOf(reminder("a", on(12))))

        scheduler.snooze("b", 10)

        assertEquals(listOf(millis(on(8, 10)), millis(on(12))), shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtMs }.sorted())
    }

    @Test
    fun `la notificacion de un evento lleva su texto, su color y Posponer`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        ReminderNotifier(app, DigestNotifier(app), clock).show(reminder("e1", on(8)))

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals("Gimnasio", notification.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals("Empieza a las 8:15 · en 15 min · 📍 Sala 2", notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
        assertEquals(0xFFEC4899.toInt(), notification.color)
        assertEquals(listOf("Posponer 10 min"), notification.actions.map { it.title.toString() })
    }

    @Test
    fun `la de una tarea tambien deja marcarla como hecha`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        ReminderNotifier(app, DigestNotifier(app), clock).show(reminder("t1", on(8), isTask = true))

        val actions = shadowOf(notificationManager).allNotifications.single().actions.map { it.title.toString() }
        assertEquals(listOf("Posponer 10 min", "Hecha"), actions)
    }

    @Test
    fun `sin permiso de notificaciones no publica nada`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        ReminderNotifier(app, DigestNotifier(app), clock).show(reminder("e1", on(8)))

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }
}
