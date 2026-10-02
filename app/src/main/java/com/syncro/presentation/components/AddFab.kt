package com.syncro.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * El "+" de las pantallas principales (Inicio, Calendario, Ahorros): abre la hoja con lo que se
 * puede crear ahí. Va levantado para flotar sobre el degradado y la barra de navegación.
 */
@Composable
fun AddFab(onClick: () -> Unit, contentDescription: String = "Añadir") {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .padding(bottom = 110.dp)
            .size(56.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = contentDescription,
            modifier = Modifier.size(32.dp)
        )
    }
}
