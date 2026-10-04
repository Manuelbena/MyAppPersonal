package com.syncro.domain.usecase

import com.syncro.domain.model.LeftoverTasks
import com.syncro.domain.model.leftoverTasks
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
 * Tareas que se quedaron sin hacer en el repaso de ahora (ver [leftoverTasks]), con la hora de la
 * noche que haya elegido el usuario. La hora actual se toma al llamar: quien lo muestre debe volver
 * a llamarlo al volver a la app, por si ya ha llegado la hora de la noche.
 */
class GetLeftoverTasksUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock
) {
    operator fun invoke(): Flow<LeftoverTasks> {
        val now = LocalDateTime.now(clock)
        val eveningFrom = settingsRepository.settings.map { it.digest.eveningTime }.distinctUntilChanged()
        return combine(repository.getUnfinishedTasksUntil(LocalDate.now(clock)), eveningFrom) { unfinished, evening ->
            // Las que se repiten no se proponen pasar a otro día: ya viene la siguiente repetición
            leftoverTasks(unfinished.filter { it.seriesId == null }, now, evening)
        }
    }
}
