package com.syncro.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Lo que el usuario contestó a "¿Te recuerdo tu día?". */
enum class DigestAnswer { ACCEPTED, DECLINED }

/** Lo que puede contestar el usuario tocando una opción bajo un mensaje. */
enum class ChatReply {
    ENABLE_DIGEST,
    NOT_NOW,
    LEFTOVERS_MOVE_ALL,
    LEFTOVERS_ONE_BY_ONE,
    LEFTOVERS_KEEP,
    /** Confirma las tareas marcadas en la lista del mensaje (prioridades del día). */
    FOCUS_CONFIRM,
    FOCUS_SKIP,
    /** Día de nómina: abrir Ahorros con la nómina preparada. */
    PAYDAY_REGISTER,
    PAYDAY_LATER,
    /** Hábito detectado: que se repita cada semana ese día. */
    HABIT_REPEAT,
    HABIT_DISMISS,
    /** Hay versión nueva: abrir Syncro en la tienda para actualizar. */
    UPDATE_NOW,
    /** Hay versión nueva: no avisar más de esta versión. */
    UPDATE_LATER,
    /** Tarea atascada: ya está hecha, ya no hace falta (se borra) o se mantiene (no se vuelve a preguntar). */
    STUCK_DONE,
    STUCK_DELETE,
    STUCK_KEEP
}

data class ChatOption(val reply: ChatReply, val label: String)

/** Lo que se puede hacer con cada tarea en el modo "una a una". */
enum class TaskAction { MOVE_TO_TARGET, OTHER_DAY, DONE }

/**
 * Un mensaje del chat del asistente. El [id] es estable (no depende del orden) para poder
 * recordar cuáles ha leído el usuario.
 *
 * @param tasks lista de tareas dentro de la burbuja (las pendientes)
 * @param taskTarget si no es null, cada tarea lleva sus acciones (pasar a este día, otro día, hecha)
 * @param maxSelectable si es mayor que 0, las tareas se pueden marcar (hasta ese número) y
 *   [ChatReply.FOCUS_CONFIRM] confirma las marcadas
 * @param options opciones para contestar, bajo el mensaje; desaparecen al contestar
 * @param day el día en que salió el mensaje (el chip "Hoy", "Ayer"… encima); null en los de
 *   bienvenida, que van con los que les siguen
 */
data class ChatMessage(
    val id: String,
    val fromAssistant: Boolean,
    val text: String,
    val tasks: List<ChatTask> = emptyList(),
    val taskTarget: MoveTarget? = null,
    val maxSelectable: Int = 0,
    val options: List<ChatOption> = emptyList(),
    val day: LocalDate? = null
)

data class AssistantConversation(val messages: List<ChatMessage>) {
    /** Mensajes del asistente que el usuario aún no ha visto (el número del icono de Asistente). */
    fun unreadCount(readIds: Set<String>): Int = messages.count { it.fromAssistant && it.id !in readIds }
}

/**
 * Construye la conversación a partir de lo que se sabe (no es un historial guardado): así siempre
 * refleja el estado real, p. ej. si el usuario activa las notificaciones desde los ajustes o
 * completa una tarea desde Inicio.
 *
 * @param answer lo que contestó el usuario a los avisos, o null si aún no ha contestado
 * @param notificationsAllowed si Android deja publicar los avisos ahora mismo
 * @param leftovers las tareas pendientes del repaso actual (null si aún no se saben)
 * @param outcomes lo que el usuario decidió en cada repaso (el actual y los recientes)
 * @param stuck las tareas que llevan varios repasos sin hacerse, sin contestar aún
 * @param stuckAnswers lo contestado sobre tareas atascadas (lo reciente)
 * @param focus las tareas de hoy entre las que elegir prioridades (null desde las 21:00)
 * @param focusHistory las prioridades ya elegidas (la de hoy y las recientes)
 * @param payday el último día de nómina si fue hace poco (null sin nómina en Ajustes)
 * @param budgetAlerts los presupuestos que llegaron al 80 % o se pasaron hace poco
 * @param weeklyBalance las horas por categoría de la semana pasada (se cuenta el lunes)
 * @param errands las salidas (Recados, Compras) pendientes de hoy, si son varias
 * @param dayLoads solapes y días a tope de hoy y, desde la hora de la noche, de mañana
 * @param habits lo que el usuario apunta casi cada semana el mismo día, sin contestar aún
 * @param habitAnswers lo contestado a esas propuestas (las recientes)
 * @param update versión nueva en la tienda: va al final, es lo más nuevo
 * @param updateDismissed si a esa versión ya se contestó "Más tarde"
 * @param clearedIds mensajes que el usuario borró al vaciar el chat (no vuelven a salir)
 * @param today hoy: ningún mensaje lleva un día posterior (el repaso de la noche se pregunta hoy
 *   aunque se ordene con mañana); null para no poner días
 */
fun assistantConversation(
    answer: DigestAnswer?,
    notificationsAllowed: Boolean,
    leftovers: LeftoverTasks? = null,
    outcomes: List<LeftoverOutcome> = emptyList(),
    stuck: List<StuckTask> = emptyList(),
    stuckAnswers: List<StuckAnswer> = emptyList(),
    focus: FocusCandidates? = null,
    focusHistory: List<DailyFocus> = emptyList(),
    payday: Payday? = null,
    budgetAlerts: List<BudgetAlert> = emptyList(),
    weeklyBalance: WeeklyBalance? = null,
    errands: ErrandsDay? = null,
    dayLoads: List<DayLoad> = emptyList(),
    habits: List<Habit> = emptyList(),
    habitAnswers: List<HabitAnswer> = emptyList(),
    update: AppUpdate? = null,
    updateDismissed: Boolean = false,
    clearedIds: Set<String> = emptySet(),
    today: LocalDate? = null
): AssistantConversation {
    val messages = digestMessages(answer, notificationsAllowed)

    // Repasos y prioridades por orden de llegada: el repaso de un día se contesta por la noche (o a
    // la mañana siguiente), así que va detrás de las prioridades de ese día y delante de las del siguiente
    val blocks = mutableListOf<Pair<Moment, List<ChatMessage>>>()
    outcomes.forEach { outcome ->
        val pending = leftovers?.takeIf { it.reviewDate == outcome.reviewDate }?.tasks.orEmpty()
        blocks += reviewMoment(outcome.reviewDate, outcome.target) to answeredLeftoverMessages(outcome, pending)
    }
    val unanswered = leftovers?.takeIf { current ->
        current.tasks.isNotEmpty() && outcomes.none { it.reviewDate == current.reviewDate }
    }
    unanswered?.let { blocks += reviewMoment(it.reviewDate, it.target) to listOf(leftoverQuestion(it)) }
    val leftoverPending = unanswered != null
    // Las atascadas, justo detrás del repaso que las ha vuelto a dejar sin hacer
    stuckAnswers.forEach { blocks += Moment(it.lastReview.plusDays(1), STUCK_ORDER) to answeredStuckMessages(it) }
    stuck.filter { task -> stuckAnswers.none { it.taskId == task.task.id } }
        .forEach { blocks += Moment(it.lastReview.plusDays(1), STUCK_ORDER) to listOf(stuckQuestion(it)) }
    focusHistory.forEach { chosen -> blocks += Moment(chosen.date, FOCUS_ORDER) to answeredFocusMessages(chosen) }
    // Las prioridades se preguntan cuando ya no queda un repaso por contestar: lo que se pase a hoy
    // también puede ser prioridad
    if (focus != null && !leftoverPending && focusHistory.none { it.date == focus.date } &&
        focus.tasks.size >= MIN_FOCUS_CANDIDATES
    ) {
        blocks += Moment(focus.date, FOCUS_ORDER) to listOf(focusQuestion(focus))
    }
    // La nómina, lo primero de su día: es lo que marca cómo se organiza el mes
    payday?.let { blocks += Moment(it.date, PAYDAY_ORDER) to paydayMessages(it) }
    // Los avisos de presupuesto, el día en que se cruzó el umbral (detrás de lo demás de ese día)
    budgetAlerts.forEach { blocks += Moment(it.date, BUDGET_ORDER) to listOf(budgetAlertMessage(it)) }
    // El resumen de la semana, lo primero del lunes
    weeklyBalance?.let { blocks += Moment(it.weekEnd.plusDays(1), WEEK_ORDER) to listOf(weeklyBalanceMessage(it)) }
    // Las salidas de hoy, detrás de lo demás de ese día
    errands?.let { blocks += Moment(it.date, ERRANDS_ORDER) to listOf(errandsMessage(it)) }
    // Cómo viene el día, en su día: el de mañana (que se cuenta esta noche) queda lo último del chat
    dayLoads.forEach {
        // El de mañana se cuenta esta noche
        val asked = if (it.isTomorrow) it.date.minusDays(1) else it.date
        blocks += Moment(it.date, DAY_LOAD_ORDER, asked) to listOf(dayLoadMessage(it))
    }
    // Los hábitos, el día que se hizo la última vez (que es cuando se nota)
    habitAnswers.forEach { blocks += Moment(it.date, HABIT_ORDER) to answeredHabitMessages(it) }
    habits.filter { habit -> habitAnswers.none { it.key == habit.key } }
        .forEach { blocks += Moment(it.lastDone, HABIT_ORDER) to listOf(habitQuestion(it)) }
    blocks.sortedWith(compareBy({ it.first.day }, { it.first.order })).forEach { (moment, block) ->
        val day = today?.let { minOf(moment.asked, it) }
        messages += block.map { it.copy(day = day) }
    }
    // La versión nueva, lo último: es de ahora mismo (y se va sola al actualizar)
    update?.let { messages += updateMessages(it, updateDismissed).map { message -> message.copy(day = today) } }

    return AssistantConversation(messages.filter { it.id !in clearedIds })
}

/**
 * Posición de un bloque en el chat: el día y, dentro del día, repaso (antes) o prioridades.
 * [asked] es el día en que se dijo, si no es [day] (el repaso de la noche va con el día siguiente).
 */
private data class Moment(val day: LocalDate, val order: Int, val asked: LocalDate = day)

/** El repaso de la noche se pregunta ese mismo día; el de la mañana, al día siguiente. */
private fun reviewMoment(reviewDate: LocalDate, target: MoveTarget) = Moment(
    reviewDate.plusDays(1),
    LEFTOVERS_ORDER,
    asked = if (target == MoveTarget.TOMORROW) reviewDate else reviewDate.plusDays(1)
)
private const val WEEK_ORDER = -2
private const val PAYDAY_ORDER = -1
private const val LEFTOVERS_ORDER = 0
private const val STUCK_ORDER = 1
private const val FOCUS_ORDER = 2
private const val BUDGET_ORDER = 3
private const val ERRANDS_ORDER = 4
private const val HABIT_ORDER = 5
private const val DAY_LOAD_ORDER = 6

// region Orden de llegada

/**
 * El chat como en WhatsApp: cada mensaje en el momento en que salió por primera vez ([arrivedAt],
 * lo guarda la app) y con ese día, así lo nuevo siempre queda abajo aunque hable de días pasados.
 * Los que llegaron a la vez (o aún no tienen hora: son de [now]) mantienen el orden de la conversación.
 */
fun AssistantConversation.inArrivalOrder(arrivedAt: Map<String, LocalDateTime>, now: LocalDateTime): AssistantConversation {
    val at = messages.map { arrivedAt[it.id] ?: now }
    return AssistantConversation(
        messages.indices.sortedBy { at[it] }.map { messages[it].copy(day = at[it].toLocalDate()) }
    )
}

/**
 * La hora de llegada de los mensajes que aún no la tienen: ahora. La primera vez (sin nada guardado,
 * p. ej. al actualizar la app) los de días pasados se quedan en su día, para no juntarlo todo en hoy.
 */
fun AssistantConversation.newArrivals(arrivedAt: Map<String, LocalDateTime>, now: LocalDateTime): Map<String, LocalDateTime> {
    val firstTime = arrivedAt.isEmpty()
    // Los de bienvenida (sin día) van con el primer día que haya
    val firstDay = messages.firstNotNullOfOrNull { it.day }
    return messages.filter { it.id !in arrivedAt }.associate { message ->
        val day = message.day ?: firstDay
        message.id to if (firstTime && day != null && day.isBefore(now.toLocalDate())) day.atStartOfDay() else now
    }
}

// endregion

// region Avisos diarios

private fun digestMessages(answer: DigestAnswer?, notificationsAllowed: Boolean): MutableList<ChatMessage> {
    val messages = mutableListOf(assistant(ID_HELLO, "¡Hola! Soy tu asistente. Por aquí te iré contando cosas de tu día."))

    // Ya tiene los avisos y nunca se le preguntó (p. ej. Android 12, sin permiso que pedir): se le cuenta y ya
    if (answer == null && notificationsAllowed) {
        messages += assistant(ID_DIGEST_ON, "Cada mañana a las 9:00 te cuento lo que te espera, y a las 21:00 cómo ha ido el día. 🔔")
        return messages
    }

    messages += assistant(
        ID_DIGEST_QUESTION,
        "¿Te recuerdo tu día? Cada mañana a las 9:00 te cuento lo que te espera, y a las 21:00 " +
            "cómo ha ido el día. Funciona también sin internet.",
        options = if (answer == null) {
            listOf(ChatOption(ChatReply.ENABLE_DIGEST, "Sí, avísame"), ChatOption(ChatReply.NOT_NOW, "Ahora no"))
        } else {
            emptyList()
        }
    )
    when (answer) {
        null -> Unit
        DigestAnswer.ACCEPTED -> {
            messages += user(ID_USER_ANSWER, "Sí, avísame")
            messages += if (notificationsAllowed) {
                assistant(ID_DIGEST_ACCEPTED, "¡Hecho! Te escribo mañana a las 9:00. 😊")
            } else {
                // Aceptó pero Android no lo permite (denegó el diálogo o las tiene silenciadas)
                assistant(
                    ID_DIGEST_BLOCKED,
                    "Android no me deja enviarte avisos. Puedes activarlos en los ajustes de notificaciones de Syncro."
                )
            }
        }
        DigestAnswer.DECLINED -> {
            messages += user(ID_USER_ANSWER, "Ahora no")
            messages += assistant(
                ID_DIGEST_DECLINED,
                "Vale, sin problema. Si cambias de idea, puedes activarlos en los ajustes de notificaciones de Syncro."
            )
        }
    }
    return messages
}

// endregion

// region Tareas pendientes

private fun leftoverQuestionText(target: MoveTarget, total: Int): String {
    val greeting = if (target == MoveTarget.TODAY) "¡Buenos días! ☀️ " else ""
    val verb = if (total == 1) "quedó 1 tarea" else "quedaron $total tareas"
    return "${greeting}Se te $verb sin hacer. ¿Qué hacemos?"
}

private fun moveAllLabel(target: MoveTarget) = if (target == MoveTarget.TODAY) "Todas a hoy" else "Todas a mañana"

/** La pregunta, con la lista de tareas y las tres opciones. */
private fun leftoverQuestion(leftovers: LeftoverTasks): ChatMessage = assistant(
    leftoverId(leftovers.reviewDate),
    leftoverQuestionText(leftovers.target, leftovers.tasks.size),
    tasks = leftovers.tasks.map { it.toChatTask(leftovers.reviewDate) },
    options = listOf(
        ChatOption(ChatReply.LEFTOVERS_MOVE_ALL, moveAllLabel(leftovers.target)),
        ChatOption(ChatReply.LEFTOVERS_ONE_BY_ONE, "Elegir una a una"),
        ChatOption(ChatReply.LEFTOVERS_KEEP, "Déjalas")
    )
)

/** Un repaso ya contestado: la pregunta (sin lista ni opciones), la respuesta y cómo quedó. */
private fun answeredLeftoverMessages(outcome: LeftoverOutcome, pending: List<SyncroItem.Task>): List<ChatMessage> {
    val id = leftoverId(outcome.reviewDate)
    val question = assistant(id, leftoverQuestionText(outcome.target, outcome.total))
    val targetName = outcome.target.label.lowercase()

    return when (outcome.choice) {
        LeftoverChoice.MOVE_ALL -> listOf(
            question,
            user("$id-answer", moveAllLabel(outcome.target)),
            assistant("$id-done", "Hecho ✅ He pasado ${tasksCount(outcome.total)} a $targetName.")
        )
        LeftoverChoice.KEEP -> listOf(
            question,
            user("$id-answer", "Déjalas"),
            assistant("$id-done", "Vale, las dejo como están. Te las vuelvo a recordar en el próximo repaso.")
        )
        LeftoverChoice.ONE_BY_ONE -> buildList {
            add(question)
            add(user("$id-answer", "Elegir una a una"))
            if (pending.isNotEmpty()) {
                // En curso: la lista con las acciones de cada tarea; se vacía según las resuelve
                add(
                    assistant(
                        "$id-pick",
                        "Vale, dime qué hago con cada una:",
                        tasks = pending.map { it.toChatTask(outcome.reviewDate) },
                        taskTarget = outcome.target
                    )
                )
            } else {
                add(assistant("$id-done", oneByOneSummary(outcome)))
            }
        }
    }
}

private fun oneByOneSummary(outcome: LeftoverOutcome): String {
    val parts = listOfNotNull(
        outcome.moved.takeIf { it > 0 }?.let { "he movido ${tasksCount(it)}" },
        outcome.done.takeIf { it > 0 }?.let { if (it == 1) "has hecho 1" else "has hecho $it" }
    )
    return if (parts.isEmpty()) "Listo ✅ Ya no te queda nada pendiente." else "Listo ✅ ${parts.joinToString(" y ").replaceFirstChar { it.uppercase() }}."
}

private fun leftoverId(reviewDate: LocalDate) = "leftovers-$reviewDate"

// endregion

// region Tareas atascadas

private fun stuckQuestionText(title: String, reviews: Int) =
    "🐌 «$title» ya lleva $reviews repasos sin hacerse. Cuando algo se aplaza tanto suele ser porque es " +
        "demasiado grande o porque ya no hace falta. ¿Qué hacemos?"

private fun stuckQuestion(stuck: StuckTask): ChatMessage = assistant(
    stuckId(stuck.task.id),
    stuckQuestionText(stuck.task.title, stuck.reviews),
    options = listOf(
        ChatOption(ChatReply.STUCK_DONE, stuckLabel(StuckChoice.DONE)),
        ChatOption(ChatReply.STUCK_DELETE, stuckLabel(StuckChoice.DELETE)),
        ChatOption(ChatReply.STUCK_KEEP, stuckLabel(StuckChoice.KEEP))
    )
)

private fun stuckLabel(choice: StuckChoice) = when (choice) {
    StuckChoice.DONE -> "Ya está hecha"
    StuckChoice.DELETE -> "Ya no hace falta"
    StuckChoice.KEEP -> "La mantengo"
}

private fun answeredStuckMessages(answer: StuckAnswer): List<ChatMessage> {
    val id = stuckId(answer.taskId)
    return listOf(
        assistant(id, stuckQuestionText(answer.title, answer.reviews)),
        user("$id-answer", stuckLabel(answer.choice)),
        assistant(
            "$id-done",
            when (answer.choice) {
                StuckChoice.DONE -> "¡Genial! ✅ La marco como hecha."
                StuckChoice.DELETE -> "Hecho, la he borrado. Una cosa menos en la cabeza. 🧹"
                StuckChoice.KEEP -> "Vale, la mantengo y no te vuelvo a preguntar por ella. Un truco: apunta solo el " +
                    "primer paso, que cuesta mucho menos empezar. 💡"
            }
        )
    )
}

fun stuckId(taskId: String) = "stuck-$taskId"

// endregion

// region Día de nómina

/**
 * El día de nómina: el saludo con un consejo y la pregunta de si se apunta en Ahorros; lo que se
 * contestó; y, en cuanto la nómina está apuntada, el reparto 50/30/20 con los euros de verdad.
 */
private fun paydayMessages(payday: Payday): List<ChatMessage> {
    val id = paydayId(payday.date)
    val salary = payday.salaryCents
    // Si ya estaba apuntada (p. ej. una nómina mensual) no hay nada que preguntar
    val asks = salary == null || payday.answer != null
    val greeting = pickFor(
        payday.date,
        "💼 ¡Día de nómina! 🎉 Hoy entra el sueldo: es el mejor momento para organizar el mes.",
        "💼 ¡Hoy cobras! 🎉 Antes de que el dinero vuele, vamos a darle un plan.",
        "💼 ¡Día de nómina! 💸 Lo que hagas hoy con el sueldo marca cómo irá el resto del mes."
    )
    val tip = "💡 El truco de los bancos: aparta primero el ahorro y gasta lo que queda, no al revés."
    return buildList {
        add(
            assistant(
                id,
                if (asks) "$greeting\n\n$tip\n\n¿Apuntamos la nómina en Ahorros?" else "$greeting\n\n$tip",
                options = if (payday.answer == null && salary == null) {
                    listOf(ChatOption(ChatReply.PAYDAY_REGISTER, "Apuntar nómina"), ChatOption(ChatReply.PAYDAY_LATER, "Ahora no"))
                } else {
                    emptyList()
                }
            )
        )
        when (payday.answer) {
            PaydayAnswer.REGISTER -> {
                add(user("$id-answer", "Apuntar nómina"))
                if (salary == null) add(assistant("$id-open", "¡Vamos! Te abro Ahorros con la nómina preparada: solo pon el importe. 📝"))
            }
            PaydayAnswer.LATER -> {
                add(user("$id-answer", "Ahora no"))
                add(assistant("$id-later", "Vale 👍 Cuando la cobres, apúntala con el + de Ahorros y te ayudo a repartirla."))
            }
            null -> Unit
        }
        salary?.let { add(assistant("$id-plan", salaryPlanText(it))) }
    }
}

private fun salaryPlanText(salaryCents: Long): String {
    val plan = salaryPlan(salaryCents)
    return "💰 Tienes apuntados ${euros(salaryCents)} de nómina. Con la regla 50/30/20 quedaría así:\n" +
        "🏠 Necesidades (50 %): ${euros(plan.needsCents)}\n" +
        "🎉 Caprichos (30 %): ${euros(plan.wantsCents)}\n" +
        "🐷 Ahorro (20 %): ${euros(plan.savingsCents)}\n\n" +
        "Consejo: mueve hoy mismo los ${euros(plan.savingsCents)} a tu cuenta de ahorro y olvídate de ellos. " +
        "¡Tu yo del futuro te lo agradecerá! 😉"
}

/** Una de varias frases, fija para cada día (cambia de un mes a otro, no al reabrir el chat). */
private fun pickFor(date: LocalDate, vararg options: String): String = options[date.dayOfYear % options.size]

private fun paydayId(date: LocalDate) = "payday-$date"

// endregion

// region Presupuestos

/**
 * Aviso de un presupuesto: al 80 %, con lo que queda hasta fin de mes; al pasarse, sin sermones y
 * con una salida (revisar en qué se ha ido o ajustar el límite).
 */
private fun budgetAlertMessage(alert: BudgetAlert): ChatMessage {
    val budget = alert.budget
    // Con varias cuentas se dice de cuál: "Supermercado (Conjunta)"
    val name = "${budget.category.emoji} ${budget.category.label}" + (alert.accountName?.let { " ($it)" } ?: "")
    val text = when (alert.level) {
        BudgetLevel.EXCEEDED ->
            "🚨 Te has pasado del presupuesto de $name: llevas ${euros(alert.spentCents)} de ${euros(budget.limitCents)}. " +
                "No pasa nada: mira en Ahorros en qué se ha ido y, si hace falta, ajusta el límite para el mes que viene. 💪"
        else -> {
            val percent = alert.spentCents * 100 / budget.limitCents
            "⚠️ Ojo con $name: llevas ${euros(alert.spentCents)} de ${euros(budget.limitCents)} ($percent %). " +
                "Te quedan ${euros(budget.limitCents - alert.spentCents)} hasta fin de mes. 💡"
        }
    }
    val level = if (alert.level == BudgetLevel.EXCEEDED) "exceeded" else "warning"
    // La cuenta principal no lleva su id: así los avisos de antes de las cuentas no salen otra vez como nuevos
    val account = budget.accountId.takeIf { it != MAIN_ACCOUNT_ID }?.let { "$it-" } ?: ""
    return assistant("budget-${alert.month}-$account${budget.category.name}-$level", text)
}

// endregion

// region Semana

/** Las horas de cada categoría (de más a menos) y, si se nota, un consejo amable. */
private fun weeklyBalanceMessage(balance: WeeklyBalance): ChatMessage {
    val lines = balance.minutesByCategory.entries
        .sortedByDescending { it.value }
        .joinToString(" · ") { (category, minutes) -> "${categoryEmoji(category)} $category ${hoursText(minutes)}" }
    val work = balance.minutesOf("Trabajo")
    val unwind = balance.minutesOf("Ocio") + balance.minutesOf("Deporte")
    val tip = when {
        work >= BUSY_WORK_MINUTES && unwind == 0L ->
            " Mucho trabajo y nada de Ocio ni Deporte: ¿te reservas un rato para ti esta semana? 🌿"
        balance.minutesOf("Deporte") >= 3 * 60 -> " ¡Bien ese deporte! 💪"
        else -> ""
    }
    return assistant("week-${balance.weekEnd}", "📊 Así fue tu semana: $lines.$tip")
}

/** "45 min", "3 h", "3,5 h" (redondeado a la media hora). */
internal fun hoursText(minutes: Long): String {
    if (minutes < 60) return "$minutes min"
    val halves = (minutes + 15) / 30
    return if (halves % 2 == 0L) "${halves / 2} h" else "${halves / 2},5 h"
}

private fun categoryEmoji(category: String): String = when (category) {
    "Trabajo" -> "💼"
    "Salud" -> "🩺"
    "Deporte" -> "🏃"
    "Ocio" -> "🎉"
    "Compras" -> "🛍️"
    "Recados" -> "📋"
    "Personal" -> "🙂"
    else -> "🗓️"
}

// endregion

// region Recados

private fun errandsMessage(errands: ErrandsDay): ChatMessage {
    val list = errands.titles.dropLast(1).joinToString(", ") + " y " + errands.titles.last()
    return assistant(
        "errands-${errands.date}",
        "🧭 Hoy tienes ${errands.titles.size} salidas: $list. Si puedes, hazlas del tirón y te ahorras viajes. 🚶"
    )
}

// endregion

// region Cómo viene el día

/** Los solapes (para mover alguno) y, si va a tope, las horas ocupadas con un consejo. */
private fun dayLoadMessage(load: DayLoad): ChatMessage {
    val day = if (load.isTomorrow) "Mañana" else "Hoy"
    val clashes = when (load.clashes.size) {
        0 -> null
        1 -> load.clashes.single().let {
            "⚠️ $day se te pisan «${it.firstTitle}» y «${it.secondTitle}» (de ${clockText(it.from)} a ${clockText(it.to)})."
        }
        else -> "⚠️ $day se te pisan ${load.clashes.size} veces:\n" +
            load.clashes.joinToString("\n") { "• «${it.firstTitle}» y «${it.secondTitle}», de ${clockText(it.from)} a ${clockText(it.to)}" }
    }?.let { "$it\n¿Te da tiempo a todo o mueves alguno? 🗓️" }
    val overload = if (load.isOverloaded) {
        val opening = if (clashes == null) "🔋 $day vas a tope" else "🔋 Y vas a tope"
        "$opening: ${load.eventCount} eventos y ${hoursText(load.busyMinutes)} ocupadas, " +
            "de ${clockText(load.firstStart)} a ${clockText(load.lastEnd)}. Guárdate un rato para comer y respirar. 🌿"
    } else {
        null
    }
    return assistant("day-load-${load.date}", listOfNotNull(clashes, overload).joinToString("\n\n"))
}

/** "9:30", "18:00"; un evento que llega a la medianoche acaba a las "24:00". */
private fun clockText(time: LocalTime): String =
    if (time == LocalTime.MIDNIGHT) "24:00" else "${time.hour}:${time.minute.toString().padStart(2, '0')}"

// endregion

// region Hábitos

private fun habitQuestionText(title: String, weekday: java.time.DayOfWeek) =
    "🔁 Veo que «$title» lo sueles apuntar los ${weekday.spanishName()}. ¿Quieres que se repita solo cada ${weekday.spanishName()}?"

private fun habitQuestion(habit: Habit): ChatMessage = assistant(
    habitId(habit.key),
    habitQuestionText(habit.title, habit.weekday),
    options = listOf(
        ChatOption(ChatReply.HABIT_REPEAT, "Sí, cada ${habit.weekday.spanishName()}"),
        ChatOption(ChatReply.HABIT_DISMISS, "No, gracias")
    )
)

private fun answeredHabitMessages(answer: HabitAnswer): List<ChatMessage> {
    val id = habitId(answer.key)
    val day = answer.weekday.spanishName()
    return listOf(
        assistant(id, habitQuestionText(answer.title, answer.weekday)),
        user("$id-answer", if (answer.accepted) "Sí, cada $day" else "No, gracias"),
        assistant(
            "$id-done",
            if (answer.accepted) "Hecho: «${answer.title}» se repetirá cada $day. Lo puedes cambiar desde su detalle. 🔁"
            else "Vale, no te lo vuelvo a proponer. 👍"
        )
    )
}

fun habitId(key: String) = "habit-$key"

// endregion

// region Prioridades del día

private fun focusQuestion(focus: FocusCandidates): ChatMessage = assistant(
    focusId(focus.date),
    "¿Cuáles son tus $MAX_FOCUS_TASKS prioridades de hoy? Márcalas y te las destaco en Inicio. 🎯",
    tasks = focus.tasks.map { it.toChatTask(focus.date) },
    maxSelectable = MAX_FOCUS_TASKS,
    options = listOf(ChatOption(ChatReply.FOCUS_CONFIRM, "Listo"), ChatOption(ChatReply.FOCUS_SKIP, "Hoy no"))
)

private fun answeredFocusMessages(focus: DailyFocus): List<ChatMessage> {
    val id = focusId(focus.date)
    val question = assistant(id, "¿Cuáles son tus $MAX_FOCUS_TASKS prioridades de hoy? Márcalas y te las destaco en Inicio. 🎯")
    return if (focus.skipped) {
        listOf(
            question,
            user("$id-answer", "Hoy no"),
            assistant("$id-done", "Vale, hoy sin prioridades. ¡Que vaya bien el día!")
        )
    } else {
        listOf(
            question,
            user("$id-answer", focus.tasks.joinToString("\n") { "⭐ ${it.title}" }),
            assistant("$id-done", "¡Perfecto! Te las destaco en Inicio. A por ellas 💪")
        )
    }
}

private fun focusId(date: LocalDate) = "focus-$date"

// endregion

private fun assistant(
    id: String,
    text: String,
    tasks: List<ChatTask> = emptyList(),
    taskTarget: MoveTarget? = null,
    maxSelectable: Int = 0,
    options: List<ChatOption> = emptyList()
) = ChatMessage(id, fromAssistant = true, text = text, tasks = tasks, taskTarget = taskTarget, maxSelectable = maxSelectable, options = options)

private fun user(id: String, text: String) = ChatMessage(id, fromAssistant = false, text = text)

private const val ID_HELLO = "hello"
private const val ID_DIGEST_ON = "digest_on"
private const val ID_DIGEST_QUESTION = "digest_question"
private const val ID_USER_ANSWER = "user_digest_answer"
private const val ID_DIGEST_ACCEPTED = "digest_accepted"
private const val ID_DIGEST_BLOCKED = "digest_blocked"
private const val ID_DIGEST_DECLINED = "digest_declined"

// region Versión nueva

/** Por versión: si sale otra más nueva, es un mensaje nuevo (y vuelve a contar como sin leer). */
fun updateMessageId(update: AppUpdate) = "update-${update.versionCode}"

private fun updateMessages(update: AppUpdate, dismissed: Boolean): List<ChatMessage> {
    val id = updateMessageId(update)
    val question = assistant(
        id,
        "🎉 ¡Hay una versión nueva de Syncro! Trae mejoras y arreglos. Actualiza desde Google Play en un momento, sin perder nada de lo tuyo.",
        options = if (dismissed) {
            emptyList()
        } else {
            listOf(ChatOption(ChatReply.UPDATE_NOW, "Actualizar"), ChatOption(ChatReply.UPDATE_LATER, "Más tarde"))
        }
    )
    if (!dismissed) return listOf(question)
    return listOf(
        question,
        user("$id-answer", "Más tarde"),
        assistant("$id-later", "¡Vale! Cuando quieras la tienes en Google Play. 😉")
    )
}

// endregion
