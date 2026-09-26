package com.syncro.presentation.navigation


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.ui.graphics.vector.ImageVector

// Usamos una sealed class para representar nuestras rutas de forma segura
sealed class AppScreen(val route: String, val title: String, val icon: ImageVector) {
    object Home : AppScreen("home", "Inicio", Icons.Outlined.Home)
    object Calendar : AppScreen("calendar", "Calendario", Icons.Outlined.DateRange)
    object Savings : AppScreen("savings", "Ahorros", Icons.Outlined.Savings)
    object Assistant : AppScreen("assistant", "Asistente", Icons.Outlined.AutoAwesome)
    object Login : AppScreen("login", "Login", Icons.Outlined.Person)
    object NotesList : AppScreen("notes_list", "Mis Notas", Icons.Outlined.Home)
}

// Lista que usaremos para pintar el menú
val bottomNavItems = listOf(
    AppScreen.Home,
    AppScreen.Calendar,
    AppScreen.Savings,
    AppScreen.Assistant
)