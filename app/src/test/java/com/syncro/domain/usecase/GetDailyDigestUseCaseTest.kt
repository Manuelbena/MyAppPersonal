package com.syncro.domain.usecase

import com.syncro.domain.model.AppSettings
import com.syncro.domain.model.AssistantSettings
import com.syncro.domain.model.DailyFocus
import com.syncro.domain.model.DigestMoment
import com.syncro.domain.model.User
import com.syncro.testutil.DAY
import com.syncro.testutil.FakeDailyFocusRepository
import com.syncro.testutil.FakeSettingsRepository
import com.syncro.testutil.FakeEventRepository
import com.syncro.testutil.FakeTaskRepository
import com.syncro.testutil.FakeUserRepository
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset

/**
 * [GetDailyDigestUseCase] reúne lo de hoy y lo de mañana para el aviso diario.
 * Riesgos: avisar sin sesión, tomar "hoy" del reloj del sistema en vez del inyectado y mezclar días.
 */
class GetDailyDigestUseCaseTest {

    private lateinit var tasks: FakeTaskRepository
    private lateinit var events: FakeEventRepository
    private lateinit var users: FakeUserRepository
    private val focus = FakeDailyFocusRepository()
    private val settings = FakeSettingsRepository()
    private lateinit var getDigest: GetDailyDigestUseCase

    @Before
    fun setUp() {
        tasks = FakeTaskRepository()
        events = FakeEventRepository()
        users = FakeUserRepository()
        val clock = Clock.fixed(DAY.atTime(8, 59).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        getDigest = GetDailyDigestUseCase(GetTimelineUseCase(tasks, events), users, focus, settings, clock)
    }

    @Test
    fun `invita a elegir prioridades solo si estan activadas y aun sin elegir`() = runTest {
        users.saveUser(User(email = "ana@example.com", name = "Ana", photoUrl = null))
        assertTrue(getDigest(DigestMoment.MORNING)!!.offerFocus)

        focus.saveFocus(DailyFocus(DAY, emptyList()))
        assertFalse(getDigest(DigestMoment.MORNING)!!.offerFocus)

        focus.focus.value = emptyMap()
        settings.current.value = AppSettings(assistant = AssistantSettings(focusEnabled = false))
        assertFalse(getDigest(DigestMoment.MORNING)!!.offerFocus)
    }

    @Test
    fun `es dia de nomina solo el dia configurado`() = runTest {
        users.saveUser(User(email = "ana@example.com", name = "Ana", photoUrl = null))
        assertFalse("Sin nómina en Ajustes", getDigest(DigestMoment.MORNING)!!.isPayday)

        settings.current.value = AppSettings(assistant = AssistantSettings(paydayDay = DAY.dayOfMonth))
        assertTrue(getDigest(DigestMoment.MORNING)!!.isPayday)

        settings.current.value = AppSettings(assistant = AssistantSettings(paydayDay = DAY.dayOfMonth + 1))
        assertFalse(getDigest(DigestMoment.MORNING)!!.isPayday)
    }

    @Test
    fun `con el repaso apagado en Ajustes la noche no lleva al chat`() = runTest {
        users.saveUser(User(email = "ana@example.com", name = "Ana", photoUrl = null))
        settings.current.value = AppSettings(assistant = AssistantSettings(leftoversEnabled = false))

        assertFalse(getDigest(DigestMoment.EVENING)!!.offerLeftovers)
    }

    @Test
    fun `sin sesion no hay aviso`() = runTest {
        tasks.insertTask(aTask())

        assertNull(getDigest(DigestMoment.MORNING))
    }

    @Test
    fun `separa lo de hoy de lo de manana y usa el nombre de pila`() = runTest {
        users.saveUser(User(email = "ana@example.com", name = "Ana García", photoUrl = null))
        tasks.insertTask(aTask(id = "hoy", date = DAY))
        tasks.insertTask(aTask(id = "manana", date = DAY.plusDays(1)))
        tasks.insertTask(aTask(id = "pasado", date = DAY.plusDays(2)))
        events.insertEvent(anEvent(id = "dentista", date = DAY.plusDays(1), startTime = at("09:00"), endTime = at("10:00")))

        val digest = getDigest(DigestMoment.EVENING)!!

        assertEquals(DigestMoment.EVENING, digest.moment)
        assertEquals(DAY, digest.date)
        assertEquals("Ana", digest.firstName)
        assertEquals(listOf("hoy"), digest.tasks.map { it.id })
        assertEquals(listOf("manana"), digest.tomorrowTasks.map { it.id })
        assertEquals(listOf("dentista"), digest.tomorrowEvents.map { it.id })
    }
}
