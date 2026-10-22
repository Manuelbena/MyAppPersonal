package com.syncro.di

import com.syncro.debug.DebugAppUpdateRepository
import com.syncro.domain.repository.AppUpdateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Debug: Google Play, salvo que se haya fingido una versión nueva con FakeUpdateReceiver. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateModule {
    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(impl: DebugAppUpdateRepository): AppUpdateRepository
}
