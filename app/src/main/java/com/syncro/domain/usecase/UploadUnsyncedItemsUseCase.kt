package com.syncro.domain.usecase

import com.syncro.domain.repository.GoogleSyncRepository
import java.time.LocalDate
import javax.inject.Inject

class UploadUnsyncedItemsUseCase @Inject constructor(
    private val repository: GoogleSyncRepository
) {
    suspend operator fun invoke(date: LocalDate): Result<Unit> {
        return repository.uploadUnsyncedItems(date)
    }
}
