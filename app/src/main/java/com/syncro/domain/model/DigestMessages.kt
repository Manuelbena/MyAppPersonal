package com.syncro.domain.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/*
 * Redacción de los avisos diarios. El tono es el de un amigo que te echa una mano: cercano, con
 * algún emoji y sin agobiar. Cada situación tiene varias frases que rotan según el día (siempre la
 * misma para un día dado, así es predecible y se puede probar) para que no suene a robot.
 */

/** Líneas de detalle como máximo; el resto se resume en "…y N más". */
private const val MAX_LINES = 5
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun DailyDigest.toMessage(): DigestMessage = when (moment) {
    DigestMoment.MORNING -> morningMessage()
    DigestMoment.EVENING -> eveningMessage()
}

// region Mañana: qué te espera hoy

/**
 * El día de nómina el aviso de la mañana lo anuncia primero (título y primera línea) y lleva al
 * chat, donde el asistente ayuda a repartir el sueldo. Lo demás del día sigue debajo.
 */
private fun DailyDigest.morningMessage(): DigestMessage {
    val base = morningAgendaMessage()
    if (!isPayday) return base
    return DigestMessage(
        title = pick(listOf("💼 ¡Día de nómina$name! 🎉", "💸 ¡Hoy cobras$name! 🎉")),
        text = "Hoy entra el sueldo: vamos a organizar el dinero del mes antes de que vuele. ${base.text}",
        lines = listOf("👉 Toca y te ayudo a repartirlo (50/30/20)") + base.lines,
        opensAssistant = true
    )
}

private fun DailyDigest.morningAgendaMessage(): DigestMessage {
    val pending = tasks.filterNot { it.isCompleted }
    val title = pick(listOf("¡Buenos días$name! ☀️", "¡Arriba$name! ☀️", "Hola$name 👋 ¿Empezamos?"))

    if (pending.isEmpty() && events.isEmpty()) {
        val text = if (tasks.isNotEmpty()) {
            "Ya tienes hecho todo lo de hoy. ¡Vas sobrado! 🚀"
        } else {
            pick(
                listOf(
                    "Hoy no tienes nada apuntado. Día libre para lo que te apetezca 🌿",
                    "Agenda despejada. Si surge algo, aquí estoy para apuntarlo ✍️",
                    "Hoy no hay nada en tu agenda. ¡Disfruta del día! 🌿"
                )
            )
        }
        return DigestMessage(title, text, emptyList())
    }

    val counts = listOfNotNull(
        pending.size.takeIf { it > 0 }?.let { count(it, "tarea", "tareas") },
        events.size.takeIf { it > 0 }?.let { count(it, "evento", "eventos") }
    ).joinToString(" y ")
    // Lo que viene de ayer ya ha empezado: "lo primero" es lo que empieza hoy
    val first = firstTimedItem(pending, events.filter { it.date == date })
    val text = buildString {
        append("Hoy tienes $counts.")
        first?.let { (time, itemTitle) -> append(" Lo primero: $itemTitle a las ${time.format(TIME)}.") }
    }

    val items = (pending + events).sortedForDay(date)
    // Un solape o un día a tope va lo primero: es lo que conviene saber antes de empezar
    val warning = dayLoadLine(date, events)
    val lines = listOfNotNull(warning) + items.take(MAX_LINES).map { it.line(date) } + moreLine(items.size) +
        pick(listOf("¡Tú puedes! 💪", "Vamos a por ello 🚀", "Paso a paso, que lo tienes 🙂"))
    // Con varias tareas y sin prioridades elegidas, la notificación lleva al chat para elegirlas
    if (offerFocus && pending.size >= MIN_FOCUS_CANDIDATES) {
        return DigestMessage(title, text, lines + "👉 Toca para elegir tus $MAX_FOCUS_TASKS prioridades", opensAssistant = true)
    }
    // Con un aviso, al tocar se abre el chat, donde está el detalle
    return DigestMessage(title, text, lines, opensAssistant = warning != null)
}

// endregion

// region Noche: cómo ha ido y qué viene mañana

private fun DailyDigest.eveningMessage(): DigestMessage {
    val total = tasks.size
    val pending = tasks.filterNot { it.isCompleted }
    val done = total - pending.size
    // Lo de mañana que conviene arreglar esta noche (eventos que se pisan, día a tope)
    val warning = dayLoadLine(date.plusDays(1), tomorrowEvents)

    if (total == 0) {
        return DigestMessage(
            title = pick(listOf("Buenas noches$name 🌙", "¿Qué tal el día$name? 🌙")),
            text = "Hoy no tenías tareas apuntadas. ${tomorrowSentence()}",
            lines = warning?.let { listOf(it, "👉 Toca y lo miramos") }.orEmpty(),
            opensAssistant = warning != null
        )
    }

    val (title, text) = when {
        pending.isEmpty() -> pick(listOf("¡Día completado$name! 🎉", "¡Lo has bordado$name! 🏆", "¡Qué máquina$name! 🚀")) to
            (if (total == 1) "Has hecho tu tarea de hoy." else "Has hecho las $total tareas de hoy.") +
            " Descansa, te lo has ganado ✨"
        done == 0 -> pick(listOf("¿Qué tal el día$name? 🌙", "Resumen del día 🌙")) to
            "Hoy ${leftToDo(pending.size)}. No pasa nada: mañana será otro día 💪"
        else -> pick(listOf("¡Buen trabajo$name! 👏", "¿Qué tal el día$name? 🌙")) to
            "Has hecho $done de $total tareas. ${leftToDo(pending.size).replaceFirstChar { it.uppercase() }}: " +
            "aún estás a tiempo, o lo dejamos para mañana 💪"
    }

    val lines = pending.take(MAX_LINES).map { it.line(date) } + moreLine(pending.size) + "🔜 ${tomorrowSentence()}" +
        listOfNotNull(warning)
    if (pending.isEmpty() || !offerLeftovers) {
        if (warning == null) return DigestMessage(title, text, lines)
        return DigestMessage(title, text, lines + "👉 Toca y lo miramos", opensAssistant = true)
    }
    // Con pendientes, la notificación lleva al chat, donde se decide qué hacer con ellas
    return DigestMessage(title, text, lines + "👉 Toca para decidir qué hacer con ellas", opensAssistant = true)
}

/** "Mañana tienes 2 eventos y 1 tarea; empiezas a las 09:00 con Dentista." */
private fun DailyDigest.tomorrowSentence(): String {
    val tomorrow = date.plusDays(1)
    val tomorrowPending = tomorrowTasks.filterNot { it.isCompleted }
    if (tomorrowPending.isEmpty() && tomorrowEvents.isEmpty()) return "Mañana tienes la agenda libre."

    val counts = listOfNotNull(
        tomorrowEvents.size.takeIf { it > 0 }?.let { count(it, "evento", "eventos") },
        tomorrowPending.size.takeIf { it > 0 }?.let { count(it, "tarea", "tareas") }
    ).joinToString(" y ")
    val first = firstTimedItem(tomorrowPending, tomorrowEvents.filter { it.date == tomorrow })
    return buildString {
        append("Mañana tienes $counts")
        first?.let { (time, itemTitle) -> append("; empiezas a las ${time.format(TIME)} con $itemTitle") }
        append(".")
    }
}

/**
 * "⚠️ Mañana se te pisan «Dentista» y «Reunión» a las 10:30", "🔋 Hoy vas a tope: 7 eventos y 11 h
 * ocupadas"; null si [day] viene tranquilo o el aviso está apagado. El detalle está en el chat.
 */
private fun DailyDigest.dayLoadLine(day: LocalDate, dayEvents: List<SyncroItem.Event>): String? {
    if (!warnDayLoad) return null
    val load = dayLoadOf(dayEvents, day, today = date) ?: return null
    val whenText = if (load.isTomorrow) "Mañana" else "Hoy"
    val clash = when (load.clashes.size) {
        0 -> null
        1 -> load.clashes.single().let { "se te pisan «${it.firstTitle}» y «${it.secondTitle}» a las ${it.from.format(TIME)}" }
        else -> "tienes ${load.clashes.size} solapes"
    }
    return when {
        clash != null && load.isOverloaded -> "⚠️ $whenText $clash, y vas a tope (${hoursText(load.busyMinutes)} ocupadas)"
        clash != null -> "⚠️ $whenText $clash"
        else -> "🔋 $whenText vas a tope: ${load.eventCount} eventos y ${hoursText(load.busyMinutes)} ocupadas"
    }
}

// endregion

// region Utilidades

/** ", Ana" para meter en los saludos, o nada si no sabemos el nombre. */
private val DailyDigest.name: String get() = if (firstName.isBlank()) "" else ", $firstName"

/** Frase del día entre varias: rota con los días del año y es estable dentro del mismo día. */
private fun DailyDigest.pick(options: List<String>): String = options[date.dayOfYear % options.size]

private fun count(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

private fun leftToDo(n: Int) = if (n == 1) "te queda 1 tarea" else "te quedan $n tareas"

private fun moreLine(total: Int): List<String> =
    if (total > MAX_LINES) listOf("…y ${total - MAX_LINES} más") else emptyList()

/** Lo primero del día con hora concreta (sin contar lo de todo el día), con su título. */
private fun firstTimedItem(tasks: List<SyncroItem.Task>, events: List<SyncroItem.Event>): Pair<LocalTime, String>? {
    val timed = tasks.filterNot { it.isAllDay }.map { it.time to it.title } +
        events.filterNot { it.isAllDay }.map { it.startTime to it.title }
    return timed.minByOrNull { it.first }
}

/** Una línea del detalle: "🗓️ 10:00 · Reunión", "◻️ Llamar al banco"… */
private fun SyncroItem.line(day: LocalDate): String = when (this) {
    is SyncroItem.Event -> {
        val time = when {
            isAllDay -> "Todo el día"
            date != day -> "Hasta las ${endTime.format(TIME)}" // viene de ayer: lo útil es cuándo acaba
            else -> startTime.format(TIME)
        }
        "🗓️ $time · $title"
    }
    is SyncroItem.Task -> if (isAllDay) "◻️ $title" else "◻️ ${time.format(TIME)} · $title"
    is SyncroItem.Note -> "📝 $title"
}

// endregion
