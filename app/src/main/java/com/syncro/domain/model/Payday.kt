package com.syncro.domain.model

import java.time.LocalDate
import java.time.YearMonth

/*
 * Día de nómina: el día que se cobra, el asistente ayuda a organizar el mes. Se pregunta si se
 * apunta la nómina en Ahorros y, con ella apuntada, se propone el reparto 50/30/20.
 */

/** Lo que contestó el usuario el día de nómina. */
enum class PaydayAnswer {
    /** "Apuntar nómina": se abre Ahorros con el ingreso preparado. */
    REGISTER,
    /** "Ahora no". */
    LATER
}

/** Día en que cae la nómina en [month]: [day] o, si el mes es más corto, el último (31 → 30 de abril). */
fun paydayIn(month: YearMonth, day: Int): LocalDate = month.atDay(minOf(day, month.lengthOfMonth()))

/** La última nómina hasta [today] incluido: la de este mes si ya llegó, si no la del anterior. */
fun lastPayday(day: Int, today: LocalDate): LocalDate {
    val thisMonth = paydayIn(YearMonth.from(today), day)
    return if (thisMonth.isAfter(today)) paydayIn(YearMonth.from(today).minusMonths(1), day) else thisMonth
}

/**
 * Un día de nómina en el chat.
 *
 * @param salaryCents la nómina apuntada en Ahorros ese mes (ingresos de "Nómina"), o null si aún no
 * @param answer lo que contestó a "¿Apuntamos la nómina?", o null si aún nada
 */
data class Payday(val date: LocalDate, val salaryCents: Long?, val answer: PaydayAnswer?)

/**
 * Reparto 50/30/20: la mitad para lo necesario (casa, facturas, comida), un 30 % para caprichos y
 * un 20 % para ahorrar. Las tres partes suman exactamente la nómina (el redondeo va a lo necesario).
 */
data class SalaryPlan(val needsCents: Long, val wantsCents: Long, val savingsCents: Long)

fun salaryPlan(salaryCents: Long): SalaryPlan {
    val savings = salaryCents * 20 / 100
    val wants = salaryCents * 30 / 100
    return SalaryPlan(needsCents = salaryCents - wants - savings, wantsCents = wants, savingsCents = savings)
}

/** Lo cobrado de nómina en el mes: los ingresos de la categoría "Nómina". Null si no hay ninguno. */
fun MonthMovements.salaryCents(): Long? = occurrences
    .filter { it.movement.type == MovementType.INCOME && it.movement.category == MovementCategory.SALARY }
    .takeIf { it.isNotEmpty() }
    ?.sumOf { it.movement.amountCents }
