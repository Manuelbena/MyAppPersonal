package com.syncro.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Los dos avisos diarios: el de la mañana (qué te espera hoy) y el de la noche (cómo ha ido). */
enum class DigestMoment(val time: LocalTime) {
    MORNING(LocalTime.of(9, 0)),
    EVENING(LocalTime.of(21, 0))
}

/**
 * Próxima vez que toca el aviso [this] después de [now]: hoy si aún no ha pasado la hora, si no
 * mañana. Estrictamente después, para que una alarma que suena a su hora no se reprograme para
 * el mismo instante.
 */
fun DigestMoment.nextAfter(now: LocalDateTime): LocalDateTime {
    val today = now.toLocalDate().atTime(time)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

/** Todo lo que hace falta para escribir un aviso: el día de hoy, el de mañana y a quién se habla. */
data class DailyDigest(
    val moment: DigestMoment,
    val date: LocalDate,
    /** Nombre de pila; vacío si no se conoce. */
    val firstName: String,
    val tasks: List<SyncroItem.Task>,
    val events: List<SyncroItem.Event>,
    val tomorrowTasks: List<SyncroItem.Task>,
    val tomorrowEvents: List<SyncroItem.Event>
)

/** Lo que muestra la notificación: título, frase principal y líneas extra al desplegarla. */
data class DigestMessage(
    val title: String,
    val text: String,
    val lines: List<String>
)
