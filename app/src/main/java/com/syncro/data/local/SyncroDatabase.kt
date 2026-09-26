package com.syncro.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.syncro.data.local.dao.EventDao
import com.syncro.data.local.dao.NoteDao
import com.syncro.data.local.dao.TaskDao
import com.syncro.data.local.dao.UserDao
import com.syncro.data.local.entity.EventEntity
import com.syncro.data.local.entity.NoteEntity
import com.syncro.data.local.entity.SubtaskEntity
import com.syncro.data.local.entity.TaskEntity
import com.syncro.data.local.entity.UserEntity

@Database(
    entities = [TaskEntity::class, UserEntity::class, EventEntity::class, SubtaskEntity::class, NoteEntity::class], 
    version = 8,
    exportSchema = false
)
abstract class SyncroDatabase : RoomDatabase() {
    abstract val taskDao: TaskDao
    abstract val userDao: UserDao
    abstract val eventDao: EventDao
    abstract val noteDao: NoteDao
}
