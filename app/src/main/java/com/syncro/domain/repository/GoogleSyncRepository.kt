package com.syncro.domain.repository

import java.time.LocalDate

interface GoogleSyncRepository {
    suspend fun syncTasks(date: LocalDate): Result<Unit>
    suspend fun syncCalendar(date: LocalDate): Result<Unit>
    suspend fun uploadUnsyncedItems(date: LocalDate): Result<Unit>
    suspend fun uploadTaskToGoogle(title: String, notes: String?, date: LocalDate): Result<Unit>
    suspend fun uploadEventToGoogle(
        title: String, 
        description: String?, 
        location: String?, 
        startDate: LocalDate, 
        startTime: String, 
        endTime: String,
        category: String? = null,
        subtasks: List<String> = emptyList()
    ): Result<Unit>
}
