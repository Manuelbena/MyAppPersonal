package com.syncro.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.syncro.data.local.dao.BudgetDao
import com.syncro.data.local.dao.EventDao
import com.syncro.data.local.dao.MovementDao
import com.syncro.data.local.dao.NoteDao
import com.syncro.data.local.dao.RepeatSeriesDao
import com.syncro.data.local.dao.TaskDao
import com.syncro.data.local.dao.UserDao
import com.syncro.data.local.entity.BudgetEntity
import com.syncro.data.local.entity.EventEntity
import com.syncro.data.local.entity.MovementEntity
import com.syncro.data.local.entity.NoteEntity
import com.syncro.data.local.entity.RepeatSeriesEntity
import com.syncro.data.local.entity.SubtaskEntity
import com.syncro.data.local.entity.TaskEntity
import com.syncro.data.local.entity.UserEntity

@Database(
    entities = [
        TaskEntity::class, UserEntity::class, EventEntity::class, SubtaskEntity::class, NoteEntity::class,
        MovementEntity::class, BudgetEntity::class, RepeatSeriesEntity::class
    ],
    version = 17,
    exportSchema = false
)
abstract class SyncroDatabase : RoomDatabase() {
    abstract val taskDao: TaskDao
    abstract val userDao: UserDao
    abstract val eventDao: EventDao
    abstract val noteDao: NoteDao
    abstract val movementDao: MovementDao
    abstract val budgetDao: BudgetDao
    abstract val repeatSeriesDao: RepeatSeriesDao
}
