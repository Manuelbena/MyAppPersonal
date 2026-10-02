package com.syncro.domain.usecase

import com.syncro.domain.model.DailyDigest
import com.syncro.domain.model.DigestMoment
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.model.paydayIn
import com.syncro.domain.repository.DailyFocusRepository
import com.syncro.domain.repository.SettingsRepository
import com.syncro.domain.repository.UserRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Reúne lo que necesita el aviso diario: lo de hoy, lo de mañana y el nombre del usuario. Lee solo
 * la base de datos local, así que funciona sin conexión. Null si no hay sesión: no hay a quién avisar.
 */
class GetDailyDigestUseCase @Inject constructor(
    private val getTimeline: GetTimelineUseCase,
    private val userRepository: UserRepository,
    private val focusRepository: DailyFocusRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(moment: DigestMoment): DailyDigest? {
        val user = userRepository.getUser().first() ?: return null
        val today = LocalDate.now(clock)
        val todayItems = getTimeline(today).first()
        val tomorrowItems = getTimeline(today.plusDays(1)).first()
        val assistant = settingsRepository.settings.first().assistant

        return DailyDigest(
            moment = moment,
            date = today,
            firstName = user.name.trim().substringBefore(' '),
            tasks = todayItems.filterIsInstance<SyncroItem.Task>(),
            events = todayItems.filterIsInstance<SyncroItem.Event>(),
            tomorrowTasks = tomorrowItems.filterIsInstance<SyncroItem.Task>(),
            tomorrowEvents = tomorrowItems.filterIsInstance<SyncroItem.Event>(),
            offerFocus = assistant.focusEnabled && focusRepository.getFocus(today).first() == null,
            offerLeftovers = assistant.leftoversEnabled,
            isPayday = assistant.paydayDay?.let { paydayIn(YearMonth.from(today), it) == today } == true
        )
    }
}
