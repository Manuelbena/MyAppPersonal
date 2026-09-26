package com.syncro.presentation.home

import android.content.Intent
import androidx.compose.ui.graphics.Color
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.syncro.domain.model.User
import com.syncro.domain.usecase.DeleteNoteUseCase
import com.syncro.domain.usecase.GetDailyQuoteUseCase
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

    private lateinit var log: CallLog
    private lateinit var tasks: FakeTaskRepository
    private lateinit var events: FakeEventRepository
    private lateinit var google: FakeGoogleSyncRepository
    private lateinit var notes: FakeNoteRepository
    private lateinit var users: FakeUserRepository

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
        syncGoogleTasksUseCase = SyncGoogleTasksUseCase(google),
        syncGoogleCalendarUseCase = SyncGoogleCalendarUseCase(google),
        pushPendingChangesUseCase = PushPendingChangesUseCase(google),
        getNotesUseCase = GetNotesUseCase(notes),
        getLocalUserUseCase = GetLocalUserUseCase(users),
        getDailyQuoteUseCase = GetDailyQuoteUseCase(clock),
        saveNoteUseCase = SaveNoteUseCase(notes, clock),
        deleteNoteUseCase = DeleteNoteUseCase(notes)
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
        // Regresión: el nombre estaba fijo en el código y cualquier cuenta veía "Hola, Manuel"
        users.saveUser(User(email = "ana@example.com", name = "", photoUrl = null))

        assertEquals("", createViewModel().uiState.value.userName)
    }

    @Test
    fun `la frase del dia es la del caso de uso para hoy`() = runTest {
        val expected = GetDailyQuoteUseCase(clock)()

        val state = createViewModel().uiState.value

        assertEquals(expected.text, state.quote)
        assertEquals(expected.author, state.quoteAuthor)
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

        viewModel.saveQuickTask(title = "Comprar pan", description = "", date = DAY, time = at("10:00"))

        assertEquals(listOf(HomeEffect.ShowSnackbar("Tarea creada correctamente")), effects)
        assertEquals(1, tasks.tasks.value.size)
    }

    // endregion
}
