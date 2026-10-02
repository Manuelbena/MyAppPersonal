package com.syncro.domain.usecase

import com.syncro.domain.model.AppSettings
import com.syncro.domain.model.InvalidDigestTimesException
import com.syncro.domain.model.InvalidPaydayException
import com.syncro.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Cambia los ajustes partiendo de los actuales. Falla sin guardar con
 * [InvalidDigestTimesException] si el aviso de la mañana no queda antes que el de la noche: el de
 * la noche marca el final del día y lo de la mañana dejaría de tener sentido. También falla con
 * [InvalidPaydayException] si el día de la nómina no es del 1 al 31.
 */
class UpdateSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(change: (AppSettings) -> AppSettings): Result<Unit> {
        val updated = change(repository.settings.first())
        if (!updated.digest.morningTime.isBefore(updated.digest.eveningTime)) {
            return Result.failure(InvalidDigestTimesException())
        }
        if (updated.assistant.paydayDay?.let { it !in 1..31 } == true) {
            return Result.failure(InvalidPaydayException())
        }
        repository.save(updated)
        return Result.success(Unit)
    }
}
