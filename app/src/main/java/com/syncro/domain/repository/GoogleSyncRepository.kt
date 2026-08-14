package com.syncro.domain.repository

import com.syncro.domain.model.SyncroItem

interface GoogleSyncRepository {
    suspend fun syncTasks(): Result<Unit>
    suspend fun syncCalendar(): Result<Unit>
}
