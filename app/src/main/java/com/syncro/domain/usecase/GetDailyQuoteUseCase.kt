package com.syncro.domain.usecase

import com.syncro.domain.model.Quote
import com.syncro.domain.model.QuotesProvider
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Frase del día: cambia cada día y es la misma durante todo el día. */
class GetDailyQuoteUseCase @Inject constructor(
    private val clock: Clock
) {
    operator fun invoke(quotes: List<Quote> = QuotesProvider.quotes): Quote {
        require(quotes.isNotEmpty()) { "No hay frases" }
        return quotes[LocalDate.now(clock).dayOfYear % quotes.size]
    }
}
