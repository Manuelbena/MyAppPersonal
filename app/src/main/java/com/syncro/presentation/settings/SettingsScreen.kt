package com.syncro.presentation.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.syncro.data.preferences.ThemeMode
import com.syncro.domain.model.AssistantSettings
import com.syncro.domain.model.DataLossSummary
import com.syncro.domain.model.DigestSettings
import com.syncro.presentation.components.canAskNotificationPermission
import com.syncro.presentation.components.markNotificationPermissionAsked
import com.syncro.presentation.components.notificationsAllowed
import com.syncro.presentation.components.openNotificationSettings
import com.syncro.presentation.components.SyncroIconButton
import com.syncro.presentation.components.UserAvatar
import com.syncro.presentation.legal.LegalDocumentId
import com.syncro.presentation.components.toDisplayTime
import java.time.LocalTime

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLegal: (LegalDocumentId) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // El permiso se puede cambiar en los ajustes del sistema: se vuelve a mirar al volver
    var notificationsAllowed by remember { mutableStateOf(context.notificationsAllowed()) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = context.notificationsAllowed()
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Si dice que no en el diálogo, se respeta: no se le manda a los ajustes
        notificationsAllowed = granted && context.notificationsAllowed()
    }
    LaunchedEffect(viewModel.messages) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        SettingsContent(
            state = state,
            notificationsAllowed = notificationsAllowed,
            appVersion = remember { context.appVersion() },
            onBack = onBack,
            onNotificationsToggle = { on ->
                when {
                    !on -> context.openNotificationSettings()
                    // Android 13+: el diálogo del sistema mientras Android deje preguntar
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                        if (context.canAskNotificationPermission()) {
                            context.markNotificationPermissionAsked()
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            // Ya lo denegó y Android no vuelve a mostrar el diálogo: solo queda activarlo allí
                            context.openNotificationSettings()
                        }
                    // Con permiso pero silenciadas en el sistema: solo se pueden activar allí
                    else -> context.openNotificationSettings()
                }
            },
            onDigestChange = viewModel::updateDigest,
            onAssistantChange = viewModel::updateAssistant,
            onThemeChange = viewModel::setThemeMode,
            onLogoutClick = viewModel::requestLogout,
            onLogoutConfirm = viewModel::confirmLogout,
            onLogoutDismiss = viewModel::dismissLogout,
            onOpenLegal = onOpenLegal,
            modifier = Modifier.padding(padding)
        )
    }
}

/** La pantalla sin ViewModel ni sistema, para poder probarla. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    notificationsAllowed: Boolean,
    appVersion: String,
    onBack: () -> Unit,
    /** Activar pide el permiso; desactivar lleva a los ajustes del sistema (una app no puede quitárselo). */
    onNotificationsToggle: (Boolean) -> Unit,
    onDigestChange: ((DigestSettings) -> DigestSettings) -> Unit,
    onAssistantChange: ((AssistantSettings) -> AssistantSettings) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLogoutClick: () -> Unit,
    onLogoutConfirm: () -> Unit,
    onLogoutDismiss: () -> Unit,
    onOpenLegal: (LegalDocumentId) -> Unit,
    modifier: Modifier = Modifier
) {
    val digest = state.settings.digest
    val assistant = state.settings.assistant
    // Qué hora se está cambiando (mañana o noche)
    var editingMorning by remember { mutableStateOf<Boolean?>(null) }
    var editingPayday by remember { mutableStateOf(false) }
    // Apartado abierto; null = la lista de categorías. Sobrevive a girar la pantalla
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    // El botón atrás del móvil vuelve a la lista antes de salir de Ajustes
    BackHandler(enabled = page != null) { page = null }

    Column(modifier = modifier.fillMaxSize()) {
        // Misma cabecera que las demás pantallas: título grande y botón redondo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SyncroIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                onClick = { if (page != null) page = null else onBack() },
                contentDescription = "Volver"
            )
            Spacer(Modifier.width(16.dp))
            Text(
                page?.title ?: "Ajustes",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Los apartados entran por la derecha y vuelven por la izquierda, como una página más
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally(tween(300)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
            },
            label = "SettingsPage"
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                when (current) {
                    null -> {
                        state.user?.let { user ->
                            SettingsCard {
                                ProfileHeader(user.name, user.email, user.photoUrl, onClick = { page = SettingsPage.ACCOUNT })
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                        SettingsCard {
                            CategoryRow(SettingsPage.NOTIFICATIONS, if (notificationsAllowed) "Activadas" else "Desactivadas") { page = it }
                            SettingsDivider()
                            CategoryRow(SettingsPage.DIGEST, digestSummary(digest)) { page = it }
                            SettingsDivider()
                            CategoryRow(SettingsPage.ASSISTANT, assistantSummary(assistant)) { page = it }
                            SettingsDivider()
                            CategoryRow(SettingsPage.APPEARANCE, themeLabel(state.themeMode)) { page = it }
                            SettingsDivider()
                            CategoryRow(SettingsPage.LEGAL, "Privacidad, términos y licencias") { page = it }
                            SettingsDivider()
                            CategoryRow(SettingsPage.ABOUT, "Versión $appVersion") { page = it }
                        }
                    }

                    SettingsPage.ACCOUNT -> state.user?.let { user ->
                        SettingsCard {
                            ProfileHeader(user.name, user.email, user.photoUrl, onClick = null)
                        }
                        Spacer(Modifier.height(16.dp))
                        SettingsCard {
                            SettingsRow(
                                title = "Cerrar sesión",
                                subtitle = "Se borran de este móvil los datos de la cuenta",
                                titleColor = MaterialTheme.colorScheme.error,
                                onClick = onLogoutClick,
                                trailing = {
                                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            )
                        }
                    }

                    SettingsPage.NOTIFICATIONS -> SettingsCard {
                        SwitchRow(
                            title = "Recibir notificaciones",
                            subtitle = if (notificationsAllowed) {
                                "Activadas. Para quitarlas te llevo a los ajustes del móvil"
                            } else {
                                "Desactivadas: no te llegará ningún aviso"
                            },
                            checked = notificationsAllowed,
                            onCheckedChange = onNotificationsToggle
                        )
                    }

                    SettingsPage.DIGEST -> SettingsCard {
                        SwitchRow(
                            title = "Resumen de la mañana",
                            subtitle = "Lo que te espera hoy",
                            checked = digest.morningEnabled,
                            onCheckedChange = { on -> onDigestChange { it.copy(morningEnabled = on) } }
                        )
                        if (digest.morningEnabled) {
                            TimeRow("Hora", digest.morningTime) { editingMorning = true }
                        }
                        SettingsDivider()
                        SwitchRow(
                            title = "Resumen de la noche",
                            subtitle = "Cómo ha ido el día y qué hacer con lo pendiente",
                            checked = digest.eveningEnabled,
                            onCheckedChange = { on -> onDigestChange { it.copy(eveningEnabled = on) } }
                        )
                        // La hora de la noche se ve siempre: también marca cuándo acaba el día para el asistente
                        TimeRow("Hora de cierre del día", digest.eveningTime) { editingMorning = false }
                    }

                    SettingsPage.ASSISTANT -> SettingsCard {
                        SwitchRow(
                            title = "Prioridades del día",
                            subtitle = "Cada mañana te pregunta cuáles son tus 3 tareas importantes",
                            checked = assistant.focusEnabled,
                            onCheckedChange = { on -> onAssistantChange { it.copy(focusEnabled = on) } }
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = "Repaso de pendientes",
                            subtitle = "Te pregunta qué hacer con las tareas que se quedaron sin hacer",
                            checked = assistant.leftoversEnabled,
                            onCheckedChange = { on -> onAssistantChange { it.copy(leftoversEnabled = on) } }
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = "Avisos de presupuesto",
                            subtitle = "Te avisa al llegar al 80 % de un presupuesto y si te pasas",
                            checked = assistant.budgetAlertsEnabled,
                            onCheckedChange = { on -> onAssistantChange { it.copy(budgetAlertsEnabled = on) } }
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = "Frase del día",
                            subtitle = "Una frase para empezar el día en Inicio. Con la ✕ la ocultas solo hasta mañana",
                            checked = assistant.dailyQuoteEnabled,
                            onCheckedChange = { on -> onAssistantChange { it.copy(dailyQuoteEnabled = on) } }
                        )
                        SettingsDivider()
                        SettingsRow(
                            title = "Día de nómina",
                            subtitle = "El día que cobras te ayuda a organizar el dinero del mes",
                            onClick = { editingPayday = true },
                            trailing = {
                                Text(
                                    paydayLabel(assistant.paydayDay),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }

                    SettingsPage.APPEARANCE -> SettingsCard {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Tema", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(12.dp))
                            val options = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                options.forEachIndexed { index, mode ->
                                    SegmentedButton(
                                        selected = state.themeMode == mode,
                                        onClick = { onThemeChange(mode) },
                                        shape = SegmentedButtonDefaults.itemShape(index, options.size)
                                    ) { Text(themeLabel(mode)) }
                                }
                            }
                        }
                    }

                    SettingsPage.LEGAL -> SettingsCard {
                        LegalDocumentId.entries.forEachIndexed { index, doc ->
                            if (index > 0) SettingsDivider()
                            SettingsRow(
                                title = doc.title,
                                subtitle = doc.summary,
                                onClick = { onOpenLegal(doc) },
                                trailing = {
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            )
                        }
                    }

                    SettingsPage.ABOUT -> SettingsCard {
                        SettingsRow(title = "Versión", trailing = {
                            Text(appVersion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        })
                    }
                }
            }
        }
    }

    editingMorning?.let { morning ->
        TimePickerDialog(
            title = if (morning) "Resumen de la mañana" else "Hora de cierre del día",
            initial = if (morning) digest.morningTime else digest.eveningTime,
            onDismiss = { editingMorning = null },
            onConfirm = { time ->
                editingMorning = null
                onDigestChange { if (morning) it.copy(morningTime = time) else it.copy(eveningTime = time) }
            }
        )
    }

    if (editingPayday) {
        PaydayDialog(
            initial = assistant.paydayDay,
            onDismiss = { editingPayday = false },
            onConfirm = { day ->
                editingPayday = false
                onAssistantChange { it.copy(paydayDay = day) }
            }
        )
    }

    state.logoutPrompt?.let { loss ->
        LogoutDialog(loss = loss, isLoggingOut = state.isLoggingOut, onConfirm = onLogoutConfirm, onDismiss = onLogoutDismiss)
    }

}

// region Categorías

/** Los apartados de Ajustes: cada uno es una página propia a la que se llega desde la lista. */
enum class SettingsPage(val title: String, val icon: ImageVector) {
    ACCOUNT("Cuenta", Icons.Outlined.AccountCircle),
    NOTIFICATIONS("Notificaciones", Icons.Outlined.Notifications),
    DIGEST("Resumen del día", Icons.Outlined.WbSunny),
    ASSISTANT("Asistente", Icons.Outlined.AutoAwesome),
    APPEARANCE("Apariencia", Icons.Outlined.Palette),
    LEGAL("Privacidad y legal", Icons.Outlined.Shield),
    ABOUT("Acerca de", Icons.Outlined.Info)
}

private fun digestSummary(digest: DigestSettings): String = listOfNotNull(
    digest.morningTime.toDisplayTime().takeIf { digest.morningEnabled },
    digest.eveningTime.toDisplayTime().takeIf { digest.eveningEnabled }
).joinToString(" · ").ifEmpty { "Desactivado" }

private fun assistantSummary(assistant: AssistantSettings): String = listOfNotNull(
    "Prioridades".takeIf { assistant.focusEnabled },
    "Pendientes".takeIf { assistant.leftoversEnabled },
    "Presupuestos".takeIf { assistant.budgetAlertsEnabled },
    "Frase".takeIf { assistant.dailyQuoteEnabled },
    assistant.paydayDay?.let { "Nómina día $it" }
).joinToString(" · ").ifEmpty { "Sin preguntas" }

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Sistema"
    ThemeMode.LIGHT -> "Claro"
    ThemeMode.DARK -> "Oscuro"
}

/** Botón de una categoría: icono en su cuadro de color, nombre, estado actual y flecha. */
@Composable
private fun CategoryRow(page: SettingsPage, summary: String, onOpen: (SettingsPage) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(page) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(page.icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(page.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Foto, nombre y correo. En la lista lleva a "Cuenta"; dentro de "Cuenta" no se toca. */
@Composable
private fun ProfileHeader(name: String, email: String, photoUrl: String?, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(name = name, photoUrl = photoUrl, size = 64.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name.ifBlank { "Tu cuenta" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Sincronizado con Google Calendar y Tasks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// endregion

// region Piezas

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = titleColor)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.let {
            Spacer(Modifier.width(12.dp))
            it()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // Toda la fila activa el interruptor, no solo el botón
    SettingsRow(
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}

@Composable
private fun TimeRow(title: String, time: LocalTime, onClick: () -> Unit) {
    SettingsRow(
        title = title,
        onClick = onClick,
        trailing = {
            Text(
                time.toDisplayTime(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Elegir el día de cobro: "No tengo nómina" (lo de fábrica: sin trabajo aún, autónomos…) o un día
 * del 1 al 31. En los meses más cortos se usa el último día.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaydayDialog(initial: Int?, onDismiss: () -> Unit, onConfirm: (Int?) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Qué día cobras la nómina?") },
        text = {
            // Con scroll: en un móvil pequeño los 31 días podrían no caber
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.RadioButton) { selected = null },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == null, onClick = { selected = null })
                    Column {
                        Text("No tengo nómina", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Si aún no trabajas o no cobras un sueldo fijo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..31).forEach { day ->
                        val isSelected = selected == day
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(role = Role.RadioButton) { selected = day }
                                .semantics { contentDescription = "Día $day" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$day",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    "Si el mes tiene menos días, será el último día del mes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun paydayLabel(day: Int?): String = day?.let { "Día $it" } ?: "No tengo"

@Composable
private fun LogoutDialog(loss: DataLossSummary, isLoggingOut: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!isLoggingOut) onDismiss() },
        title = { Text("¿Cerrar sesión?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se borrarán de este móvil tus tareas, eventos, notas, ingresos, gastos y presupuestos, y el chat del asistente. Lo que ya está en Google volverá al entrar de nuevo.")
                if (loss.unsyncedChanges > 0) {
                    Text(
                        "⚠️ ${changes(loss.unsyncedChanges)} aún sin subir a Google: se perderán.",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (loss.notes > 0) {
                    Text(
                        "⚠️ Las notas solo se guardan en el móvil: perderás ${notes(loss.notes)}.",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (loss.movements > 0) {
                    Text(
                        "⚠️ Los ingresos y gastos solo se guardan en el móvil: perderás ${movements(loss.movements)}.",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isLoggingOut) {
                Text("Cerrar sesión", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoggingOut) { Text("Cancelar") } }
    )
}

private fun changes(n: Int) = if (n == 1) "Tienes 1 cambio" else "Tienes $n cambios"
private fun notes(n: Int) = if (n == 1) "1 nota" else "$n notas"
private fun movements(n: Int) = if (n == 1) "1 movimiento" else "$n movimientos"

// endregion

private fun Context.appVersion(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "—"

