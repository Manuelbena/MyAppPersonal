package com.syncro.domain.model

import java.time.LocalDate

/*
 * Tareas atascadas: las que han pasado por varios repasos de pendientes sin hacerse (se movieron
 * o se dejaron). Aplazar lo mismo una y otra vez suele querer decir que es demasiado grande o que
 * ya no hace falta, así que el asistente pregunta qué hacer con ella en vez de seguir moviéndola.
 */

/** Repasos sin hacerse a partir de los que una tarea cuenta como atascada. */
const val STUCK_AFTER_REVIEWS = 3

/** Una tarea atascada: cuántos repasos lleva y el último (el mensaje va detrás de ese repaso). */
data class StuckTask(val task: SyncroItem.Task, val reviews: Int, val lastReview: LocalDate)

enum class StuckChoice { DONE, DELETE, KEEP }

/**
 * Lo contestado sobre una tarea atascada, con lo que decía la pregunta (la tarea puede ya no
 * existir): el título, los repasos y el último, que es donde va en el chat.
 */
data class StuckAnswer(val taskId: String, val title: String, val reviews: Int, val lastReview: LocalDate, val choice: StuckChoice)

/**
 * Las tareas de [tasks] sin hacer que han pasado por [STUCK_AFTER_REVIEWS] repasos o más
 * ([reviews]: los días de repaso de cada tarea) y de las que aún no se ha contestado nada. Las que
 * se repiten no cuentan: su siguiente repetición llega igual.
 */
fun stuckTasks(tasks: List<SyncroItem.Task>, reviews: Map<String, Set<LocalDate>>, answered: Set<String>): List<StuckTask> =
    tasks.filter { !it.isCompleted && it.seriesId == null && it.id !in answered }
        .mapNotNull { task ->
            val dates = reviews[task.id].orEmpty()
            if (dates.size < STUCK_AFTER_REVIEWS) null else StuckTask(task, dates.size, dates.max())
        }
        .sortedWith(compareBy({ it.lastReview }, { it.task.title }))
