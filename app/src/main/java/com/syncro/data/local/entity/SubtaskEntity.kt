package com.syncro.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["eventId"])]
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val eventId: Int,
    val title: String,
    val isCompleted: Boolean
)
