package com.syncro.presentation.navigation

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContentTransitionScope
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.syncro.presentation.AuthViewModel
import com.syncro.presentation.SessionState
import com.syncro.presentation.assistant.AssistantMainScreen
import com.syncro.presentation.calendar.CalendarScreen
import com.syncro.presentation.components.ComingSoonScreen
import com.syncro.presentation.event.AddEventScreen
import com.syncro.presentation.home.HomeScreen
import com.syncro.presentation.login.LoginScreen
import com.syncro.presentation.notes.NotesListScreen
import com.syncro.presentation.theme.SyncroTheme
import com.syncro.presentation.theme.ThemeViewModel

// Diseño a pantalla completa: la barra inferior flota sobre el contenido, así que el padding del
// Scaffold se ignora a propósito (cada pantalla deja su propio margen inferior)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScaffold(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val isDarkTheme by themeViewModel.isDarkTheme.collectAsState()
    val session by authViewModel.session.collectAsState()
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val showBottomBar = currentRoute != AppScreen.Login.route && 
                       currentRoute != AppScreen.NotesList.route

    SyncroTheme(darkTheme = isDarkTheme) {
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

            Scaffold(
                bottomBar = {
                    if (showBottomBar) {
                        FloatingBottomNav(
                            items = bottomNavItems,
                            currentRoute = currentRoute,
                            onItemClick = { screen ->
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
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

                        if (initialIndex != -1 && targetIndex != -1 && initialIndex != targetIndex) {
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

                        if (initialIndex != -1 && targetIndex != -1 && initialIndex != targetIndex) {
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
                composable(AppScreen.Login.route) {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(AppScreen.Home.route) {
                                popUpTo(AppScreen.Login.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(AppScreen.Home.route) {
                     HomeScreen(
                         isDarkTheme = isDarkTheme,
                         onThemeToggle = { themeViewModel.toggleTheme() },
                         onNavigateToNotes = { navController.navigate(AppScreen.NotesList.route) }
                     )
                }
                composable(AppScreen.NotesList.route) {
                    NotesListScreen(
                        onBack = { navController.popBackStack() }
                    )
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
                     AssistantMainScreen()
                }
                }
            }
        }
    }
}
