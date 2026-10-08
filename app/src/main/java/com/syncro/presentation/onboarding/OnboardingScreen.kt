package com.syncro.presentation.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.syncro.data.preferences.ThemeMode
import com.syncro.domain.model.AssistantSettings
import com.syncro.domain.model.DigestSettings
import com.syncro.presentation.components.MascotVideo
import com.syncro.presentation.components.canAskNotificationPermission
import com.syncro.presentation.components.markNotificationPermissionAsked
import com.syncro.presentation.components.notificationsAllowed
import com.syncro.presentation.components.openNotificationSettings
import com.syncro.presentation.theme.*

/*
 * La guía de inicio: después del primer inicio de sesión, unos pasos cortos para dejar la app a
 * gusto (avisos, nómina, asistente y tema). Todo se guarda al tocarlo, todo se puede cambiar luego
 * en Ajustes y la guía se puede saltar en cualquier momento.
 */

/** Los pasos, con su emoji grande, los que flotan alrededor y su color. */
enum class OnboardingStep(val emoji: String, val floating: List<String>, val accent: Color) {
    WELCOME("👋", listOf("📅", "💶", "✨"), Cyan400),
    NOTIFICATIONS("🔔", listOf("☀️", "🌙", "⏰"), Amber500),
    MONEY("💶", listOf("🐷", "📈", "🎯"), Emerald500),
    ASSISTANT("🤖", listOf("⭐", "💬", "🧹"), Violet500),
    LOOK("🎨", listOf("☀️", "🌙", "✨"), Indigo500),
    DONE("🎉", listOf("🎊", "🥳", "✨"), Pink500);

    val previous: OnboardingStep? get() = entries.getOrNull(ordinal - 1)
    val next: OnboardingStep? get() = entries.getOrNull(ordinal + 1)
}

/** Los pasos que configuran algo (sin la bienvenida ni el final): los de la barra de progreso. */
private val CONFIG_STEPS = OnboardingStep.entries.filter { it != OnboardingStep.WELCOME && it != OnboardingStep.DONE }

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    // Lo decide Android: se mira al entrar y al volver (p. ej. de los ajustes del sistema)
    var notificationsAllowed by remember { mutableStateOf(context.notificationsAllowed()) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = context.notificationsAllowed()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = context.notificationsAllowed()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    OnboardingContent(
        state = state,
        notificationsAllowed = notificationsAllowed,
        onEnableNotifications = {
            // El diálogo de Android mientras aún lo enseñe; si no, sus ajustes (como en Ajustes y el chat)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.canAskNotificationPermission()) {
                context.markNotificationPermissionAsked()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openNotificationSettings()
            }
        },
        onDigestChange = viewModel::updateDigest,
        onAssistantChange = viewModel::updateAssistant,
        onThemeChange = viewModel::setTheme,
        onFinish = {
            viewModel.finish()
            onFinished()
        },
        snackbarHostState = snackbarHostState
    )
}

/**
 * La guía, sin ViewModel (para las pruebas). [showMascot] enseña la mascota animada en la
 * bienvenida y el final; en las pruebas va un emoji, que no necesita reproductor de vídeo.
 */
@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    notificationsAllowed: Boolean,
    onEnableNotifications: () -> Unit,
    onDigestChange: ((DigestSettings) -> DigestSettings) -> Unit,
    onAssistantChange: ((AssistantSettings) -> AssistantSettings) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onFinish: () -> Unit,
    showMascot: Boolean = true,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    // El atrás del sistema vuelve al paso anterior; en la bienvenida, sale como siempre
    BackHandler(enabled = step.previous != null && step != OnboardingStep.DONE) { step.previous?.let { step = it } }

    val accent by animateColorAsState(step.accent, tween(600), label = "onboardingAccent")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)))
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                OnboardingTopBar(
                    step = step,
                    accent = accent,
                    onBack = { step.previous?.let { step = it } },
                    onSkip = onFinish
                )

                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        (slideInHorizontally(tween(450)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(450)))
                            .togetherWith(slideOutHorizontally(tween(350)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(250)))
                    },
                    modifier = Modifier.weight(1f),
                    label = "onboardingStep"
                ) { current ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(8.dp))
                        StepHero(current, showMascot = showMascot && (current == OnboardingStep.WELCOME || current == OnboardingStep.DONE))
                        Spacer(Modifier.height(20.dp))
                        StepBody(
                            step = current,
                            state = state,
                            notificationsAllowed = notificationsAllowed,
                            onEnableNotifications = onEnableNotifications,
                            onDigestChange = onDigestChange,
                            onAssistantChange = onAssistantChange,
                            onThemeChange = onThemeChange
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp, top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { step.next?.let { step = it } ?: onFinish() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
                    ) {
                        Text(
                            when (step) {
                                OnboardingStep.WELCOME -> "Empezar ✨"
                                OnboardingStep.DONE -> "Ir a mi día 🚀"
                                else -> "Siguiente"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (step == OnboardingStep.WELCOME) {
                        Text(
                            "Te llevará 1 minuto. Todo se puede cambiar luego en Ajustes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Atrás, la barra de pasos ("Paso 2 de 4") y "Saltar". En la bienvenida y el final no hay barra. */
@Composable
private fun OnboardingTopBar(step: OnboardingStep, accent: Color, onBack: () -> Unit, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (step != OnboardingStep.WELCOME && step != OnboardingStep.DONE) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Paso anterior")
                }
            }
        }
        val position = CONFIG_STEPS.indexOf(step)
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { if (position >= 0) contentDescription = "Paso ${position + 1} de ${CONFIG_STEPS.size}" },
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (position >= 0) {
                CONFIG_STEPS.forEachIndexed { index, _ ->
                    val done = index <= position
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (done) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    )
                }
            }
        }
        Box(modifier = Modifier.widthIn(min = 72.dp), contentAlignment = Alignment.CenterEnd) {
            if (step != OnboardingStep.DONE) {
                TextButton(onClick = onSkip) {
                    Text("Saltar", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * El dibujo de cada paso: un círculo con su emoji (o la mascota animada) que late despacio, y tres
 * emojis pequeños flotando alrededor.
 */
@Composable
private fun StepHero(step: OnboardingStep, showMascot: Boolean) {
    // Grande en la bienvenida y el final; en los pasos de ajustes, más pequeño para que quepa lo que se elige
    val big = step == OnboardingStep.WELCOME || step == OnboardingStep.DONE
    val transition = rememberInfiniteTransition(label = "hero")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val float by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float"
    )

    Box(modifier = Modifier.size(width = if (big) 240.dp else 200.dp, height = if (big) 190.dp else 120.dp), contentAlignment = Alignment.Center) {
        // Halo de color detrás
        Box(
            modifier = Modifier
                .size(if (big) 176.dp else 112.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(step.accent.copy(alpha = 0.35f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(if (big) 136.dp else 88.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, step.accent.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (showMascot) {
                MascotVideo(modifier = Modifier.size(120.dp).clip(CircleShape))
            } else {
                Text(step.emoji, style = if (big) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displaySmall)
            }
        }
        // Los pequeños, en tres esquinas, subiendo y bajando a destiempo
        val positions = listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomEnd)
        step.floating.zip(positions).forEachIndexed { index, (emoji, alignment) ->
            Text(
                emoji,
                style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(alignment)
                    .padding(8.dp)
                    .graphicsLayer { translationY = if (index % 2 == 0) float * density else -float * density }
            )
        }
    }
}

@Composable
private fun StepBody(
    step: OnboardingStep,
    state: OnboardingUiState,
    notificationsAllowed: Boolean,
    onEnableNotifications: () -> Unit,
    onDigestChange: ((DigestSettings) -> DigestSettings) -> Unit,
    onAssistantChange: ((AssistantSettings) -> AssistantSettings) -> Unit,
    onThemeChange: (ThemeMode) -> Unit
) {
    when (step) {
        OnboardingStep.WELCOME -> WelcomeStep(state.firstName)
        OnboardingStep.NOTIFICATIONS -> NotificationsStep(state.settings.digest, notificationsAllowed, onEnableNotifications, onDigestChange)
        OnboardingStep.MONEY -> MoneyStep(state.settings.assistant, onAssistantChange)
        OnboardingStep.ASSISTANT -> AssistantStep(state.settings.assistant, onAssistantChange)
        OnboardingStep.LOOK -> LookStep(state.themeMode, onThemeChange)
        OnboardingStep.DONE -> DoneStep(state, notificationsAllowed)
    }
}
