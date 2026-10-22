package com.syncro.di

import com.syncro.data.update.PlayAppUpdateRepository
import com.syncro.domain.repository.AppUpdateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Release: la versión nueva la dice Google Play. (La debug tiene su propio módulo, con una de mentira para probar.) */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateModule {
    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(impl: PlayAppUpdateRepository): AppUpdateRepository
}
