package com.syncro.presentation.home

import android.content.Intent
import androidx.compose.ui.graphics.Color
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.syncro.domain.model.AppSettings
import com.syncro.domain.model.MAIN_ACCOUNT_ID
import com.syncro.domain.usecase.ObserveSavingsAccountsUseCase
import com.syncro.testutil.FakeSavingsAccountRepository
import com.syncro.domain.model.AssistantSettings
import com.syncro.domain.model.User
import com.syncro.domain.usecase.DeleteEventUseCase
import com.syncro.domain.usecase.DeleteTaskUseCase
import com.syncro.domain.usecase.DeleteNoteUseCase
import com.syncro.domain.model.DailyFocus
import com.syncro.domain.model.FocusedTask
import com.syncro.domain.usecase.GetDailyFocusUseCase
import com.syncro.domain.usecase.GetDailyQuoteUseCase
import com.syncro.domain.usecase.HideDailyQuoteUseCase
import com.syncro.domain.usecase.ObserveDailyQuoteUseCase
import com.syncro.domain.usecase.ObserveSyncStateUseCase
import com.syncro.domain.usecase.ObserveHomeSavingsUseCase
import com.syncro.domain.usecase.GetMonthMovementsUseCase
import com.syncro.domain.usecase.GetBudgetsUseCase
import com.syncro.domain.model.Budget
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.usecase.GetEventsInRangeUseCase
import com.syncro.domain.usecase.GetTasksInRangeUseCase
import com.syncro.domain.usecase.UndoDeleteEventUseCase
import com.syncro.domain.usecase.UndoDeleteNoteUseCase
import com.syncro.domain.usecase.UndoDeleteTaskUseCase
import com.syncro.domain.model.DayMark
import com.syncro.domain.usecase.GetLocalUserUseCase
import com.syncro.domain.usecase.GetNotesUseCase
import com.syncro.domain.usecase.GetTimelineUseCase
import com.syncro.domain.usecase.PushPendingChangesUseCase
import com.syncro.domain.usecase.SaveEventUseCase
import com.syncro.domain.usecase.SaveNoteUseCase
import com.syncro.domain.usecase.SaveTaskUseCase
import com.syncro.domain.usecase.SyncGoogleCalendarUseCase
import com.syncro.domain.usecase.SyncGoogleTasksUseCase
import com.syncro.domain.usecase.ToggleEventCompletionUseCase
import com.syncro.domain.usecase.ToggleSubtaskCompletionUseCase
import com.syncro.domain.usecase.ToggleTaskCompletionUseCase
import com.syncro.testutil.CallLog
import com.syncro.testutil.DAY
import com.syncro.testutil.FakeConnectivityRepository
import com.syncro.testutil.FakeMovementRepository
import com.syncro.testutil.FakeBudgetRepository
import com.syncro.testutil.aMovement
import com.syncro.testutil.FakeDailyFocusRepository
import com.syncro.testutil.FakeDailyQuoteRepository
import com.syncro.testutil.FakeSettingsRepository
import com.syncro.testutil.FakeEventRepository
import com.syncro.testutil.FakeGoogleSyncRepository
import com.syncro.testutil.FakeRepeatSeriesRepository
import com.syncro.domain.usecase.GenerateRepeatsUseCase
import com.syncro.testutil.FakeNoteRepository
import com.syncro.testutil.FakeTaskRepository
import com.syncro.testutil.FakeUserRepository
import com.syncro.testutil.MainDispatcherRule
import com.syncro.testutil.MutableClock
import com.syncro.testutil.aTask
import com.syncro.testutil.aNote
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Plan de pruebas de [HomeViewModel]: la lógica de la pantalla de inicio, sin dibujarla.
 *
 * Riesgos: descargar de Google antes de subir lo pendiente (la descarga pisaría los cambios),
 * pedir permisos de Google varias veces seguidas, mostrar datos de otro día o de otro usuario,
 * y fallar sin avisar al guardar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) // Robolectric: el ViewModel usa android.util.Log e Intent
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = MutableClock(DAY.atTime(9, 0).toInstant(ZoneOffset.UTC))
    private val connectivity = FakeConnectivityRepository()
    private val movements = FakeMovementRepository()
    private val budgets = FakeBudgetRepository()
    private val accounts = FakeSavingsAccountRepository()
    private val settings = FakeSettingsRepository()
    private val quotes = FakeDailyQuoteRepository()

    private lateinit var log: CallLog
    private lateinit var tasks: FakeTaskRepository
    private lateinit var events: FakeEventRepository
    private lateinit var google: FakeGoogleSyncRepository
    private val series = FakeRepeatSeriesRepository()
    private lateinit var notes: FakeNoteRepository
    private lateinit var users: FakeUserRepository
    private val focus = FakeDailyFocusRepository()

    @Before
    fun setUp() {
        log = CallLog()
        tasks = FakeTaskRepository(log)
        events = FakeEventRepository(log)
        google = FakeGoogleSyncRepository(log)
        notes = FakeNoteRepository()
        users = FakeUserRepository()
    }

    private fun createViewModel() = HomeViewModel(
        getTimelineUseCase = GetTimelineUseCase(tasks, events),
        saveTaskUseCase = SaveTaskUseCase(tasks, google, series, GenerateRepeatsUseCase(series, tasks, events, google, clock)),
        saveEventUseCase = SaveEventUseCase(events, google, series, GenerateRepeatsUseCase(series, tasks, events, google, clock)),
        toggleTaskCompletionUseCase = ToggleTaskCompletionUseCase(tasks, google),
        toggleSubtaskCompletionUseCase = ToggleSubtaskCompletionUseCase(events, google),
        toggleEventCompletionUseCase = ToggleEventCompletionUseCase(events, google),
        deleteEventUseCase = DeleteEventUseCase(events, google, series),
        deleteTaskUseCase = DeleteTaskUseCase(tasks, google, series),
        syncGoogleTasksUseCase = SyncGoogleTasksUseCase(google),
        syncGoogleCalendarUseCase = SyncGoogleCalendarUseCase(google),
        pushPendingChangesUseCase = PushPendingChangesUseCase(google),
        getNotesUseCase = GetNotesUseCase(notes),
        getLocalUserUseCase = GetLocalUserUseCase(users),
        observeDailyQuoteUseCase = ObserveDailyQuoteUseCase(settings, quotes, GetDailyQuoteUseCase(clock), clock),
        hideDailyQuoteUseCase = HideDailyQuoteUseCase(quotes, clock),
        saveNoteUseCase = SaveNoteUseCase(notes, clock),
        deleteNoteUseCase = DeleteNoteUseCase(notes),
        getDailyFocusUseCase = GetDailyFocusUseCase(focus, tasks),
        observeSyncStateUseCase = ObserveSyncStateUseCase(connectivity, google),
        clock = clock,
        getTasksInRangeUseCase = GetTasksInRangeUseCase(tasks),
        getEventsInRangeUseCase = GetEventsInRangeUseCase(events),
        undoDeleteTaskUseCase = UndoDeleteTaskUseCase(tasks, google),
        undoDeleteEventUseCase = UndoDeleteEventUseCase(events, google),
        undoDeleteNoteUseCase = UndoDeleteNoteUseCase(notes),
        observeHomeSavingsUseCase = ObserveHomeSavingsUseCase(settings, GetMonthMovementsUseCase(movements, settings), GetBudgetsUseCase(budgets), ObserveSavingsAccountsUseCase(accounts)),
        generateRepeatsUseCase = GenerateRepeatsUseCase(series, tasks, events, google, clock)
    )

    /** Recoge los efectos de un solo uso (snackbars, petición de permisos) que emite el ViewModel. */
    private fun TestScope.collectEffects(viewModel: HomeViewModel): List<HomeEffect> {
        val effects = mutableListOf<HomeEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.toList(effects) }
        return effects
    }

    // region Cabecera

    @Test
    fun `el saludo usa el nombre de pila del usuario con sesion`() = runTest {
        users.saveUser(User(email = "ana@example.com", name = "Ana María López", photoUrl = null))

        val viewModel = createViewModel()

        assertEquals("Ana", viewModel.uiState.value.userName)
    }

    @Test
    fun `si Google no da nombre el saludo queda sin nombre`() = runTest {
        // Regresión: el nombre estaba fijo en el código y cualquier cuenta veía el mismo saludo
        users.saveUser(User(email = "ana@example.com", name = "", photoUrl = null))

        assertEquals("", createViewModel().uiState.value.userName)
    }

    @Test
    fun `las prioridades del dia se muestran en el orden elegido y sin las pasadas a otro dia`() = runTest {
        val viewModel = createViewModel()
        val day = viewModel.uiState.value.selectedDate
        tasks.insertTask(aTask(id = "t1", title = "Gimnasio", date = day))
        tasks.insertTask(aTask(id = "t2", title = "Llamar al banco", date = day))
        tasks.insertTask(aTask(id = "t3", title = "Regalo", date = day.plusDays(1)))

        focus.saveFocus(
            DailyFocus(day, listOf(FocusedTask("t2", "Llamar al banco"), FocusedTask("t1", "Gimnasio"), FocusedTask("t3", "Regalo")))
        )

        assertEquals(listOf("t2", "t1"), viewModel.uiState.value.focusTasks.map { it.id })
    }

    @Test
    fun `la frase del dia es la del caso de uso para hoy`() = runTest {
        val expected = GetDailyQuoteUseCase(clock)()

        val state = createViewModel().uiState.value

        assertEquals(expected, state.quote)
    }

    @Test
    fun `cerrar la frase la oculta hoy, y manana vuelve`() = runTest {
        val viewModel = createViewModel()

        viewModel.hideDailyQuote()

        assertEquals(null, viewModel.uiState.value.quote)
        assertEquals(DAY, quotes.hiddenOn.value)
        // Ocultada ayer: hoy vuelve a salir
        quotes.hiddenOn.value = DAY.minusDays(1)
        assertEquals(GetDailyQuoteUseCase(clock)(), viewModel.uiState.value.quote)
    }

    @Test
    fun `con la frase apagada en Ajustes no sale`() = runTest {
        settings.current.value = AppSettings(assistant = AssistantSettings(dailyQuoteEnabled = false))

        assertEquals(null, createViewModel().uiState.value.quote)
    }

    // endregion

    // region Sincronización

    @Test
    fun `al abrir se suben los cambios pendientes antes de descargar de Google`() = runTest {
        createViewModel()

        val firstDownload = log.calls.indexOfFirst { it.startsWith("sync") }
        assertTrue("Debe haber sincronizado", firstDownload >= 0)
        assertTrue(
            "Lo pendiente se sube antes que cualquier descarga: ${log.calls}",
            log.calls.indexOf("pushPendingChanges") in 0 until firstDownload
        )
    }

    @Test
    fun `al terminar la sincronizacion deja de mostrarse la carga`() = runTest {
        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `si faltan permisos de Google se piden una sola vez aunque fallen tareas y calendario`() = runTest {
        // Una sola sincronización (la de abrir la pantalla) en la que fallan las dos descargas
        google.syncFailure = UserRecoverableAuthIOException(UserRecoverableAuthException("Sin permiso", Intent()))

        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        assertEquals(1, effects.count { it is HomeEffect.LaunchAuthRecovery })
    }

    // endregion

    // region Aviso de sincronización

    @Test
    fun `con todo subido y conexion no hay aviso`() = runTest {
        assertEquals(null, createViewModel().uiState.value.syncNotice)
    }

    @Test
    fun `sin conexion se avisa enseguida con los cambios que esperan`() = runTest {
        connectivity.online.value = false
        google.pendingChanges.value = 2

        val viewModel = createViewModel()

        assertEquals(SyncNotice.Offline(pendingChanges = 2), viewModel.uiState.value.syncNotice)
    }

    @Test
    fun `si falla la sincronizacion con conexion se avisa y al reintentar con exito desaparece`() = runTest {
        google.syncFailure = IOException("Google no responde")
        val viewModel = createViewModel()

        assertEquals(SyncNotice.SyncFailed, viewModel.uiState.value.syncNotice)

        google.syncFailure = null
        viewModel.syncFromGoogle()

        assertEquals(null, viewModel.uiState.value.syncNotice)
    }

    @Test
    fun `si faltan permisos no se muestra el aviso de fallo`() = runTest {
        google.syncFailure = UserRecoverableAuthIOException(UserRecoverableAuthException("Sin permiso", Intent()))

        assertEquals(null, createViewModel().uiState.value.syncNotice)
    }

    @Test
    fun `los cambios pendientes solo se avisan si siguen sin subir pasado un rato`() = runTest {
        val viewModel = createViewModel()

        // Un cambio normal está pendiente un instante mientras se sube: no debe parpadear el aviso
        google.pendingChanges.value = 1
        runCurrent()
        assertEquals(null, viewModel.uiState.value.syncNotice)
        google.pendingChanges.value = 0
        advanceTimeBy(5_000)
        assertEquals(null, viewModel.uiState.value.syncNotice)

        // Si se queda pendiente, se avisa
        google.pendingChanges.value = 3
        advanceTimeBy(10_001)
        assertEquals(SyncNotice.PendingChanges(3), viewModel.uiState.value.syncNotice)
    }

    @Test
    fun `al volver la conexion se sincroniza solo`() = runTest {
        connectivity.online.value = false
        createViewModel()
        log.calls.clear()

        connectivity.online.value = true

        assertTrue("Debe subir lo pendiente al reconectar: ${log.calls}", log.calls.contains("pushPendingChanges"))
        assertTrue(log.calls.any { it.startsWith("syncTasks") })
    }

    // endregion

    // region Día seleccionado

    @Test
    fun `al cambiar de dia se muestran los elementos de ese dia`() = runTest {
        tasks.insertTask(aTask(title = "Hoy", date = DAY))
        tasks.insertTask(aTask(title = "Mañana", date = DAY.plusDays(1)))
        val viewModel = createViewModel()

        viewModel.onDaySelected(DAY.plusDays(1))

        val titles = viewModel.uiState.value.timelineItems.map { (it as com.syncro.domain.model.SyncroItem.Task).title }
        assertEquals(listOf("Mañana"), titles)
    }

    @Test
    fun `al cambiar de dia se sincroniza ese dia con Google Calendar`() = runTest {
        val viewModel = createViewModel()

        viewModel.onDaySelected(DAY.plusDays(3))

        assertTrue(log.calls.contains("syncCalendar(${DAY.plusDays(3)}..${DAY.plusDays(3)})"))
    }

    @Test
    fun `hoy sale del reloj inyectado`() = runTest {
        val state = createViewModel().uiState.value

        assertEquals(DAY, state.today)
        assertEquals(DAY, state.selectedDate)
    }

    @Test
    fun `al pasar la medianoche con la app abierta quien miraba hoy pasa al nuevo hoy`() = runTest {
        // Regresión: hoy se calculaba una vez al abrir y la Home se quedaba en ayer toda la mañana
        tasks.insertTask(aTask(title = "De mañana", date = DAY.plusDays(1)))
        val viewModel = createViewModel()

        clock.instant = DAY.plusDays(1).atTime(0, 1).toInstant(ZoneOffset.UTC)
        viewModel.refreshToday()

        val state = viewModel.uiState.value
        assertEquals(DAY.plusDays(1), state.today)
        assertEquals(DAY.plusDays(1), state.selectedDate)
        assertEquals(listOf("De mañana"), state.timelineItems.map { (it as com.syncro.domain.model.SyncroItem.Task).title })
    }

    @Test
    fun `al pasar la medianoche quien miraba otro dia se queda en ese dia`() = runTest {
        val viewModel = createViewModel()
        viewModel.onDaySelected(DAY.plusDays(5))

        clock.instant = DAY.plusDays(1).atTime(0, 1).toInstant(ZoneOffset.UTC)
        viewModel.refreshToday()

        assertEquals(DAY.plusDays(1), viewModel.uiState.value.today)
        assertEquals(DAY.plusDays(5), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `el boton Hoy lleva al hoy del reloj aunque haya cambiado el dia`() = runTest {
        val viewModel = createViewModel()
        viewModel.onDaySelected(DAY.minusDays(3))

        clock.instant = DAY.plusDays(1).atTime(8, 0).toInstant(ZoneOffset.UTC)
        viewModel.goToToday()

        assertEquals(DAY.plusDays(1), viewModel.uiState.value.selectedDate)
    }

    // endregion

    // region Guardar: el usuario siempre recibe respuesta

    @Test
    fun `guardar un evento invalido muestra el motivo`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.saveDetailedEvent(
            title = "Cena", description = null, location = null,
            date = DAY, endDate = DAY, startTime = at("21:30"), endTime = at("01:00"),
            categoryText = "Ocio", categoryColor = Color.Yellow, priority = null, subtasks = emptyList()
        )

        assertEquals(
            listOf(HomeEffect.ShowSnackbar("El evento no puede terminar antes de empezar")),
            effects
        )
        assertTrue(events.events.value.isEmpty())
    }

    @Test
    fun `crear una tarea confirma que se ha creado`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.saveQuickTask(title = "Comprar pan", description = "", date = DAY)

        assertEquals(listOf(HomeEffect.ShowSnackbar("Tarea creada correctamente")), effects)
        assertEquals(1, tasks.tasks.value.size)
    }

    @Test
    fun `una tarea nueva solo tiene dia y se guarda de todo el dia sin categoria`() = runTest {
        val viewModel = createViewModel()

        viewModel.saveQuickTask(title = "Comprar pan", description = "", date = DAY)

        val task = tasks.tasks.value.values.single()
        assertEquals(LocalTime.MIDNIGHT, task.time)
        assertTrue(task.isAllDay)
        assertEquals(null, task.categoryText)
    }

    // endregion

    // region Deshacer

    @Test
    fun `borrar una tarea la quita al momento pero no la borra en Google mientras se puede deshacer`() = runTest {
        tasks.insertTask(aTask(id = "t1", date = DAY))
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)
        log.calls.clear()

        viewModel.deleteTask("t1")

        assertTrue(viewModel.uiState.value.timelineItems.isEmpty())
        assertFalse("No se sube hasta que pasa el aviso: ${log.calls}", log.calls.contains("pushTask(t1)"))
        assertEquals(listOf(HomeEffect.OfferUndo("Tarea eliminada", HomeUndo.DeletedTask("t1"))), effects)
    }

    @Test
    fun `deshacer el borrado de una tarea la recupera tal cual y la sube`() = runTest {
        val task = aTask(id = "t1", title = "Llamar al banco", date = DAY)
        tasks.insertTask(task)
        val viewModel = createViewModel()
        viewModel.deleteTask("t1")

        viewModel.undo(HomeUndo.DeletedTask("t1"))

        assertEquals(listOf(task), viewModel.uiState.value.timelineItems)
        assertTrue(log.calls.contains("pushTask(t1)"))
    }

    @Test
    fun `si no se deshace, al pasar el aviso el borrado se sube a Google`() = runTest {
        events.insertEvent(anEvent(id = "e1", date = DAY))
        val viewModel = createViewModel()
        viewModel.deleteEvent("e1")
        log.calls.clear()

        viewModel.undoExpired(HomeUndo.DeletedEvent("e1"))

        assertEquals(listOf("pushPendingChanges"), log.calls)
    }

    @Test
    fun `si el borrado ya se subio, deshacer lo explica en vez de fallar en silencio`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        // Otra sincronización ya lo borró del todo: no queda nada que recuperar
        viewModel.undo(HomeUndo.DeletedEvent("e-ya-subido"))

        assertEquals(listOf(HomeEffect.ShowSnackbar("Ya se había borrado en Google")), effects)
    }

    @Test
    fun `deshacer el borrado de una nota la vuelve a guardar igual`() = runTest {
        val note = aNote(id = "n1")
        notes.insertNote(note)
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.deleteNote(note)
        assertEquals(listOf(HomeEffect.OfferUndo("Nota eliminada", HomeUndo.DeletedNote(note))), effects)
        viewModel.undo(HomeUndo.DeletedNote(note))

        assertEquals(listOf(note), viewModel.uiState.value.notes)
    }

    @Test
    fun `completar una tarea ofrece deshacer y descompletarla no`() = runTest {
        tasks.insertTask(aTask(id = "t1", date = DAY))
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.toggleTaskCompletion("t1")
        assertEquals(listOf(HomeEffect.OfferUndo("Tarea completada", HomeUndo.CompletedTask("t1"))), effects)

        viewModel.undo(HomeUndo.CompletedTask("t1"))
        assertFalse(tasks.tasks.value.getValue("t1").isCompleted)

        viewModel.toggleTaskCompletion("t1")
        viewModel.toggleTaskCompletion("t1")
        assertEquals("Descompletar no ofrece deshacer", 2, effects.size)
    }

    // endregion

    // region Puntos de la tira de la semana

    @Test
    fun `la tira marca los dias con algo pendiente y los que tienen todo hecho`() = runTest {
        tasks.insertTask(aTask(date = DAY.plusDays(1)))
        tasks.insertTask(aTask(date = DAY.minusDays(2), isCompleted = true))
        events.insertEvent(anEvent(date = DAY.plusDays(3)))

        val marks = createViewModel().uiState.value.dayMarks

        assertEquals(
            mapOf(DAY.plusDays(1) to DayMark.PENDING, DAY.minusDays(2) to DayMark.DONE, DAY.plusDays(3) to DayMark.PENDING),
            marks
        )
    }

    @Test
    fun `los puntos se actualizan al crear o completar`() = runTest {
        val viewModel = createViewModel()
        tasks.insertTask(aTask(id = "t1", date = DAY))
        assertEquals(DayMark.PENDING, viewModel.uiState.value.dayMarks[DAY])

        viewModel.toggleTaskCompletion("t1")

        assertEquals(DayMark.DONE, viewModel.uiState.value.dayMarks[DAY])
    }

    // endregion

    // region Ahorros en Inicio

    @Test
    fun `los ahorros no salen en Inicio si no se activan`() = runTest {
        movements.movements.value = mapOf("m1" to aMovement(id = "m1"))

        assertEquals(null, createViewModel().uiState.value.savings)
    }

    @Test
    fun `activados, salen el balance del mes y los presupuestos justos`() = runTest {
        settings.current.value = AppSettings(assistant = AssistantSettings(homeSavingsEnabled = true))
        movements.movements.value = mapOf(
            "nomina" to aMovement(id = "nomina", type = MovementType.INCOME, amountCents = 150_000, category = MovementCategory.SALARY),
            "super" to aMovement(id = "super", amountCents = 27_000, category = MovementCategory.GROCERIES),
            "mes-pasado" to aMovement(id = "mes-pasado", amountCents = 99_000, date = DAY.minusMonths(1))
        )
        budgets.budgets.value = mapOf(
            (MAIN_ACCOUNT_ID to MovementCategory.GROCERIES) to Budget(MovementCategory.GROCERIES, 30_000),
            (MAIN_ACCOUNT_ID to MovementCategory.LEISURE) to Budget(MovementCategory.LEISURE, 10_000)
        )

        val savings = createViewModel().uiState.value.savings!!

        assertEquals(123_000, savings.balanceCents)
        assertEquals(listOf(MovementCategory.GROCERIES), savings.tightBudgets.map { it.budget.category })
    }

    // endregion
}
