package com.syncro.di

import android.content.Context
import androidx.room.Room
import com.syncro.data.local.SyncroDatabase
import com.syncro.data.local.dao.EventDao
import com.syncro.data.local.dao.NoteDao
import com.syncro.data.local.dao.TaskDao
import com.syncro.data.local.dao.UserDao
import com.syncro.data.repository.EventRepositoryImpl
import com.syncro.data.repository.GoogleSyncRepositoryImpl
import com.syncro.data.repository.NoteRepositoryImpl
import com.syncro.data.repository.TaskRepositoryImpl
import com.syncro.data.repository.UserRepositoryImpl
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.NoteRepository
import com.syncro.domain.repository.TaskRepository
import com.syncro.domain.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSyncroDatabase(@ApplicationContext context: Context): SyncroDatabase {
        return Room.databaseBuilder(
            context,
            SyncroDatabase::class.java,
            "syncro_db"
        )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    fun provideTaskDao(db: SyncroDatabase): TaskDao {
        return db.taskDao
    }

    @Provides
    fun provideUserDao(db: SyncroDatabase): UserDao {
        return db.userDao
    }

    @Provides
    fun provideEventDao(db: SyncroDatabase): EventDao {
        return db.eventDao
    }

    @Provides
    fun provideNoteDao(db: SyncroDatabase): NoteDao {
        return db.noteDao
    }

    @Provides
    @Singleton
    fun provideTaskRepository(dao: TaskDao): TaskRepository {
        return TaskRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideNoteRepository(dao: NoteDao): NoteRepository {
        return NoteRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideUserRepository(dao: UserDao): UserRepository {
        return UserRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideEventRepository(dao: EventDao): EventRepository {
        return EventRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideGoogleSyncRepository(
        @ApplicationContext context: Context,
        userDao: UserDao,
        taskDao: TaskDao,
        eventDao: EventDao
    ): com.syncro.domain.repository.GoogleSyncRepository {
        return com.syncro.data.repository.GoogleSyncRepositoryImpl(context, userDao, taskDao, eventDao)
    }
}
