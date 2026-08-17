package com.syncro.domain.usecase

import androidx.compose.ui.graphics.Color
import com.syncro.domain.model.Priority
import com.syncro.domain.model.Subtask
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import java.time.LocalDate
import javax.inject.Inject

class SaveEventUseCase @Inject constructor(
    private val repository: EventRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    suspend operator fun invoke(
        title: String,
        description: String?,
        location: String?,
        date: LocalDate,
        startTime: String,
        endTime: String,
        categoryText: String,
        categoryColor: Color,
        priority: Priority?,
        subtasks: List<String>
    ) {
        val event = SyncroItem.Event(
            id = "0", // Generated in repository
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            categoryText = categoryText,
            categoryColor = categoryColor,
            priority = priority,
            subtasks = subtasks.map { Subtask(it, false) },
            isCompleted = false
        )
        repository.insertEvent(event, date, location)

        // Sincronizar con Google Calendar
        googleSyncRepository.uploadEventToGoogle(
            title = title,
            description = description,
            location = location,
            startDate = date,
            startTime = startTime,
            endTime = endTime,
            category = categoryText
        )
    }
}
