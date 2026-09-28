package com.syncro.domain.usecase

import com.syncro.domain.model.FocusCandidates
import com.syncro.domain.model.focusCandidates
import com.syncro.domain.repository.SettingsRepository
import com.syncro.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Tareas de hoy entre las que elegir las prioridades (ver [focusCandidates]); null desde la hora de
 * la noche que haya elegido el usuario. La hora actual se toma al llamar: quien lo muestre debe
 * volver a llamarlo al volver a la app.
 */
class GetFocusCandidatesUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock
) {
    operator fun invoke(): Flow<FocusCandidates?> {
        val now = LocalDateTime.now(clock)
        val eveningFrom = settingsRepository.settings.map { it.digest.eveningTime }.distinctUntilChanged()
        return combine(repository.getTasksByDate(LocalDate.now(clock)), eveningFrom) { tasks, evening ->
            focusCandidates(tasks, now, evening)
        }
    }
}
