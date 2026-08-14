package com.syncro.domain.usecase

import android.util.Log
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

class GetTimelineUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val eventRepository: EventRepository
) {
    operator fun invoke(date: LocalDate): Flow<List<SyncroItem>> {
        Log.d("GetTimelineUseCase", "Fetching timeline for date: $date (epoch: ${date.toEpochDay()})")
        return combine(
            taskRepository.getTasksByDate(date),
            eventRepository.getEventsByDate(date)
        ) { tasks, events ->
            Log.d("GetTimelineUseCase", "Found ${tasks.size} tasks and ${events.size} events in DB")
            (tasks + events).sortedBy { item ->
                when (item) {
                    is SyncroItem.Task -> item.time
                    is SyncroItem.Event -> item.startTime
                }
            }
        }
    }
}
