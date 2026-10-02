package com.syncro.di

import android.content.Context
import androidx.room.Room
import com.syncro.data.local.MIGRATION_7_8
import com.syncro.data.local.MIGRATION_8_9
import com.syncro.data.local.MIGRATION_9_10
import com.syncro.data.local.MIGRATION_10_11
import com.syncro.data.local.MIGRATION_11_12
import com.syncro.data.local.MIGRATION_12_13
import com.syncro.data.local.MIGRATION_13_14
import com.syncro.data.local.MIGRATION_14_15
import com.syncro.data.local.SyncroDatabase
import com.syncro.data.local.dao.BudgetDao
import com.syncro.data.local.dao.EventDao
import com.syncro.data.local.dao.MovementDao
import com.syncro.data.local.dao.NoteDao
import com.syncro.data.local.dao.TaskDao
import com.syncro.data.local.dao.UserDao
import com.syncro.data.repository.AccountDataRepositoryImpl
import com.syncro.data.repository.BudgetRepositoryImpl
import com.syncro.data.repository.DailyFocusRepositoryImpl
import com.syncro.data.repository.DailyQuoteRepositoryImpl
import com.syncro.data.repository.SettingsRepositoryImpl
import com.syncro.data.repository.EventRepositoryImpl
import com.syncro.data.repository.GoogleSyncRepositoryImpl
import com.syncro.data.repository.MovementRepositoryImpl
import com.syncro.data.repository.NoteRepositoryImpl
import com.syncro.data.repository.TaskRepositoryImpl
import com.syncro.data.repository.UserRepositoryImpl
import com.syncro.data.remote.GoogleApiRemoteDataSource
import com.syncro.data.remote.GoogleRemoteDataSource
import com.syncro.data.sync.SyncScheduler
import com.syncro.data.sync.WorkManagerSyncScheduler
import com.syncro.domain.repository.AccountDataRepository
import com.syncro.domain.repository.BudgetRepository
import com.syncro.domain.repository.DailyFocusRepository
import com.syncro.domain.repository.DailyQuoteRepository
import com.syncro.domain.repository.SettingsRepository
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.MovementRepository
import com.syncro.domain.repository.NoteRepository
import com.syncro.domain.repository.TaskRepository
import com.syncro.domain.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
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
            .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
            .fallbackToDestructiveMigration(true)
            .build()
    }

    // Hora actual inyectable: los casos de uso que dependen de "ahora" se prueban con un reloj fijo
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

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
    fun provideMovementDao(db: SyncroDatabase): MovementDao {
        return db.movementDao
    }

    @Provides
    @Singleton
    fun provideMovementRepository(dao: MovementDao): MovementRepository {
        return MovementRepositoryImpl(dao)
    }

    @Provides
    fun provideBudgetDao(db: SyncroDatabase): BudgetDao {
        return db.budgetDao
    }

    @Provides
    @Singleton
    fun provideBudgetRepository(dao: BudgetDao): BudgetRepository {
        return BudgetRepositoryImpl(dao)
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
    fun provideDailyFocusRepository(impl: DailyFocusRepositoryImpl): DailyFocusRepository = impl

    @Provides
    @Singleton
    fun provideDailyQuoteRepository(impl: DailyQuoteRepositoryImpl): DailyQuoteRepository = impl

    @Provides
    @Singleton
    fun provideSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository = impl

    @Provides
    @Singleton
    fun provideAccountDataRepository(impl: AccountDataRepositoryImpl): AccountDataRepository = impl

    @Provides
    @Singleton
    fun provideGoogleRemoteDataSource(@ApplicationContext context: Context, userDao: UserDao): GoogleRemoteDataSource {
        return GoogleApiRemoteDataSource(context, userDao)
    }

    @Provides
    @Singleton
    fun provideSyncScheduler(@ApplicationContext context: Context): SyncScheduler {
        return WorkManagerSyncScheduler(context)
    }

    @Provides
    @Singleton
    fun provideGoogleSyncRepository(
        remote: GoogleRemoteDataSource,
        taskDao: TaskDao,
        eventDao: EventDao,
        syncScheduler: SyncScheduler,
        clock: Clock
    ): GoogleSyncRepository {
        return GoogleSyncRepositoryImpl(remote, taskDao, eventDao, syncScheduler, clock)
    }
}
