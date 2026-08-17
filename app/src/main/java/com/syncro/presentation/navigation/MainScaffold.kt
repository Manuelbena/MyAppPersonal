package com.syncro.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.syncro.presentation.AuthViewModel
import com.syncro.presentation.assistant.AssistantScreen
import com.syncro.presentation.calendar.CalendarScreen
import com.syncro.presentation.event.AddEventScreen
import com.syncro.presentation.home.HomeScreen
import com.syncro.presentation.login.LoginScreen
import com.syncro.presentation.theme.SyncroTheme
import com.syncro.presentation.theme.ThemeViewModel

@Composable
fun MainScaffold(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val isDarkTheme by themeViewModel.isDarkTheme.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val showBottomBar = currentRoute != AppScreen.AddEvent.route && currentRoute != AppScreen.Login.route

    SyncroTheme(darkTheme = isDarkTheme) {
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
        ) { paddingValues ->
            // Ingnoramos paddingValues para que el contenido sea inmersivo
            NavHost(
                navController = navController,
                startDestination = if (currentUser == null) AppScreen.Login.route else AppScreen.Home.route,
                modifier = Modifier.fillMaxSize()
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
                         onNavigateToAddEvent = { /* Ya no navegamos, se gestiona internamente */ }
                     )
                }
                composable(AppScreen.Calendar.route) {
                     CalendarScreen()
                }
                composable(AppScreen.Savings.route) {
                     //SavingsScreen()
                }
                composable(AppScreen.Assistant.route) {
                     AssistantScreen()
                }
                // Quitamos la ruta separada de AddEvent para evitar el pantallazo blanco
            }
        }
    }
}
