package com.syncro.domain.model

/**
 * Lo que se perdería al cerrar sesión: los cambios de tareas y eventos que aún no han llegado a
 * Google y las notas, que solo existen en el móvil (no se sincronizan).
 */
data class DataLossSummary(val unsyncedChanges: Int, val notes: Int) {
    val losesSomething: Boolean get() = unsyncedChanges > 0 || notes > 0
}
