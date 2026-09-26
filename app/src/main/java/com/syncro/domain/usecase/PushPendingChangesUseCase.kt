package com.syncro.domain.usecase

import com.syncro.domain.repository.GoogleSyncRepository
import javax.inject.Inject

/** Sube a Google todos los cambios hechos sin conexión, de cualquier fecha. */
class PushPendingChangesUseCase @Inject constructor(
    private val repository: GoogleSyncRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.pushPendingChanges()
}
