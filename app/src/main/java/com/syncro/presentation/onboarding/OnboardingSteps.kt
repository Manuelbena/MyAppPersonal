package com.syncro.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syncro.data.preferences.ThemeMode
import com.syncro.domain.model.AssistantSettings
import com.syncro.domain.model.DigestSettings
import com.syncro.presentation.theme.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/*
 * Lo de cada paso de la guía de inicio. Pocas palabras, un dibujo y lo justo para decidir; el
 * detalle está en Ajustes.
 */

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

/** Título grande y una frase debajo, centrados. */
@Composable
private fun StepTitle(title: String, subtitle: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(8.dp))
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
}

/** Una tarjeta redondeada como las de la app. */
@Composable
private fun OnboardingCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/** El emoji en un cuadrado suave del color del paso. */
@Composable
private fun EmojiBadge(emoji: String, accent: Color) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
    }
}

/** Un ajuste que se enciende o apaga: emoji, título, una línea de explicación y el interruptor. */
@Composable
private fun ToggleRow(emoji: String, title: String, subtitle: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmojiBadge(emoji, accent)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent),
            modifier = Modifier.semantics { contentDescription = title }
        )
    }
}

// region Bienvenida

@Composable
fun WelcomeStep(firstName: String?) {
    StepTitle(
        title = firstName?.let { "¡Hola, $it! 👋" } ?: "¡Hola! 👋",
        subtitle = "Soy Syncro: tu día, tu dinero y un asistente, todo en un solo sitio. Vamos a dejarlo a tu gusto."
    )
    OnboardingCard {
        Feature("📅", "Tu día en un vistazo", "Tareas y eventos sincronizados con tu Google Calendar y Tasks", Sky500)
        Feature("💶", "Tu dinero bajo control", "Ingresos, gastos, presupuestos y cuentas de ahorro", Emerald500)
        Feature("🤖", "Un asistente que te echa una mano", "Te avisa, te resume el día y te ayuda a priorizar", Violet500)
    }
}

@Composable
private fun Feature(emoji: String, title: String, text: String, accent: Color) {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        EmojiBadge(emoji, accent)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// endregion

// region Avisos

private enum class DigestMomentPick { MORNING, EVENING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsStep(
    digest: DigestSettings,
    notificationsAllowed: Boolean,
    onEnableNotifications: () -> Unit,
    onDigestChange: ((DigestSettings) -> DigestSettings) -> Unit
) {
    val accent = OnboardingStep.NOTIFICATIONS.accent
    var picking by remember { mutableStateOf<DigestMomentPick?>(null) }

    StepTitle(
        title = "Que no se te escape nada",
        subtitle = "Te resumo el día por la mañana y por la noche, y te aviso de lo que tú elijas. Sin agobios, prometido 🤞"
    )

    // Cómo se verá: una notificación de ejemplo
    NotificationPreview()
    Spacer(Modifier.height(16.dp))

    if (notificationsAllowed) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Emerald500.copy(alpha = 0.12f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✅", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Text("Notificaciones activadas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Emerald500)
        }
    } else {
        Button(
            onClick = onEnableNotifications,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
        ) {
            Text("🔔  Activar notificaciones", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Text(
            "Sin este permiso no te llegará ningún aviso",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
    Spacer(Modifier.height(16.dp))

    OnboardingCard {
        DigestRow(
            emoji = "☀️",
            title = "Resumen de la mañana",
            subtitle = "Lo que te espera hoy",
            enabled = digest.morningEnabled,
            time = digest.morningTime,
            accent = accent,
            onToggle = { on -> onDigestChange { it.copy(morningEnabled = on) } },
            onPickTime = { picking = DigestMomentPick.MORNING }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))
        DigestRow(
            emoji = "🌙",
            title = "Resumen de la noche",
            subtitle = "Cómo ha ido y qué hacer con lo pendiente",
            enabled = digest.eveningEnabled,
            time = digest.eveningTime,
            accent = accent,
            onToggle = { on -> onDigestChange { it.copy(eveningEnabled = on) } },
            onPickTime = { picking = DigestMomentPick.EVENING }
        )
    }

    picking?.let { moment ->
        val initial = if (moment == DigestMomentPick.MORNING) digest.morningTime else digest.eveningTime
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(if (moment == DigestMomentPick.MORNING) "☀️ Resumen de la mañana" else "🌙 Resumen de la noche") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val time = LocalTime.of(state.hour, state.minute)
                    onDigestChange { if (moment == DigestMomentPick.MORNING) it.copy(morningTime = time) else it.copy(eveningTime = time) }
                    picking = null
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancelar") } }
        )
    }
}

/** Una notificación de mentira, para ver cómo será la de la mañana. */
@Composable
private fun NotificationPreview() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Cyan400),
                contentAlignment = Alignment.Center
            ) {
                Text("S", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Syncro · ahora", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("☀️ ¡Buenos días!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Hoy tienes 3 tareas y 2 eventos. Lo primero: Reunión a las 10:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DigestRow(
    emoji: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    time: LocalTime,
    accent: Color,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit
) {
    ToggleRow(emoji, title, subtitle, enabled, accent, onToggle)
    if (enabled) {
        Row(modifier = Modifier.padding(start = 58.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("A las", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = onPickTime,
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .semantics { contentDescription = "$title a las ${time.format(TIME)}. Cambiar hora" }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(time.format(TIME), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
                }
            }
        }
    }
}

// endregion

// region Dinero

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyStep(assistant: AssistantSettings, onAssistantChange: ((AssistantSettings) -> AssistantSettings) -> Unit) {
    val accent = OnboardingStep.MONEY.accent
    StepTitle(
        title = "Tu dinero, a tu ritmo",
        subtitle = "Si cobras una nómina, tus meses irán de nómina a nómina y te ayudaré a repartirla."
    )

    // La regla 50/30/20 en una barra: lo que se verá en los presupuestos
    OnboardingCard {
        Text("Así te ayudaré a repartirla", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
        ) {
            Box(Modifier.weight(50f).fillMaxHeight().background(Sky500))
            Box(Modifier.weight(30f).fillMaxHeight().background(Pink500))
            Box(Modifier.weight(20f).fillMaxHeight().background(Emerald500))
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SplitLabel("🏠", "50 %", "Necesidades")
            SplitLabel("🎉", "30 %", "Caprichos")
            SplitLabel("🐷", "20 %", "Ahorro")
        }
    }
    Spacer(Modifier.height(16.dp))

    OnboardingCard {
        Text("¿Qué día cobras?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        FilterChip(
            selected = assistant.paydayDay == null,
            onClick = { onAssistantChange { it.copy(paydayDay = null) } },
            label = { Text("No tengo nómina", fontWeight = FontWeight.SemiBold) },
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.18f), selectedLabelColor = accent)
        )
        Spacer(Modifier.height(8.dp))
        // Los días del mes en una cuadrícula, como un calendario
        (1..31).chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    val selected = assistant.paydayDay == day
                    Surface(
                        onClick = { onAssistantChange { it.copy(paydayDay = day) } },
                        shape = CircleShape,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .semantics {
                                contentDescription = "Día $day"
                                this.selected = selected
                                role = Role.RadioButton
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("$day", style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                // La última fila (29–31) se rellena para que los círculos midan lo mismo
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(
            assistant.paydayDay?.let { day ->
                val end = if (day == 1) "último día" else "${day - 1}"
                "Cobras el día $day: tus meses irán del $day al $end del siguiente${if (day > 28) " (o el último día si el mes es más corto)" else ""}."
            } ?: "Sin nómina, tus meses irán del día 1 al último.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(16.dp))

    OnboardingCard {
        ToggleRow(
            emoji = "📊",
            title = "Ver mis ahorros en Inicio",
            subtitle = "El balance del mes en tu pantalla principal",
            checked = assistant.homeSavingsEnabled,
            accent = accent,
            onChange = { on -> onAssistantChange { it.copy(homeSavingsEnabled = on) } }
        )
    }
}

@Composable
private fun SplitLabel(emoji: String, percent: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$emoji $percent", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// endregion

// region Asistente

@Composable
fun AssistantStep(assistant: AssistantSettings, onAssistantChange: ((AssistantSettings) -> AssistantSettings) -> Unit) {
    val accent = OnboardingStep.ASSISTANT.accent
    StepTitle(
        title = "Tu asistente personal",
        subtitle = "Elige en qué te echo una mano. Viene todo encendido; apaga lo que no quieras."
    )
    OnboardingCard {
        ToggleRow("⭐", "Prioridades del día", "Cada mañana te pregunto por tus 3 tareas clave", assistant.focusEnabled, accent) { on ->
            onAssistantChange { it.copy(focusEnabled = on) }
        }
        ToggleRow("🧹", "Repaso de pendientes", "Qué hacer con lo que se quedó sin hacer", assistant.leftoversEnabled, accent) { on ->
            onAssistantChange { it.copy(leftoversEnabled = on) }
        }
        ToggleRow("🎯", "Avisos de presupuesto", "Te aviso al llegar al 80 % de un presupuesto", assistant.budgetAlertsEnabled, accent) { on ->
            onAssistantChange { it.copy(budgetAlertsEnabled = on) }
        }
        ToggleRow("💬", "Frase del día", "Una frase para arrancar el día en Inicio", assistant.dailyQuoteEnabled, accent) { on ->
            onAssistantChange { it.copy(dailyQuoteEnabled = on) }
        }
    }
}

// endregion

// region Aspecto

private data class ThemeOption(val mode: ThemeMode, val emoji: String, val label: String)

private val THEME_OPTIONS = listOf(
    ThemeOption(ThemeMode.LIGHT, "☀️", "Claro"),
    ThemeOption(ThemeMode.DARK, "🌙", "Oscuro"),
    ThemeOption(ThemeMode.SYSTEM, "🌓", "Automático")
)

@Composable
fun LookStep(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    val accent = OnboardingStep.LOOK.accent
    StepTitle(
        title = "¿Claro u oscuro?",
        subtitle = "Elige cómo quieres ver Syncro. Se aplica al momento 👀"
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        THEME_OPTIONS.forEach { option ->
            val selected = option.mode == themeMode
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    onClick = { onThemeChange(option.mode) },
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.62f)
                        .semantics {
                            contentDescription = "Tema ${option.label}"
                            this.selected = selected
                            role = Role.RadioButton
                        }
                ) {
                    Box {
                        ThemeMock(option.mode)
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("${option.emoji} ${option.label}", style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

/** Un móvil en miniatura con los colores de cada tema (el automático, mitad y mitad). */
@Composable
private fun ThemeMock(mode: ThemeMode) {
    @Composable
    fun Screen(background: Color, card: Color, line: Color, modifier: Modifier) {
        Column(modifier = modifier.background(background).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Cyan400))
            repeat(3) {
                Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)).background(card)) {
                    Box(Modifier.padding(6.dp).fillMaxWidth(0.7f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(line))
                }
            }
        }
    }
    val light = Triple(Slate50, Color.White, Slate200)
    val dark = Triple(Slate900, Slate800, Slate500)
    when (mode) {
        ThemeMode.LIGHT -> Screen(light.first, light.second, light.third, Modifier.fillMaxSize())
        ThemeMode.DARK -> Screen(dark.first, dark.second, dark.third, Modifier.fillMaxSize())
        ThemeMode.SYSTEM -> Row(Modifier.fillMaxSize()) {
            Screen(light.first, light.second, light.third, Modifier.weight(1f).fillMaxHeight())
            Screen(dark.first, dark.second, dark.third, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

// endregion

// region Listo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoneStep(state: OnboardingUiState, notificationsAllowed: Boolean) {
    val digest = state.settings.digest
    val assistant = state.settings.assistant
    StepTitle(
        title = state.firstName?.let { "¡Todo listo, $it!" } ?: "¡Todo listo!",
        subtitle = "Así ha quedado. Lo puedes cambiar cuando quieras en Ajustes ⚙️"
    )

    val helpers = listOf(assistant.focusEnabled, assistant.leftoversEnabled, assistant.budgetAlertsEnabled, assistant.dailyQuoteEnabled).count { it }
    val summary = buildList {
        add(if (notificationsAllowed) "🔔 Notificaciones activadas" else "🔕 Sin notificaciones")
        val times = listOfNotNull(
            digest.morningTime.format(TIME).takeIf { digest.morningEnabled }?.let { "☀️ $it" },
            digest.eveningTime.format(TIME).takeIf { digest.eveningEnabled }?.let { "🌙 $it" }
        )
        add(if (times.isEmpty()) "📭 Sin resúmenes diarios" else times.joinToString(" · "))
        add(assistant.paydayDay?.let { "💶 Nómina el día $it" } ?: "💶 Sin nómina")
        if (assistant.homeSavingsEnabled) add("📊 Ahorros en Inicio")
        add(if (helpers == 0) "🤖 Asistente en silencio" else "🤖 $helpers ${if (helpers == 1) "ayuda" else "ayudas"} del asistente")
        add(
            when (state.themeMode) {
                ThemeMode.LIGHT -> "☀️ Tema claro"
                ThemeMode.DARK -> "🌙 Tema oscuro"
                ThemeMode.SYSTEM -> "🌓 Tema automático"
            }
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        summary.forEach { item ->
            Surface(
                shape = RoundedCornerShape(50),
                color = OnboardingStep.DONE.accent.copy(alpha = 0.12f)
            ) {
                Text(item, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    OnboardingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge("💡", Amber500)
            Spacer(Modifier.width(14.dp))
            Text(
                "Truco: con el botón + de Inicio creas tareas, eventos y notas en un momento.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// endregion
