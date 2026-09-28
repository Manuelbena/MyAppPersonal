package com.syncro.presentation.navigation


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.syncro.presentation.legal.LegalDocumentId

// Usamos una sealed class para representar nuestras rutas de forma segura
sealed class AppScreen(val route: String, val title: String, val icon: ImageVector) {
    object Home : AppScreen("home", "Inicio", Icons.Outlined.Home)
    object Calendar : AppScreen("calendar", "Calendario", Icons.Outlined.DateRange)
    object Savings : AppScreen("savings", "Ahorros", Icons.Outlined.Savings)
    object Assistant : AppScreen("assistant", "Asistente", Icons.Outlined.AutoAwesome)
    object Login : AppScreen("login", "Login", Icons.Outlined.Person)
    object NotesList : AppScreen("notes_list", "Mis Notas", Icons.Outlined.Home)
    object Settings : AppScreen("settings", "Ajustes", Icons.Outlined.Settings)
    /** Un documento legal; se abre con [legalRoute]. */
    object Legal : AppScreen("legal/{doc}", "Legal", Icons.Outlined.Settings)
}

// Lista que usaremos para pintar el menú
val bottomNavItems = listOf(
    AppScreen.Home,
    AppScreen.Calendar,
    AppScreen.Savings,
    AppScreen.Assistant
)
fun legalRoute(document: LegalDocumentId) = "legal/${document.name}"
