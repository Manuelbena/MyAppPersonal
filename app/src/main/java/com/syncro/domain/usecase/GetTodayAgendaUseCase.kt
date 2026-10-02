package com.syncro.domain.usecase

import com.syncro.domain.model.TodayAgenda
import com.syncro.domain.model.todayAgenda
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** Lo que te queda de hoy ahora mismo (ver [todayAgenda]). Lee solo el móvil: funciona sin conexión. */
class GetTodayAgendaUseCase @Inject constructor(
    private val getTimeline: GetTimelineUseCase,
    private val clock: Clock
) {
    suspend operator fun invoke(): TodayAgenda =
        todayAgenda(getTimeline(LocalDate.now(clock)).first(), LocalDateTime.now(clock))
}
