package com.syncro.presentation.navigation

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.syncro.presentation.AuthViewModel
import com.syncro.presentation.SessionState
import com.syncro.presentation.assistant.AssistantMainScreen
import com.syncro.presentation.assistant.AssistantViewModel
import com.syncro.presentation.assistant.TrackAssistantOnResume
import com.syncro.presentation.calendar.CalendarScreen
import com.syncro.presentation.components.ComingSoonScreen
import com.syncro.presentation.components.ProvideWidthClass
import com.syncro.presentation.components.ReadableWidth
import com.syncro.presentation.event.AddEventScreen
import com.syncro.presentation.home.HomeScreen
import com.syncro.presentation.login.LoginScreen
import com.syncro.presentation.notes.NotesListScreen
import com.syncro.presentation.settings.SettingsScreen
import com.syncro.presentation.legal.LegalDocumentId
import com.syncro.presentation.legal.LegalDocumentScreen
import com.syncro.data.preferences.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import com.syncro.presentation.theme.SyncroTheme
import com.syncro.presentation.theme.ThemeViewModel

/** Pantallas que se abren "encima" de otra (entran por la derecha) y ocultan la barra inferior. */
private val PAGE_ROUTES = setOf(AppScreen.NotesList.route, AppScreen.Settings.route, AppScreen.Legal.route)

/** Ritmo de las transiciones de página (libreta) y de la barra inferior, que van a la vez. */
private fun <T> pushSpec() = tween<T>(durationMillis = 400, easing = FastOutSlowInEasing)

// Diseño a pantalla completa: la barra inferior flota sobre el contenido, así que el padding del
// Scaffold se ignora a propósito (cada pantalla deja su propio margen inferior)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScaffold(
    // La notificación de pendientes pide abrir el chat del asistente
    openAssistant: Boolean = false,
    onAssistantOpened: () -> Unit = {},
    // El "+" del widget pide abrir la hoja de nueva tarea en Inicio
    addTask: Boolean = false,
    onAddTaskOpened: () -> Unit = {},
    themeViewModel: ThemeViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    // Uno solo para el chat y el número de la barra, así nunca se desincronizan
    assistantViewModel: AssistantViewModel = hiltViewModel()
) {
    val themeMode by themeViewModel.themeMode.collectAsState()
    val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val session by authViewModel.session.collectAsState()
    TrackAssistantOnResume(assistantViewModel)
    val assistantUnread = assistantViewModel.uiState.collectAsState().value?.unreadCount ?: 0
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val showBottomBar = currentRoute != AppScreen.Login.route &&
                       currentRoute != AppScreen.NotesList.route &&
                       currentRoute != AppScreen.Settings.route &&
                       currentRoute != AppScreen.Legal.route
    // Mientras la barra se oculta con animación, sigue marcando la sección de la que se viene
    var lastBottomRoute by remember { mutableStateOf(currentRoute) }
    if (bottomNavItems.any { it.route == currentRoute }) lastBottomRoute = currentRoute

    SyncroTheme(darkTheme = isDarkTheme) {
        ProvideWidthClass {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Mientras se lee la sesión no se muestra nada: evita el parpadeo del login
                if (session == SessionState.Loading) return@Box
                // Se decide una sola vez; después, login -> inicio lo gestiona la propia navegación
                val startDestination = remember {
                    if (session is SessionState.LoggedIn) AppScreen.Home.route else AppScreen.Login.route
                }
                val navigateToTab: (AppScreen) -> Unit = { screen ->
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                // Tras montar el NavHost (los efectos van después de la composición); sin sesión no hay chat
                // Al cerrar sesión (desde Ajustes) se vuelve al login y no se puede volver atrás
                LaunchedEffect(session) {
                    if (session == SessionState.LoggedOut && navController.currentDestination?.route != AppScreen.Login.route) {
                        navController.navigate(AppScreen.Login.route) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                }
                LaunchedEffect(addTask, session) {
                    if (addTask && session is SessionState.LoggedIn && navController.currentDestination?.route != AppScreen.Home.route) {
                        navigateToTab(AppScreen.Home)
                    }
                }
                LaunchedEffect(openAssistant, session) {
                    if (openAssistant && session is SessionState.LoggedIn) {
                        navigateToTab(AppScreen.Assistant)
                        onAssistantOpened()
                    }
                }

                Scaffold(
                    bottomBar = {
                        // Entra y sale deslizándose al ritmo de la pantalla, en vez de aparecer de golpe
                        AnimatedVisibility(
                            visible = showBottomBar,
                            enter = slideInVertically(pushSpec()) { it } + fadeIn(pushSpec()),
                            exit = slideOutVertically(pushSpec()) { it } + fadeOut(pushSpec())
                        ) {
                            // En tablet no se estira de lado a lado: queda centrada con el ancho de un móvil
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                FloatingBottomNav(
                                    items = bottomNavItems,
                                    currentRoute = lastBottomRoute,
                                    badges = mapOf(AppScreen.Assistant.route to assistantUnread),
                                    onItemClick = navigateToTab,
                                    modifier = Modifier.widthIn(max = 560.dp)
                                )
                            }
                        }
                    },
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { _ ->
                    // Sin padding a propósito: el contenido va a pantalla completa y la barra flota encima
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            val initialRoute = initialState.destination.route
                            val targetRoute = targetState.destination.route

                            val initialIndex = bottomNavItems.indexOfFirst { it.route == initialRoute }
                            val targetIndex = bottomNavItems.indexOfFirst { it.route == targetRoute }

                            if (initialRoute in PAGE_ROUTES) {
                                // Volviendo de la libreta: Inicio regresa desde la izquierda
                                slideInHorizontally(pushSpec()) { -it / 4 } + fadeIn(pushSpec())
                            } else if (initialIndex != -1 && targetIndex != -1 && initialIndex != targetIndex) {
                                if (targetIndex > initialIndex) {
                                    slideInHorizontally(
                                        initialOffsetX = { it / 3 },
                                        animationSpec = tween(900, easing = EaseOutQuart)
                                    ) + fadeIn(animationSpec = tween(800, easing = EaseInOutQuart))
                                } else {
                                    slideInHorizontally(
                                        initialOffsetX = { -it / 3 },
                                        animationSpec = tween(900, easing = EaseOutQuart)
                                    ) + fadeIn(animationSpec = tween(800, easing = EaseInOutQuart))
                                }
                            } else {
                                fadeIn(animationSpec = tween(900, easing = EaseInOutQuart)) + 
                                scaleIn(initialScale = 0.95f, animationSpec = tween(900, easing = EaseOutQuart))
                            }
                        },
                        exitTransition = {
                            val initialRoute = initialState.destination.route
                            val targetRoute = targetState.destination.route
                            
                            val initialIndex = bottomNavItems.indexOfFirst { it.route == initialRoute }
                            val targetIndex = bottomNavItems.indexOfFirst { it.route == targetRoute }

                            if (targetRoute in PAGE_ROUTES) {
                                // Abriendo la libreta: Inicio se aparta un poco hacia la izquierda
                                slideOutHorizontally(pushSpec()) { -it / 4 } + fadeOut(pushSpec())
                            } else if (initialIndex != -1 && targetIndex != -1 && initialIndex != targetIndex) {
                                if (targetIndex > initialIndex) {
                                    slideOutHorizontally(
                                        targetOffsetX = { -it / 3 },
                                        animationSpec = tween(900, easing = EaseOutQuart)
                                    ) + fadeOut(animationSpec = tween(700, easing = EaseInOutQuart))
                                } else {
                                    slideOutHorizontally(
                                        targetOffsetX = { it / 3 },
                                        animationSpec = tween(900, easing = EaseOutQuart)
                                    ) + fadeOut(animationSpec = tween(700, easing = EaseInOutQuart))
                                }
                            } else {
                                fadeOut(animationSpec = tween(700, easing = EaseInOutQuart))
                            }
                        }
                    ) {
                    // Login, ajustes, textos legales y chat son de lectura: en tablet van centrados
                    composable(AppScreen.Login.route) {
                        ReadableWidth {
                            LoginScreen(
                                onOpenLegal = { navController.navigate(legalRoute(it)) },
                                onLoginSuccess = {
                                    navController.navigate(AppScreen.Home.route) {
                                        popUpTo(AppScreen.Login.route) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                    composable(AppScreen.Home.route) {
                         HomeScreen(
                             onOpenSettings = { navController.navigate(AppScreen.Settings.route) },
                             openQuickTask = addTask && session is SessionState.LoggedIn,
                             onQuickTaskOpened = onAddTaskOpened,
                             onNavigateToNotes = { navController.navigate(AppScreen.NotesList.route) }
                         )
                    }
                    // La libreta es una página "encima" de Inicio: entra por la derecha y sale por ella
                    composable(
                        AppScreen.NotesList.route,
                        enterTransition = { slideInHorizontally(pushSpec()) { it } },
                        popExitTransition = { slideOutHorizontally(pushSpec()) { it } }
                    ) {
                        NotesListScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    // Ajustes, igual que la libreta: una página encima de Inicio
                    composable(
                        AppScreen.Settings.route,
                        enterTransition = { slideInHorizontally(pushSpec()) { it } },
                        popExitTransition = { slideOutHorizontally(pushSpec()) { it } }
                    ) {
                        ReadableWidth {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenLegal = { navController.navigate(legalRoute(it)) }
                            )
                        }
                    }
                    // Documentos legales: desde Ajustes y desde el inicio de sesión (sin sesión)
                    composable(
                        AppScreen.Legal.route,
                        enterTransition = { slideInHorizontally(pushSpec()) { it } },
                        popExitTransition = { slideOutHorizontally(pushSpec()) { it } }
                    ) { entry ->
                        val document = entry.arguments?.getString("doc")
                            ?.let { name -> LegalDocumentId.entries.firstOrNull { it.name == name } }
                            ?: LegalDocumentId.PRIVACY
                        ReadableWidth {
                            LegalDocumentScreen(documentId = document, onBack = { navController.popBackStack() })
                        }
                    }
                    composable(AppScreen.Calendar.route) {
                         CalendarScreen()
                    }
                    composable(AppScreen.Savings.route) {
                        ComingSoonScreen(
                            icon = AppScreen.Savings.icon,
                            title = "Ahorros",
                            description = "Controla tus objetivos de ahorro junto a tus tareas y eventos."
                        )
                    }
                    composable(AppScreen.Assistant.route) {
                        ReadableWidth {
                            AssistantMainScreen(viewModel = assistantViewModel)
                        }
                    }
                    }
                }
            }
        }
    }
}
