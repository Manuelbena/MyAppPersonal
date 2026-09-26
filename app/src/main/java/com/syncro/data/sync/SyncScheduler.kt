package com.syncro.data.sync

/** Programa la subida de los cambios pendientes para cuando haya red. */
interface SyncScheduler {
    fun schedulePendingPush()
}
