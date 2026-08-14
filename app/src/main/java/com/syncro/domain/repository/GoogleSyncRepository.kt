package com.syncro.domain.repository

import java.time.LocalDate

interface GoogleSyncRepository {
    suspend fun syncTasks(date: LocalDate): Result<Unit>
    suspend fun syncCalendar(date: LocalDate): Result<Unit>
}
