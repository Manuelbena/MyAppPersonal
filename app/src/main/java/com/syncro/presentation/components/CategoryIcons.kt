package com.syncro.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Icono de una categoría de evento; el mismo en el formulario, la tarjeta y el detalle. */
fun categoryIcon(category: String): ImageVector = when (category.lowercase().trim()) {
    "personal" -> Icons.Rounded.Person
    "trabajo" -> Icons.Rounded.Work
    "salud" -> Icons.Rounded.Favorite
    "ocio" -> Icons.Rounded.SportsEsports
    "deporte" -> Icons.Rounded.FitnessCenter
    "compras" -> Icons.Rounded.ShoppingCart
    "recados" -> Icons.Rounded.Checklist
    "otro" -> Icons.Rounded.MoreHoriz
    "google calendar" -> Icons.Rounded.CalendarMonth
    else -> Icons.AutoMirrored.Rounded.Label
}
