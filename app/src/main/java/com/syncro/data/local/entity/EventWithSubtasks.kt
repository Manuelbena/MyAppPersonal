package com.syncro.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class EventWithSubtasks(
    @Embedded val event: EventEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "eventId"
    )
    val subtasks: List<SubtaskEntity>
)
