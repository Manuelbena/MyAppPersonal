package com.syncro.presentation.calendar

import com.syncro.domain.model.ArgbColor
import com.syncro.domain.usecase.DeleteEventUseCase
import com.syncro.domain.usecase.DeleteTaskUseCase
import com.syncro.domain.usecase.GetEventsInRangeUseCase
import com.syncro.domain.usecase.GetTasksInRangeUseCase
import com.syncro.domain.usecase.SaveNoteUseCase
import com.syncro.domain.usecase.SaveTaskUseCase
import com.syncro.domain.usecase.SyncGoogleCalendarUseCase
import com.syncro.domain.usecase.SyncGoogleTasksUseCase
import com.syncro.domain.usecase.ToggleEventCompletionUseCase
import com.syncro.domain.usecase.ToggleSubtaskCompletionUseCase
import com.syncro.domain.usecase.ToggleTaskCompletionUseCase
import com.syncro.testutil.CallLog
import com.syncro.testutil.FakeEventRepository
import com.syncro.testutil.FakeGoogleSyncRepository
import com.syncro.testutil.FakeNoteRepository
import com.syncro.testutil.FakeTaskRepository
import com.syncro.testutil.MainDispatcherRule
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.YearMonth

/** [CalendarViewModel] agrupa por día lo que se pinta en la cuadrícula del mes. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Se usa el mes actual porque el ViewModel arranca en YearMonth.now()
    private val month = YearMonth.now()
    private val day = month.atDay(10)

    private lateinit var log: CallLog
    private lateinit var tasks: FakeTaskRepository
    private lateinit var events: FakeEventRepository
    private lateinit var google: FakeGoogleSyncRepository
    private val notes = FakeNoteRepository()
    private val clock = Clock.systemDefaultZone()

    @Before
    fun setUp() {
        log = CallLog()
        tasks = FakeTaskRepository(log)
        events = FakeEventRepository(log)
        google = FakeGoogleSyncRepository(log)
    }

    private fun createViewModel() = CalendarViewModel(
        getEventsInRangeUseCase = GetEventsInRangeUseCase(events),
        getTasksInRangeUseCase = GetTasksInRangeUseCase(tasks),
        syncGoogleCalendarUseCase = SyncGoogleCalendarUseCase(google),
        syncGoogleTasksUseCase = SyncGoogleTasksUseCase(google),
        toggleEventCompletionUseCase = ToggleEventCompletionUseCase(events, google),
        toggleSubtaskCompletionUseCase = ToggleSubtaskCompletionUseCase(events, google),
        toggleTaskCompletionUseCase = ToggleTaskCompletionUseCase(tasks, google),
        deleteEventUseCase = DeleteEventUseCase(events, google),
        deleteTaskUseCase = DeleteTaskUseCase(tasks, google),
        saveTaskUseCase = SaveTaskUseCase(tasks, google),
        saveNoteUseCase = SaveNoteUseCase(notes, clock)
    )

    @Test
    fun `al abrir sincroniza el mes entero, no solo el dia 1`() = runTest {
        createViewModel()

        assertTrue(log.calls.contains("syncCalendar(${month.atDay(1)}..${month.atEndOfMonth()})"))
    }

    @Test
    fun `un evento que cruza la medianoche aparece en los dos dias del calendario`() = runTest {
        events.insertEvent(anEvent(title = "Cena", date = day, endDate = day.plusDays(1), startTime = at("21:30"), endTime = at("01:00")))
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        val byDay = viewModel.uiState.value.events
        assertEquals(1, byDay[day]?.size)
        assertEquals(1, byDay[day.plusDays(1)]?.size)
    }

    @Test
    fun `dentro de cada dia lo de todo el dia va primero, como en Inicio`() = runTest {
        tasks.insertTask(aTask(title = "Gimnasio", date = day, time = at("07:00")))
        events.insertEvent(anEvent(title = "Festivo", date = day, startTime = at("00:00"), endTime = at("00:00")))
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        val titles = viewModel.uiState.value.events.getValue(day).map {
            when (it) {
                is com.syncro.domain.model.SyncroItem.Task -> it.title
                is com.syncro.domain.model.SyncroItem.Event -> it.title
                is com.syncro.domain.model.SyncroItem.Note -> it.title
            }
        }
        assertEquals(listOf("Festivo", "Gimnasio"), titles)
    }

    @Test
    fun `completar una tarea desde el calendario la marca y la sube`() = runTest {
        // Regresión: en el calendario el check de las tareas no hacía nada
        tasks.insertTask(aTask(id = "t1", date = day))
        val viewModel = createViewModel()

        viewModel.toggleTaskCompletion("t1")

        assertTrue(tasks.tasks.value.getValue("t1").isCompleted)
        assertTrue(google.pushedTaskIds.contains("t1"))
    }

    // Regresión: el "+" de Calendario abría la hoja de nota pero guardar no hacía nada
    @Test
    fun `una nota creada desde Calendario se guarda en la libreta`() = runTest {
        val viewModel = createViewModel()

        viewModel.saveNote("Ideas", "App de hábitos", ArgbColor(0xFFF59E0B))

        assertEquals(listOf("Ideas"), notes.notes.value.values.map { it.title })
    }
}
