package com.syncro.presentation.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.syncro.domain.model.AssistantConversation
import com.syncro.domain.model.ChatMessage
import com.syncro.domain.model.ChatReply
import com.syncro.domain.model.DigestAnswer
import com.syncro.presentation.components.SyncroIconButton

/** Si Android deja publicar avisos: permiso concedido (Android 13+) y notificaciones sin silenciar. */
fun Context.notificationsAllowed(): Boolean {
    val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return permissionGranted && NotificationManagerCompat.from(this).areNotificationsEnabled()
}

/**
 * Pasa al [AssistantViewModel] si hay permiso de avisos, y lo vuelve a mirar cada vez que la app
 * vuelve a primer plano (el usuario puede cambiarlo en los ajustes del sistema).
 */
@Composable
fun TrackNotificationsAllowed(viewModel: AssistantViewModel) {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.onNotificationsAllowedChanged(context.notificationsAllowed())
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
        onReply = { reply ->
            when (reply) {
                ChatReply.ENABLE_DIGEST ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // Antes de Android 13 no hay permiso que pedir
                        viewModel.answerDigest(DigestAnswer.ACCEPTED)
                    }
                ChatReply.NOT_NOW -> viewModel.answerDigest(DigestAnswer.DECLINED)
            }
        },
        onClearChat = viewModel::clearChat
    )
}

/** Chat al estilo WhatsApp: el asistente a la izquierda, el usuario a la derecha y opciones para contestar. */
@Composable
fun AssistantChatContent(
    conversation: AssistantConversation?,
    onReply: (ChatReply) -> Unit,
    onClearChat: () -> Unit
) {
    val listState = rememberLazyListState()
    val messages = conversation?.messages.orEmpty()
    val replies = conversation?.replies.orEmpty()
    var confirmClear by remember { mutableStateOf(false) }
    // Al tocar una opción se ocultan en el acto (sin esperar a que se guarde la respuesta)
    var repliesUsed by remember(replies) { mutableStateOf(false) }
    val visibleReplies = if (repliesUsed) emptyList() else replies

    // Al llegar un mensaje nuevo, baja hasta el final como en cualquier chat
    LaunchedEffect(messages.size, visibleReplies.size) {
        // Elementos: "Hoy" + mensajes + opciones (si hay)
        val lastIndex = messages.size + (if (visibleReplies.isNotEmpty()) 1 else 0)
        if (messages.isNotEmpty()) listState.animateScrollToItem(lastIndex)
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
                    ChatBubble(
                        message = message,
                        withTail = startsRun,
                        modifier = Modifier
                            .animateItem()
                            .padding(top = if (startsRun) 8.dp else 0.dp)
                    )
                }

                if (visibleReplies.isNotEmpty()) {
                    item(key = "replies") {
                        ReplyOptions(
                            replies = visibleReplies,
                            onReply = { reply ->
                                repliesUsed = true
                                onReply(reply)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
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
private fun ChatBubble(message: ChatMessage, withTail: Boolean, modifier: Modifier = Modifier) {
    val fromAssistant = message.fromAssistant
    val tail = if (withTail) 4.dp else 18.dp
    val shape = if (fromAssistant) {
        RoundedCornerShape(topStart = tail, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = tail, bottomEnd = 18.dp, bottomStart = 18.dp)
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (fromAssistant) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = shape,
            color = if (fromAssistant) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    message.text,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (fromAssistant) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
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
        }
    }
}

/** Las opciones de la pregunta, del lado del usuario: "Ahora no" en texto y la principal rellena. */
@Composable
private fun ReplyOptions(replies: List<ChatReply>, onReply: (ChatReply) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        replies.drop(1).forEach { reply ->
            TextButton(onClick = { onReply(reply) }) { Text(reply.label) }
        }
        replies.firstOrNull()?.let { reply ->
            Button(onClick = { onReply(reply) }, shape = RoundedCornerShape(14.dp)) {
                Text(reply.label, fontWeight = FontWeight.Bold)
            }
        }
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
