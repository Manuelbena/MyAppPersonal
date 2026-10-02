package com.syncro.presentation.home

import android.content.Intent
import androidx.compose.ui.graphics.Color
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.syncro.domain.model.AppSettings
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
import com.syncro.testutil.FakeDailyFocusRepository
import com.syncro.testutil.FakeDailyQuoteRepository
import com.syncro.testutil.FakeSettingsRepository
import com.syncro.testutil.FakeEventRepository
import com.syncro.testutil.FakeGoogleSyncRepository
import com.syncro.testutil.FakeNoteRepository
import com.syncro.testutil.FakeTaskRepository
import com.syncro.testutil.FakeUserRepository
import com.syncro.testutil.MainDispatcherRule
import com.syncro.testutil.aTask
import com.syncro.testutil.at
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
import java.time.Clock
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

    private val clock = Clock.fixed(DAY.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val settings = FakeSettingsRepository()
    private val quotes = FakeDailyQuoteRepository()

    private lateinit var log: CallLog
    private lateinit var tasks: FakeTaskRepository
    private lateinit var events: FakeEventRepository
    private lateinit var google: FakeGoogleSyncRepository
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
        saveTaskUseCase = SaveTaskUseCase(tasks, google),
        saveEventUseCase = SaveEventUseCase(events, google),
        toggleTaskCompletionUseCase = ToggleTaskCompletionUseCase(tasks, google),
        toggleSubtaskCompletionUseCase = ToggleSubtaskCompletionUseCase(events, google),
        toggleEventCompletionUseCase = ToggleEventCompletionUseCase(events, google),
        deleteEventUseCase = DeleteEventUseCase(events, google),
        deleteTaskUseCase = DeleteTaskUseCase(tasks, google),
        syncGoogleTasksUseCase = SyncGoogleTasksUseCase(google),
        syncGoogleCalendarUseCase = SyncGoogleCalendarUseCase(google),
        pushPendingChangesUseCase = PushPendingChangesUseCase(google),
        getNotesUseCase = GetNotesUseCase(notes),
        getLocalUserUseCase = GetLocalUserUseCase(users),
        observeDailyQuoteUseCase = ObserveDailyQuoteUseCase(settings, quotes, GetDailyQuoteUseCase(clock), clock),
        hideDailyQuoteUseCase = HideDailyQuoteUseCase(quotes, clock),
        saveNoteUseCase = SaveNoteUseCase(notes, clock),
        deleteNoteUseCase = DeleteNoteUseCase(notes),
        getDailyFocusUseCase = GetDailyFocusUseCase(focus, tasks)
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
}
