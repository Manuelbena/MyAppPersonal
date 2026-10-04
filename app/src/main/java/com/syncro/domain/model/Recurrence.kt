package com.syncro.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Tareas y eventos que se repiten. Cada repetición es una tarea o un evento normal (con su fila,
 * su id y su copia en Google), creada a partir de una serie: la regla y una plantilla. Así todo lo
 * demás (completar, subtareas, mover, borrar una, la sync) funciona igual que con uno suelto.
 * Google Tasks no deja crear tareas repetidas por su API, y en Calendar las repeticiones llegan ya
 * desplegadas como eventos sueltos: por eso la serie vive solo en el móvil.
 */

enum class RepeatFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Cómo se repite algo. [weekdays] solo cuenta en [RepeatFrequency.WEEKLY] (y ahí tiene al menos
 * uno). Cada mes y cada año se repite el mismo día que el primero; si el mes es más corto, el
 * último (del 31: el 30 de abril; el 29 de febrero: el 28 en años no bisiestos).
 */
data class Recurrence(val frequency: RepeatFrequency, val weekdays: Set<DayOfWeek> = emptySet()) {
    init {
        require(frequency != RepeatFrequency.WEEKLY || weekdays.isNotEmpty()) { "Elige al menos un día de la semana" }
    }

    /** Si [date] es una repetición de una serie que empieza en [start]. */
    fun matches(date: LocalDate, start: LocalDate): Boolean {
        if (date.isBefore(start)) return false
        val sameDayOfMonth = date.dayOfMonth == minOf(start.dayOfMonth, date.lengthOfMonth())
        return when (frequency) {
            RepeatFrequency.DAILY -> true
            RepeatFrequency.WEEKLY -> date.dayOfWeek in weekdays
            RepeatFrequency.MONTHLY -> sameDayOfMonth
            RepeatFrequency.YEARLY -> date.month == start.month && sameDayOfMonth
        }
    }

    /** Repeticiones entre [from] y [to] (ambos incluidos) de una serie que empieza en [start]. */
    fun occurrences(start: LocalDate, from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(maxOf(from, start)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { matches(it, start) }
            .toList()

    /** La primera repetición desde [from] incluido (siempre hay una antes de un año y un día). */
    fun next(start: LocalDate, from: LocalDate): LocalDate =
        occurrences(start, from, maxOf(from, start).plusDays(366)).first()

    companion object {
        /** Cada semana el mismo día que [date]: lo que se propone al elegir "Cada semana". */
        fun weeklyOn(date: LocalDate) = Recurrence(RepeatFrequency.WEEKLY, setOf(date.dayOfWeek))
    }
}

/** Al editar o borrar una repetición: solo esa, o esa y las que vienen detrás. */
enum class RepeatScope { THIS, THIS_AND_FOLLOWING }

/**
 * Una serie: la regla, desde cuándo y la plantilla de cada repetición ([template] es la tarea o el
 * evento tal como es el día [start]).
 *
 * @param until último día en que puede haber repetición (incluido); null = sin fin. Se pone al
 * borrar o cambiar "esta y las siguientes"
 * @param generatedUntil hasta qué día están ya creadas las repeticiones: no se vuelven a crear,
 * así una borrada no reaparece
 */
data class RepeatSeries(
    val id: String,
    val recurrence: Recurrence,
    val start: LocalDate,
    val until: LocalDate? = null,
    val generatedUntil: LocalDate,
    val template: SyncroItem
) {
    init {
        require(template is SyncroItem.Task || template is SyncroItem.Event) { "Solo se repiten tareas y eventos" }
    }

    /**
     * Hasta qué día hay que tener creadas las repeticiones: hasta [horizon] y, como mínimo, la
     * próxima desde [today] (un cumpleaños a once meses vista ya se ve), sin pasar de [until].
     */
    fun generationTarget(today: LocalDate, horizon: LocalDate): LocalDate {
        val target = maxOf(horizon, recurrence.next(start, maxOf(today, start)))
        return until?.let { minOf(it, target) } ?: target
    }

    /** Las repeticiones aún sin crear hasta [target]. */
    fun datesToGenerate(target: LocalDate): List<LocalDate> =
        if (!target.isAfter(generatedUntil)) emptyList()
        else recurrence.occurrences(start, generatedUntil.plusDays(1), target)

    /** La repetición del día [date], nueva (sin subir a Google, sin completar), con el id [id]. */
    fun occurrence(date: LocalDate, id: String): SyncroItem = when (val item = template) {
        is SyncroItem.Task -> item.copy(id = id, remoteId = null, date = date, isCompleted = false, seriesId = this.id, repeat = recurrence)
        is SyncroItem.Event -> item.copy(
            id = id,
            remoteId = null,
            date = date,
            // Un evento que cruza la medianoche sigue durando lo mismo en cada repetición
            endDate = date.plusDays(ChronoUnit.DAYS.between(item.date, item.endDate)),
            isCompleted = false,
            subtasks = item.subtasks.map { it.copy(isCompleted = false) },
            seriesId = this.id,
            repeat = recurrence
        )
        is SyncroItem.Note -> error("Las notas no se repiten")
    }
}

/** Cuántos días por delante se tienen creadas las repeticiones (el Calendario pide más al avanzar). */
const val REPEAT_HORIZON_DAYS = 60L

/** Lo más lejos que se crean repeticiones al mirar meses futuros en el Calendario. */
const val REPEAT_MAX_AHEAD_DAYS = 366L
