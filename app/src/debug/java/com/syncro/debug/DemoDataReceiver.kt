package com.syncro.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.model.Priority
import com.syncro.domain.model.Recurrence
import com.syncro.domain.model.RepeatFrequency
import com.syncro.domain.model.RepeatScope
import com.syncro.domain.usecase.ChooseDailyFocusUseCase
import com.syncro.domain.usecase.DeleteEventUseCase
import com.syncro.domain.usecase.DeleteTaskUseCase
import com.syncro.domain.usecase.GetEventsInRangeUseCase
import com.syncro.domain.usecase.GetTasksInRangeUseCase
import com.syncro.domain.usecase.SaveBudgetUseCase
import com.syncro.domain.usecase.SaveEventUseCase
import com.syncro.domain.usecase.SaveMovementUseCase
import com.syncro.domain.usecase.SaveNoteUseCase
import com.syncro.domain.usecase.SaveTaskUseCase
import com.syncro.domain.usecase.ToggleTaskCompletionUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DemoDataEntryPoint {
    fun demoClock(): Clock
    fun demoSaveTask(): SaveTaskUseCase
    fun demoSaveEvent(): SaveEventUseCase
    fun demoSaveNote(): SaveNoteUseCase
    fun demoSaveMovement(): SaveMovementUseCase
    fun demoSaveBudget(): SaveBudgetUseCase
    fun demoTasksInRange(): GetTasksInRangeUseCase
    fun demoToggleTask(): ToggleTaskCompletionUseCase
    fun demoChooseFocus(): ChooseDailyFocusUseCase
    fun demoEventsInRange(): GetEventsInRangeUseCase
    fun demoDeleteEvent(): DeleteEventUseCase
    fun demoDeleteTask(): DeleteTaskUseCase
}

/**
 * Solo debug: rellena la app con datos inventados para las capturas de la ficha de Play, a través
 * de los mismos use cases que la UI (así se suben a Google como si se hubieran creado a mano).
 *
 * Crear:  adb shell am broadcast -n com.manuelgalindo.syncro/com.syncro.debug.DemoDataReceiver
 * Borrar: adb shell am broadcast -n com.manuelgalindo.syncro/com.syncro.debug.DemoDataReceiver --es action clear
 *
 * Al crear se apuntan los ids de las tareas y eventos nuevos (los que no estaban antes), y "clear"
 * borra solo esos, también en Google; las series se borran "esta y las siguientes", así que caen
 * también las repeticiones generadas después. Lo local (notas, ahorros) se va al desinstalar.
 */
class DemoDataReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, DemoDataEntryPoint::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val clear = intent.getStringExtra("action") == "clear"
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (clear) clear(entryPoint, prefs) else seed(entryPoint, prefs)
                Log.i(TAG, if (clear) "Datos de ejemplo borrados" else "Datos de ejemplo creados")
            } catch (e: Exception) {
                Log.e(TAG, "Fallo con los datos de ejemplo", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun seed(ep: DemoDataEntryPoint, prefs: SharedPreferences) {
        val today = LocalDate.now(ep.demoClock())
        val eventsBefore = eventIds(ep, today)
        val tasksBefore = taskIds(ep, today)
        try {
            seedEvents(ep, today)
            seedTasks(ep, today)
        } finally {
            // Aunque algo falle a medias, lo creado queda apuntado para poder borrarlo
            prefs.edit()
                .putStringSet(KEY_EVENTS, prefs.getStringSet(KEY_EVENTS, emptySet()).orEmpty() + (eventIds(ep, today) - eventsBefore))
                .putStringSet(KEY_TASKS, prefs.getStringSet(KEY_TASKS, emptySet()).orEmpty() + (taskIds(ep, today) - tasksBefore))
                .commit()
        }
        seedSavings(ep, today)
        seedNotes(ep)
    }

    private suspend fun clear(ep: DemoDataEntryPoint, prefs: SharedPreferences) {
        val today = LocalDate.now(ep.demoClock())
        val eventIds = prefs.getStringSet(KEY_EVENTS, emptySet()).orEmpty()
        val taskIds = prefs.getStringSet(KEY_TASKS, emptySet()).orEmpty()
        // De la más antigua a la más nueva: borrar una serie desde su primera repetición se lleva el resto
        ep.demoEventsInRange()(today.minusDays(RANGE_DAYS), today.plusDays(RANGE_DAYS)).first()
            .filter { it.id in eventIds }.sortedBy { it.date }
            .forEach { event ->
                val scope = if (event.seriesId != null) RepeatScope.THIS_AND_FOLLOWING else RepeatScope.THIS
                ep.demoDeleteEvent()(event.id, scope = scope)
            }
        ep.demoTasksInRange()(today.minusDays(RANGE_DAYS), today.plusDays(RANGE_DAYS)).first()
            .filter { it.id in taskIds }.sortedBy { it.date }
            .forEach { task ->
                val scope = if (task.seriesId != null) RepeatScope.THIS_AND_FOLLOWING else RepeatScope.THIS
                ep.demoDeleteTask()(task.id, scope = scope)
            }
        prefs.edit().clear().commit()
    }

    private suspend fun eventIds(ep: DemoDataEntryPoint, today: LocalDate): Set<String> =
        ep.demoEventsInRange()(today.minusDays(RANGE_DAYS), today.plusDays(RANGE_DAYS)).first().map { it.id }.toSet()

    private suspend fun taskIds(ep: DemoDataEntryPoint, today: LocalDate): Set<String> =
        ep.demoTasksInRange()(today.minusDays(RANGE_DAYS), today.plusDays(RANGE_DAYS)).first().map { it.id }.toSet()

    private suspend fun seedEvents(ep: DemoDataEntryPoint, today: LocalDate) {
        val save = ep.demoSaveEvent()
        suspend fun event(
            title: String,
            date: LocalDate,
            start: LocalTime,
            end: LocalTime,
            category: Pair<String, ArgbColor>,
            endDate: LocalDate = date,
            description: String? = null,
            location: String? = null,
            priority: Priority? = null,
            subtasks: List<String> = emptyList(),
            repeat: Recurrence? = null,
            reminderMinutes: Int? = null
        ) = save(
            title = title,
            description = description,
            location = location,
            date = date,
            startTime = start,
            endTime = end,
            endDate = endDate,
            categoryText = category.first,
            categoryColor = category.second,
            priority = priority,
            subtasks = subtasks,
            repeat = repeat,
            reminderMinutes = reminderMinutes
        ).getOrThrow()

        // Hoy: un día lleno para Inicio
        event(
            "Reunión de equipo", today, at(9, 30), at(10, 30), WORK,
            description = "Repaso del sprint y próximos pasos",
            location = "Sala Atlántico",
            priority = Priority.HIGH,
            subtasks = listOf("Preparar la demo", "Revisar métricas", "Enviar el acta"),
            reminderMinutes = 15
        )
        event(
            "Gimnasio", today, at(13, 30), at(14, 30), SPORT,
            repeat = Recurrence(RepeatFrequency.WEEKLY, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        )
        event("Llamada con el cliente", today, at(16, 0), at(16, 45), WORK, priority = Priority.MEDIUM, reminderMinutes = 5)
        event("Café con Laura", today, at(18, 30), at(19, 30), LEISURE, location = "Café Central", reminderMinutes = 30)

        // Resto del mes para el Calendario
        event("Cumpleaños de Marta", today.plusDays(2), MIDNIGHT, MIDNIGHT, PERSONAL, endDate = today.plusDays(2))
        event("Dentista", today.plusDays(1), at(10, 0), at(10, 45), HEALTH, location = "Clínica Sonrisa", reminderMinutes = 60)
        event("Cena con amigos", today.plusDays(3), at(21, 0), at(23, 30), LEISURE, location = "La Tagliatella")
        event("Escapada a Granada", today.plusDays(5), MIDNIGHT, MIDNIGHT, LEISURE, endDate = today.plusDays(7))
        event("Clase de inglés", today.plusDays(1), at(19, 0), at(20, 0), STUDY,
            repeat = Recurrence(RepeatFrequency.WEEKLY, setOf(today.plusDays(1).dayOfWeek)))
        event("Revisión del coche", today.plusDays(9), at(8, 30), at(9, 30), ERRANDS)
        event("Presentación trimestral", today.plusDays(10), at(11, 0), at(12, 30), WORK, priority = Priority.HIGH)
        event("Concierto", today.minusDays(2), at(21, 0), at(23, 0), LEISURE, location = "WiZink Center")
        event("Médico de cabecera", today.minusDays(4), at(9, 0), at(9, 20), HEALTH)
    }

    private suspend fun seedTasks(ep: DemoDataEntryPoint, today: LocalDate) {
        val save = ep.demoSaveTask()
        val todays = listOf(
            "Comprar regalo para Marta",
            "Pagar la factura de la luz",
            "Llamar al fontanero",
            "Preparar la maleta"
        )
        todays.forEach { save(it, "", today, MIDNIGHT).getOrThrow() }
        save("Renovar el DNI", "Pedir cita previa", today.plusDays(1), MIDNIGHT).getOrThrow()
        save("Devolver el libro a la biblioteca", "", today.plusDays(2), MIDNIGHT).getOrThrow()
        save("Hacer la compra semanal", "Fruta, leche, pan, huevos", today.plusDays(4), MIDNIGHT).getOrThrow()

        // Una hecha y tres prioridades, para que Inicio muestre progreso y "Tus prioridades"
        val saved = ep.demoTasksInRange()(today, today).first().filter { it.title in todays }
        saved.firstOrNull { it.title == "Pagar la factura de la luz" }?.let { ep.demoToggleTask()(it.id) }
        ep.demoChooseFocus()(today, saved.filter { it.title != "Preparar la maleta" }.take(3)).getOrThrow()
    }

    private suspend fun seedSavings(ep: DemoDataEntryPoint, today: LocalDate) {
        val save = ep.demoSaveMovement()
        val monthStart = today.withDayOfMonth(1)
        fun day(d: Int): LocalDate = monthStart.withDayOfMonth(minOf(d, today.lengthOfMonth()))

        save(MovementType.INCOME, 1_850_00, MovementCategory.SALARY, day(1), "Nómina", true).getOrThrow()
        save(MovementType.INCOME, 120_00, MovementCategory.SALES, day(6), "Bici vendida en Wallapop", false).getOrThrow()
        save(MovementType.EXPENSE, 650_00, MovementCategory.HOUSING, day(1), "Alquiler", true).getOrThrow()
        save(MovementType.EXPENSE, 58_40, MovementCategory.BILLS, day(5), "Luz", false).getOrThrow()
        save(MovementType.EXPENSE, 35_00, MovementCategory.BILLS, day(8), "Internet y móvil", true).getOrThrow()
        save(MovementType.EXPENSE, 12_99, MovementCategory.SUBSCRIPTIONS, day(3), "Netflix", true).getOrThrow()
        save(MovementType.EXPENSE, 10_99, MovementCategory.SUBSCRIPTIONS, day(4), "Spotify", true).getOrThrow()
        save(MovementType.EXPENSE, 82_35, MovementCategory.GROCERIES, day(2), "Mercadona", false).getOrThrow()
        save(MovementType.EXPENSE, 64_10, MovementCategory.GROCERIES, day(7), "Supermercado", false).getOrThrow()
        save(MovementType.EXPENSE, 47_80, MovementCategory.RESTAURANTS, day(4), "Cena de cumpleaños", false).getOrThrow()
        save(MovementType.EXPENSE, 38_50, MovementCategory.RESTAURANTS, day(6), "Sushi", false).getOrThrow()
        save(MovementType.EXPENSE, 40_00, MovementCategory.TRANSPORT, day(2), "Abono transporte", true).getOrThrow()
        save(MovementType.EXPENSE, 45_00, MovementCategory.LEISURE, day(5), "Entradas concierto", false).getOrThrow()
        save(MovementType.EXPENSE, 39_99, MovementCategory.SHOPPING, day(7), "Zapatillas", false).getOrThrow()

        val budgets = ep.demoSaveBudget()
        budgets(MovementCategory.GROCERIES, 300_00).getOrThrow()
        // Cerca del límite: el aviso amarillo queda bien en la captura
        budgets(MovementCategory.RESTAURANTS, 100_00).getOrThrow()
        budgets(MovementCategory.LEISURE, 120_00).getOrThrow()
    }

    private suspend fun seedNotes(ep: DemoDataEntryPoint) {
        val save = ep.demoSaveNote()
        save(title = "Ideas para el viaje a Granada", content = "• Alhambra (reservar con antelación)\n• Mirador de San Nicolás al atardecer\n• Tapas por el Albaicín", color = ArgbColor(0xFFFFB74D)).getOrThrow()
        save(title = "Libros pendientes", content = "Hábitos atómicos\nEl infinito en un junco\nSapiens", color = ArgbColor(0xFF81C784)).getOrThrow()
        save(title = "Regalo de Marta", content = "Le gustan las plantas y la cerámica. Mirar la tienda de la calle Mayor.", color = ArgbColor(0xFF64B5F6)).getOrThrow()
    }

    private companion object {
        const val TAG = "DemoData"
        const val PREFS = "demo_data"
        const val KEY_EVENTS = "event_ids"
        const val KEY_TASKS = "task_ids"
        const val RANGE_DAYS = 800L
        val MIDNIGHT: LocalTime = LocalTime.MIDNIGHT
        fun at(hour: Int, minute: Int): LocalTime = LocalTime.of(hour, minute)

        // Mismos nombres y colores que las categorías del formulario de eventos
        val PERSONAL = "Personal" to ArgbColor(0xFF10B981)
        val WORK = "Trabajo" to ArgbColor(0xFF6366F1)
        val HEALTH = "Salud" to ArgbColor(0xFFFF5252)
        val LEISURE = "Ocio" to ArgbColor(0xFFF59E0B)
        val SPORT = "Deporte" to ArgbColor(0xFFEC4899)
        val ERRANDS = "Recados" to ArgbColor(0xFF0EA5E9)
        val STUDY = "Otro" to ArgbColor(0xFF64748B)
    }
}
