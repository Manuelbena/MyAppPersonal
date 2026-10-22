package com.syncro.domain.usecase

import com.syncro.domain.model.AppUpdate
import com.syncro.domain.repository.AppUpdateRepository
import javax.inject.Inject

/** Si hay una versión nueva de Syncro en la tienda (el asistente la anuncia). */
class CheckAppUpdateUseCase @Inject constructor(
    private val repository: AppUpdateRepository
) {
    suspend operator fun invoke(): AppUpdate? = repository.availableUpdate()
}
