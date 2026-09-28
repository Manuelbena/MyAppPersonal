package com.syncro.domain.usecase

import com.syncro.domain.model.MoveTarget
import com.syncro.testutil.CallLog
import com.syncro.testutil.DAY
import com.syncro.testutil.FakeGoogleSyncRepository
import com.syncro.testutil.FakeSettingsRepository
import com.syncro.testutil.FakeTaskRepository
import com.syncro.testutil.aTask
import com.syncro.testutil.at
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset

/**
 * [GetLeftoverTasksUseCase] y [MoveTasksUseCase]: las tareas pendientes y pasarlas de día.
 * Riesgos: tomar la hora del sistema en vez del reloj inyectado, cambiar la hora al mover, y subir
 * a Google antes de guardar en local (sin conexión se perdería el cambio).
 */
class LeftoverTasksUseCasesTest {

    private val log = CallLog()
    private val tasks = FakeTaskRepository(log)
    private val google = FakeGoogleSyncRepository(log)

    private fun clockAt(hour: Int) = Clock.fixed(DAY.atTime(hour, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    @Test
    fun `las pendientes dependen de la hora del reloj inyectado`() = runTest {
        tasks.insertTask(aTask(id = "ayer", date = DAY.minusDays(1)))
        tasks.insertTask(aTask(id = "hoy", date = DAY))

        val morning = GetLeftoverTasksUseCase(tasks, FakeSettingsRepository(), clockAt(9))().first()
        val night = GetLeftoverTasksUseCase(tasks, FakeSettingsRepository(), clockAt(22))().first()

        assertEquals(listOf("ayer"), morning.tasks.map { it.id })
        assertEquals(MoveTarget.TODAY, morning.target)
        assertEquals(listOf("ayer", "hoy"), night.tasks.map { it.id })
    }

    @Test
    fun `mover guarda todas en local, mantiene la hora y despues sube a Google`() = runTest {
        tasks.insertTask(aTask(id = "t1", time = at("17:00")))
        tasks.insertTask(aTask(id = "t2", time = at("09:30")))
        log.calls.clear()

        MoveTasksUseCase(tasks, google)(listOf("t1", "t2"), DAY.plusDays(1))

        assertEquals(
            listOf("moveTask(t1, ${DAY.plusDays(1)})", "moveTask(t2, ${DAY.plusDays(1)})", "pushTask(t1)", "pushTask(t2)"),
            log.calls
        )
        assertEquals(at("17:00"), tasks.getTaskById("t1")!!.time)
        assertEquals(DAY.plusDays(1), tasks.getTaskById("t2")!!.date)
    }

    @Test
    fun `sin conexion las tareas se mueven igualmente en local`() = runTest {
        tasks.insertTask(aTask(id = "t1"))
        google.isOffline = true

        MoveTasksUseCase(tasks, google)(listOf("t1"), DAY.plusDays(1))

        assertEquals(DAY.plusDays(1), tasks.getTaskById("t1")!!.date)
    }
}
