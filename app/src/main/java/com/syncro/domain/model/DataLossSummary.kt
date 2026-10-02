package com.syncro.domain.model

/**
 * Lo que se perdería al cerrar sesión: los cambios de tareas y eventos que aún no han llegado a
 * Google y lo que solo existe en el móvil (no se sincroniza): las notas y los ingresos y gastos.
 */
data class DataLossSummary(val unsyncedChanges: Int, val notes: Int, val movements: Int = 0) {
    val losesSomething: Boolean get() = unsyncedChanges > 0 || notes > 0 || movements > 0
}
