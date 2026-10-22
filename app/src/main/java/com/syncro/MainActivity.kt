package com.syncro

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syncro.data.notifications.DigestNotifier
import com.syncro.domain.model.MovementType
import com.syncro.presentation.navigation.MainScaffold
import com.syncro.presentation.theme.SyncroTheme


import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // La notificación de pendientes pide abrir el chat del asistente; se consume al navegar
    private var openAssistant by mutableStateOf(false)
    // El "+" del widget pide abrir la hoja de nueva tarea; se consume al abrirla
    private var addTask by mutableStateOf(false)
    // El widget de Ahorros pide abrir Ahorros, o su formulario de ingreso o gasto; se consume al navegar
    private var openSavings by mutableStateOf(false)
    private var addMovement by mutableStateOf<MovementType?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Solo en el primer arranque: al girar la pantalla el intent sigue siendo el mismo
        if (savedInstanceState == null) handleIntent(intent)

        // Esto permite que tu app pinte detrás de la barra de estado y navegación del móvil,
        // dándole ese toque inmersivo y de "pantalla completa" súper moderno.
        enableEdgeToEdge()

        setContent {
            SyncroTheme {
                // ¡Adiós al código de ejemplo, hola a nuestra arquitectura limpia!
                // Llamamos directamente a nuestro componente principal que ya contiene
                // el Scaffold con el menú flotante y el NavHost.
                MainScaffold(
                    openAssistant = openAssistant,
                    onAssistantOpened = { openAssistant = false },
                    addTask = addTask,
                    onAddTaskOpened = { addTask = false },
                    openSavings = openSavings,
                    addMovement = addMovement,
                    onSavingsOpened = {
                        openSavings = false
                        addMovement = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    companion object {
        /** Extra del intent: abrir la hoja de nueva tarea (lo usa el widget). */
        const val EXTRA_ADD_TASK = "add_task"

        /** Extra del intent: abrir Ahorros (lo usa el widget de Ahorros). */
        const val EXTRA_OPEN_SAVINGS = "open_savings"

        /** Extra del intent: abrir el formulario de un movimiento; el valor es el [MovementType]. */
        const val EXTRA_ADD_MOVEMENT = "add_movement"
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(DigestNotifier.EXTRA_OPEN_ASSISTANT, false) == true) openAssistant = true
        if (intent?.getBooleanExtra(EXTRA_ADD_TASK, false) == true) addTask = true
        if (intent?.getBooleanExtra(EXTRA_OPEN_SAVINGS, false) == true) openSavings = true
        intent?.getStringExtra(EXTRA_ADD_MOVEMENT)
            ?.let { name -> MovementType.entries.firstOrNull { it.name == name } }
            ?.let { addMovement = it }
    }
}
