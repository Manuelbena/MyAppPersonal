package com.syncro.domain.usecase

import com.syncro.domain.model.SyncState
import com.syncro.domain.repository.ConnectivityRepository
import com.syncro.domain.repository.GoogleSyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** La conexión, los cambios sin subir y los permisos de Google, para avisar en la UI de lo que no está sincronizado. */
class ObserveSyncStateUseCase @Inject constructor(
    private val connectivity: ConnectivityRepository,
    private val googleSync: GoogleSyncRepository
) {
    operator fun invoke(): Flow<SyncState> =
        combine(
            connectivity.isOnline,
            googleSync.observePendingChangesCount(),
            googleSync.observeMissingAccess()
        ) { online, pending, missing ->
            SyncState(isOnline = online, pendingChanges = pending, missingAccess = missing)
        }.distinctUntilChanged()
}
