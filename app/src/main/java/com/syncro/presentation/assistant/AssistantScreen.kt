package com.syncro.presentation.assistant

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.syncro.domain.model.AssistantConversation
import com.syncro.domain.model.ChatMessage
import com.syncro.domain.model.ChatOption
import com.syncro.domain.model.ChatReply
import com.syncro.domain.model.ChatTask
import com.syncro.domain.model.DigestAnswer
import com.syncro.domain.model.MoveTarget
import com.syncro.domain.model.TaskAction
import com.syncro.presentation.components.SyncroIconButton
import com.syncro.presentation.components.canAskNotificationPermission
import com.syncro.presentation.components.markNotificationPermissionAsked
import com.syncro.presentation.components.notificationsAllowed
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Cada vez que la app vuelve a primer plano, pone al día el [AssistantViewModel]: el permiso de
 * avisos (se puede cambiar en los ajustes) y el repaso de pendientes (puede que ya sean las 21:00).
 */
@Composable
fun TrackAssistantOnResume(viewModel: AssistantViewModel) {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.onResume(context.notificationsAllowed())
        onPauseOrDispose { }
    }
}

@Composable
fun AssistantMainScreen(viewModel: AssistantViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Primero el permiso y luego la respuesta: así no asoma un instante el mensaje de "bloqueado"
        viewModel.onNotificationsAllowedChanged(context.notificationsAllowed())
        viewModel.answerDigest(DigestAnswer.ACCEPTED)
    }

    // Lo que está en pantalla queda leído (también las respuestas que llegan con el chat abierto)
    val messages = state?.conversation?.messages
    LaunchedEffect(messages) { if (messages != null) viewModel.markAllRead() }

    AssistantChatContent(
        conversation = state?.conversation,
        firstSelectableDay = viewModel.firstSelectableDay(),
        onReply = { reply, selectedTaskIds ->
            when (reply) {
                ChatReply.ENABLE_DIGEST ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.canAskNotificationPermission()) {
                        context.markNotificationPermissionAsked()
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // Sin diálogo que mostrar (Android 12 o denegado para siempre): se da por aceptado
                        // y, si Android lo bloquea, el asistente explica cómo activarlo en los ajustes
                        viewModel.answerDigest(DigestAnswer.ACCEPTED)
                    }
                ChatReply.NOT_NOW -> viewModel.answerDigest(DigestAnswer.DECLINED)
                ChatReply.LEFTOVERS_MOVE_ALL, ChatReply.LEFTOVERS_ONE_BY_ONE, ChatReply.LEFTOVERS_KEEP ->
                    viewModel.answerLeftovers(reply)
                ChatReply.FOCUS_CONFIRM, ChatReply.FOCUS_SKIP -> viewModel.answerFocus(reply, selectedTaskIds)
            }
        },
        onTaskAction = viewModel::resolveLeftover,
        onClearChat = viewModel::clearChat
    )
}

/** Chat al estilo WhatsApp: el asistente a la izquierda, el usuario a la derecha y opciones para contestar. */
@Composable
fun AssistantChatContent(
    conversation: AssistantConversation?,
    firstSelectableDay: LocalDate,
    /** La opción tocada y, si el mensaje permite marcar tareas, las marcadas. */
    onReply: (reply: ChatReply, selectedTaskIds: List<String>) -> Unit,
    onTaskAction: (taskId: String, action: TaskAction, otherDay: LocalDate?) -> Unit,
    onClearChat: () -> Unit
) {
    val listState = rememberLazyListState()
    val messages = conversation?.messages.orEmpty()
    var confirmClear by remember { mutableStateOf(false) }
    // Tarea para la que se está eligiendo "Otro día"
    var pickingDayFor by remember { mutableStateOf<String?>(null) }
    // Al tocar una opción se ocultan en el acto (sin esperar a que se guarde la respuesta)
    var answeredIds by remember { mutableStateOf(emptySet<String>()) }
    var selections by remember { mutableStateOf(emptyMap<String, Set<String>>()) }

    // Al llegar un mensaje nuevo, baja hasta el final como en cualquier chat ("Hoy" + mensajes)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ChatHeader(
            canClear = messages.isNotEmpty(),
            onClearClick = { confirmClear = true }
        )

        if (conversation != null && messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 140.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No hay mensajes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Abajo deja sitio a la barra de navegación flotante
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "day") { DayChip("Hoy") }

                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                    // Solo el primero de una racha del mismo lado lleva el "pico" de la burbuja
                    val startsRun = index == 0 || messages[index - 1].fromAssistant != message.fromAssistant
                    Column(
                        modifier = Modifier
                            .animateItem()
                            .padding(top = if (startsRun) 8.dp else 0.dp)
                    ) {
                        // Tareas marcadas en este mensaje (prioridades); solo mientras se contesta
                        val selected = selections[message.id].orEmpty()
                        ChatBubble(
                            message = message,
                            withTail = startsRun,
                            selected = selected,
                            onToggleSelected = { taskId ->
                                selections = selections + (message.id to (if (taskId in selected) selected - taskId else selected + taskId))
                            },
                            onTaskAction = { taskId, action ->
                                if (action == TaskAction.OTHER_DAY) pickingDayFor = taskId
                                else onTaskAction(taskId, action, null)
                            }
                        )
                        val options = if (message.id in answeredIds) emptyList() else message.options
                        if (options.isNotEmpty()) {
                            ReplyOptions(
                                options = options,
                                // "Listo" sin nada marcado no significa nada: se desactiva
                                isEnabled = { it != ChatReply.FOCUS_CONFIRM || selected.isNotEmpty() },
                                onReply = { reply ->
                                    answeredIds = answeredIds + message.id
                                    // En el orden de la lista, no en el que se tocaron
                                    onReply(reply, message.tasks.map { it.id }.filter { it in selected })
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    pickingDayFor?.let { taskId ->
        OtherDayPicker(
            firstSelectableDay = firstSelectableDay,
            onDismiss = { pickingDayFor = null },
            onPick = { day ->
                pickingDayFor = null
                onTaskAction(taskId, TaskAction.OTHER_DAY, day)
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Vaciar el chat?") },
            text = { Text("Se borrarán todos los mensajes de la conversación.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearChat()
                }) { Text("Vaciar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancelar") }
            }
        )
    }
}

/** Misma cabecera que Inicio: título grande, subtítulo y botones redondos a la derecha. */
@Composable
private fun ChatHeader(canClear: Boolean, onClearClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                "Asistente",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "en línea",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (canClear) {
            SyncroIconButton(
                icon = Icons.Outlined.DeleteSweep,
                onClick = onClearClick,
                contentDescription = "Vaciar el chat"
            )
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    withTail: Boolean,
    selected: Set<String>,
    onToggleSelected: (taskId: String) -> Unit,
    onTaskAction: (taskId: String, action: TaskAction) -> Unit
) {
    val fromAssistant = message.fromAssistant
    val tail = if (withTail) 4.dp else 18.dp
    val shape = if (fromAssistant) {
        RoundedCornerShape(topStart = tail, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = tail, bottomEnd = 18.dp, bottomStart = 18.dp)
    }
    val textColor = if (fromAssistant) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (fromAssistant) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = shape,
            color = if (fromAssistant) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        message.text,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                    if (!fromAssistant) {
                        // El doble check de "leído", como en WhatsApp
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.DoneAll,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (message.tasks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val selectable = message.maxSelectable > 0
                    message.tasks.forEach { task ->
                        val isSelected = task.id in selected
                        TaskRow(
                            task = task,
                            target = message.taskTarget,
                            selection = when {
                                !selectable -> null
                                // Con el cupo lleno, las demás no se pueden marcar (sí desmarcar)
                                else -> TaskSelection(isSelected, enabled = isSelected || selected.size < message.maxSelectable)
                            },
                            onToggle = { onToggleSelected(task.id) },
                            onAction = { onTaskAction(task.id, it) }
                        )
                    }
                    if (selectable) {
                        Text(
                            "${selected.size} de ${message.maxSelectable} elegidas",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Si la tarea se puede marcar (prioridades): si lo está y si aún se puede tocar. */
private data class TaskSelection(val selected: Boolean, val enabled: Boolean)

/**
 * Una tarea dentro de la burbuja. En el modo "una a una" lleva debajo sus tres acciones; al elegir
 * prioridades se marca con una estrella tocando la fila.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(
    task: ChatTask,
    target: MoveTarget?,
    selection: TaskSelection?,
    onToggle: () -> Unit,
    onAction: (TaskAction) -> Unit
) {
    val rowModifier = if (selection != null) {
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .toggleable(value = selection.selected, enabled = selection.enabled, role = Role.Checkbox, onValueChange = { onToggle() })
    } else {
        Modifier
    }
    Column(modifier = rowModifier.padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.alpha(if (selection?.enabled == false) 0.4f else 1f)
        ) {
            Icon(
                when {
                    selection == null -> Icons.Outlined.RadioButtonUnchecked
                    selection.selected -> Icons.Rounded.Star
                    else -> Icons.Rounded.StarBorder
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (selection == null) 18.dp else 22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                task.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (target != null) {
            FlowRow(
                modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskActionChip(target.label, Icons.Outlined.Schedule) { onAction(TaskAction.MOVE_TO_TARGET) }
                TaskActionChip("Otro día", Icons.Outlined.CalendarMonth) { onAction(TaskAction.OTHER_DAY) }
                TaskActionChip("Hecha", Icons.Outlined.CheckCircle) { onAction(TaskAction.DONE) }
            }
        }
    }
}

@Composable
private fun TaskActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        shape = RoundedCornerShape(12.dp)
    )
}

/** Las opciones de un mensaje, del lado del usuario: la principal rellena y las demás en texto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplyOptions(
    options: List<ChatOption>,
    isEnabled: (ChatReply) -> Boolean,
    onReply: (ChatReply) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.drop(1).reversed().forEach { option ->
            TextButton(onClick = { onReply(option.reply) }, enabled = isEnabled(option.reply)) { Text(option.label) }
        }
        options.firstOrNull()?.let { option ->
            Button(onClick = { onReply(option.reply) }, enabled = isEnabled(option.reply), shape = RoundedCornerShape(14.dp)) {
                Text(option.label, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Selector de día para "Otro día"; solo deja elegir desde el día propuesto en adelante. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtherDayPicker(firstSelectableDay: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    // El DatePicker trabaja en milisegundos UTC a medianoche
    val firstMillis = firstSelectableDay.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = firstMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= firstMillis
            override fun isSelectableYear(year: Int) = year >= firstSelectableDay.year
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("Mover") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun DayChip(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
