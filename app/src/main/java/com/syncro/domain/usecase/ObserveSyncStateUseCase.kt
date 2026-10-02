package com.syncro.domain.usecase

import com.syncro.domain.model.SyncState
import com.syncro.domain.repository.ConnectivityRepository
import com.syncro.domain.repository.GoogleSyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** La conexión y los cambios sin subir a Google, para avisar en la UI de lo que aún no está sincronizado. */
class ObserveSyncStateUseCase @Inject constructor(
    private val connectivity: ConnectivityRepository,
    private val googleSync: GoogleSyncRepository
) {
    operator fun invoke(): Flow<SyncState> =
        combine(connectivity.isOnline, googleSync.observePendingChangesCount()) { online, pending ->
            SyncState(isOnline = online, pendingChanges = pending)
        }.distinctUntilChanged()
}
