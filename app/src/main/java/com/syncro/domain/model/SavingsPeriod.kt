package com.syncro.domain.model

import java.time.LocalDate
import java.time.YearMonth

/*
 * El "mes" de Ahorros. Quien cobra el 27 vive del 27 de un mes al 26 del siguiente: el dinero de
 * octubre empieza con la nómina del 27 de septiembre. Sin día de nómina es el mes natural.
 */

/** De [start] a [end], ambos incluidos: de una nómina al día antes de la siguiente. */
data class SavingsPeriod(val start: LocalDate, val end: LocalDate) {
    /** El mes en que empieza: el que le da nombre si es el mes natural, y su clave para los avisos. */
    val month: YearMonth get() = YearMonth.from(start)

    /** Del 1 al último día de un mes (sin nómina, o cobrando el día 1). */
    val isCalendarMonth: Boolean get() = start.dayOfMonth == 1 && end == month.atEndOfMonth()

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    companion object {
        /** El mes natural, del 1 al último día. */
        fun of(month: YearMonth) = SavingsPeriod(month.atDay(1), month.atEndOfMonth())
    }
}

/**
 * El periodo en que cae [date] cobrando el día [paydayDay] (null = mes natural). Empieza en la
 * última nómina hasta [date] y acaba el día antes de la siguiente; en meses más cortos la nómina
 * cae el último día (cobrando el 31: del 28 de febrero al 30 de marzo).
 */
fun savingsPeriodOf(date: LocalDate, paydayDay: Int?): SavingsPeriod {
    val day = paydayDay ?: 1
    val start = lastPayday(day, date)
    val end = paydayIn(YearMonth.from(start).plusMonths(1), day).minusDays(1)
    return SavingsPeriod(start, end)
}
