package com.syncro.domain.usecase

import com.syncro.domain.model.REPEAT_HORIZON_DAYS
import com.syncro.domain.model.REPEAT_MAX_AHEAD_DAYS
import com.syncro.domain.model.RepeatSeries
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.RepeatSeriesRepository
import com.syncro.domain.repository.TaskRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crea las repeticiones que falten de cada serie: las de los próximos [REPEAT_HORIZON_DAYS] días
 * (y siempre la siguiente, aunque caiga más lejos) o hasta [invoke]`(until)` si se mira más
 * adelante en el Calendario, con tope de [REPEAT_MAX_AHEAD_DAYS]. Inicio lo llama cada día y el
 * Calendario al cambiar de mes; sin abrir la app no se crean, pero tampoco hacen falta.
 *
 * Singleton con un Mutex: Inicio y Calendario pueden pedirlo a la vez y crearían dos veces la misma.
 */
@Singleton
class GenerateRepeatsUseCase @Inject constructor(
    private val seriesRepository: RepeatSeriesRepository,
    private val taskRepository: TaskRepository,
    private val eventRepository: EventRepository,
    private val googleSyncRepository: GoogleSyncRepository,
    private val clock: Clock
) {
    private val mutex = Mutex()

    /**
     * Devuelve cuántas repeticiones ha creado. Con [upload] las sube después a Google; quien ya va
     * a subir lo pendiente (al guardar una serie) lo pone a false para no subir dos veces.
     */
    suspend operator fun invoke(until: LocalDate? = null, upload: Boolean = true): Int {
        val created = mutex.withLock {
            val today = LocalDate.now(clock)
            val requested = minOf(until ?: today, today.plusDays(REPEAT_MAX_AHEAD_DAYS))
            val horizon = maxOf(today.plusDays(REPEAT_HORIZON_DAYS), requested)
            seriesRepository.getAllSeries().sumOf { generate(it, today, horizon) }
        }
        if (upload && created > 0) googleSyncRepository.pushPendingChanges()
        return created
    }

    private suspend fun generate(series: RepeatSeries, today: LocalDate, horizon: LocalDate): Int {
        val target = series.generationTarget(today, horizon)
        if (!target.isAfter(series.generatedUntil)) return 0
        val dates = series.datesToGenerate(target)
        // Primero se apunta hasta dónde quedan creadas: si la app se cierra a medias faltará alguna,
        // pero nunca habrá una repetida (dos copias en Google)
        seriesRepository.saveSeries(series.copy(generatedUntil = target))
        dates.forEach { date ->
            when (val item = series.occurrence(date, UUID.randomUUID().toString())) {
                is SyncroItem.Task -> taskRepository.insertTask(item)
                is SyncroItem.Event -> eventRepository.insertEvent(item)
                is SyncroItem.Note -> Unit
            }
        }
        return dates.size
    }
}

/**
 * Corta la serie para que acabe el día antes de [date] (al borrar o cambiar "esta y las
 * siguientes"); si así no queda ninguna repetición, la borra.
 */
internal suspend fun RepeatSeriesRepository.endBefore(seriesId: String, date: LocalDate) {
    val series = getSeries(seriesId) ?: return
    if (!date.isAfter(series.start)) deleteSeries(seriesId) else saveSeries(series.copy(until = date.minusDays(1)))
}
