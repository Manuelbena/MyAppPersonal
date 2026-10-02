package com.syncro.domain.model

import java.time.LocalDate
import java.time.YearMonth

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
    PAYDAY_LATER
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
 */
data class ChatMessage(
    val id: String,
    val fromAssistant: Boolean,
    val text: String,
    val tasks: List<ChatTask> = emptyList(),
    val taskTarget: MoveTarget? = null,
    val maxSelectable: Int = 0,
    val options: List<ChatOption> = emptyList()
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
 * @param focus las tareas de hoy entre las que elegir prioridades (null desde las 21:00)
 * @param focusHistory las prioridades ya elegidas (la de hoy y las recientes)
 * @param payday el último día de nómina si fue hace poco (null sin nómina en Ajustes)
 * @param budgetAlerts los presupuestos que llegaron al 80 % o se pasaron hace poco
 * @param clearedIds mensajes que el usuario borró al vaciar el chat (no vuelven a salir)
 */
fun assistantConversation(
    answer: DigestAnswer?,
    notificationsAllowed: Boolean,
    leftovers: LeftoverTasks? = null,
    outcomes: List<LeftoverOutcome> = emptyList(),
    focus: FocusCandidates? = null,
    focusHistory: List<DailyFocus> = emptyList(),
    payday: Payday? = null,
    budgetAlerts: List<BudgetAlert> = emptyList(),
    clearedIds: Set<String> = emptySet()
): AssistantConversation {
    val messages = digestMessages(answer, notificationsAllowed)

    // Repasos y prioridades por orden de llegada: el repaso de un día se contesta por la noche (o a
    // la mañana siguiente), así que va detrás de las prioridades de ese día y delante de las del siguiente
    val blocks = mutableListOf<Pair<Moment, List<ChatMessage>>>()
    outcomes.forEach { outcome ->
        val pending = leftovers?.takeIf { it.reviewDate == outcome.reviewDate }?.tasks.orEmpty()
        blocks += Moment(outcome.reviewDate.plusDays(1), LEFTOVERS_ORDER) to answeredLeftoverMessages(outcome, pending)
    }
    val unanswered = leftovers?.takeIf { current ->
        current.tasks.isNotEmpty() && outcomes.none { it.reviewDate == current.reviewDate }
    }
    unanswered?.let { blocks += Moment(it.reviewDate.plusDays(1), LEFTOVERS_ORDER) to listOf(leftoverQuestion(it)) }
    val leftoverPending = unanswered != null
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
    blocks.sortedWith(compareBy({ it.first.day }, { it.first.order })).forEach { messages += it.second }

    return AssistantConversation(messages.filter { it.id !in clearedIds })
}

/** Posición de un bloque en el chat: el día y, dentro del día, repaso (antes) o prioridades. */
private data class Moment(val day: LocalDate, val order: Int)
private const val PAYDAY_ORDER = -1
private const val LEFTOVERS_ORDER = 0
private const val FOCUS_ORDER = 1
private const val BUDGET_ORDER = 2

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
    val name = "${budget.category.emoji} ${budget.category.label}"
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
    return assistant("budget-${YearMonth.from(alert.date)}-${budget.category.name}-$level", text)
}

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
