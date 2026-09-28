package com.syncro.domain.usecase

import com.syncro.domain.model.AppSettings
import com.syncro.domain.model.InvalidDigestTimesException
import com.syncro.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Cambia los ajustes partiendo de los actuales. Falla sin guardar con
 * [InvalidDigestTimesException] si el aviso de la mañana no queda antes que el de la noche: el de
 * la noche marca el final del día y lo de la mañana dejaría de tener sentido.
 */
class UpdateSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(change: (AppSettings) -> AppSettings): Result<Unit> {
        val updated = change(repository.settings.first())
        if (!updated.digest.morningTime.isBefore(updated.digest.eveningTime)) {
            return Result.failure(InvalidDigestTimesException())
        }
        repository.save(updated)
        return Result.success(Unit)
    }
}
