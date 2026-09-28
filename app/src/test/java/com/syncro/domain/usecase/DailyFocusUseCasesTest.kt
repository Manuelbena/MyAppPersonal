package com.syncro.domain.usecase

import com.syncro.domain.model.DailyFocus
import com.syncro.domain.model.FocusedTask
import com.syncro.domain.model.TooManyFocusTasksException
import com.syncro.domain.model.focusCandidates
import com.syncro.testutil.DAY
import com.syncro.testutil.FakeDailyFocusRepository
import com.syncro.testutil.FakeSettingsRepository
import com.syncro.testutil.FakeTaskRepository
import com.syncro.testutil.aTask
import com.syncro.testutil.at
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset

/**
 * Prioridades del día: entre qué tareas elegir, guardarlas y mostrarlas.
 * Responsabilidades: ofrecer las tareas de hoy sin hacer (ordenadas por hora) solo antes de las
 * 21:00; guardar como mucho 3 (o "Hoy no"); mostrarlas en el orden elegido y como están ahora.
 * Riesgos: dejar elegir más de 3, preguntar por la noche, mostrar tareas pasadas a otro día y
 * tomar la hora del sistema en vez del reloj inyectado.
 */
class DailyFocusUseCasesTest {

    private val tasks = FakeTaskRepository()
    private val focus = FakeDailyFocusRepository()

    // region Candidatas

    @Test
    fun `se elige entre las tareas de hoy sin hacer, por hora`() {
        val all = listOf(
            aTask(id = "tarde", time = at("18:00")),
            aTask(id = "hecha", isCompleted = true),
            aTask(id = "pronto", time = at("08:00")),
            aTask(id = "manana", date = DAY.plusDays(1))
        )

        val candidates = focusCandidates(all, DAY.atTime(9, 0))!!

        assertEquals(DAY, candidates.date)
        assertEquals(listOf("pronto", "tarde"), candidates.tasks.map { it.id })
    }

    @Test
    fun `desde las 21 00 ya no se pregunta por las prioridades`() {
        assertNull(focusCandidates(listOf(aTask()), DAY.atTime(21, 0)))
    }

    @Test
    fun `con la noche a las 20 00 ya no se preguntan prioridades a esa hora`() {
        assertNull(focusCandidates(listOf(aTask()), DAY.atTime(20, 0), eveningFrom = at("20:00")))
    }

    @Test
    fun `las candidatas usan el reloj inyectado`() = runTest {
        tasks.insertTask(aTask(id = "t1"))
        val clock = Clock.fixed(DAY.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

        val candidates = GetFocusCandidatesUseCase(tasks, FakeSettingsRepository(), clock)().first()!!

        assertEquals(DAY, candidates.date)
        assertEquals(listOf("t1"), candidates.tasks.map { it.id })
    }

    // endregion

    // region Elegir

    @Test
    fun `se guardan las elegidas con su titulo`() = runTest {
        val result = ChooseDailyFocusUseCase(focus)(DAY, listOf(aTask(id = "t1", title = "Gimnasio")))

        assertTrue(result.isSuccess)
        assertEquals(DailyFocus(DAY, listOf(FocusedTask("t1", "Gimnasio"))), focus.getFocus(DAY).first())
    }

    @Test
    fun `mas de 3 falla sin guardar nada`() = runTest {
        val result = ChooseDailyFocusUseCase(focus)(DAY, (1..4).map { aTask(id = "t$it") })

        assertTrue(result.exceptionOrNull() is TooManyFocusTasksException)
        assertNull(focus.getFocus(DAY).first())
    }

    @Test
    fun `hoy no se guarda como lista vacia para no volver a preguntar`() = runTest {
        ChooseDailyFocusUseCase(focus)(DAY, emptyList())

        assertTrue(focus.getFocus(DAY).first()!!.skipped)
    }

    // endregion

    // region Mostrar

    @Test
    fun `se muestran en el orden elegido, con las hechas y sin las pasadas a otro dia`() = runTest {
        tasks.insertTask(aTask(id = "t1", title = "Gimnasio"))
        tasks.insertTask(aTask(id = "t2", title = "Banco", isCompleted = true))
        tasks.insertTask(aTask(id = "t3", title = "Regalo"))
        focus.saveFocus(DailyFocus(DAY, listOf(FocusedTask("t2", "Banco"), FocusedTask("t3", "Regalo"), FocusedTask("t1", "Gimnasio"))))
        tasks.moveTask("t3", DAY.plusDays(1))

        val shown = GetDailyFocusUseCase(focus, tasks)(DAY).first()

        assertEquals(listOf("t2", "t1"), shown.map { it.id })
    }

    // endregion
}
