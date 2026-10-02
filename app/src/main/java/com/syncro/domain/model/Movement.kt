package com.syncro.domain.model

import java.time.LocalDate
import java.time.YearMonth

/*
 * Ahorros: ingresos y gastos. Solo euros. Los importes se guardan en céntimos enteros (Long): con
 * decimales (Double) 0,10 + 0,20 no da 0,30 y los totales acaban descuadrando.
 */

enum class MovementType { INCOME, EXPENSE }

/** Categorías de cada tipo; la última de cada uno ("Otros") recoge lo que no encaja en las demás. */
enum class MovementCategory(val type: MovementType) {
    // Gastos
    HOUSING(MovementType.EXPENSE),
    BILLS(MovementType.EXPENSE),
    GROCERIES(MovementType.EXPENSE),
    TRANSPORT(MovementType.EXPENSE),
    RESTAURANTS(MovementType.EXPENSE),
    LEISURE(MovementType.EXPENSE),
    SUBSCRIPTIONS(MovementType.EXPENSE),
    HEALTH(MovementType.EXPENSE),
    SHOPPING(MovementType.EXPENSE),
    EDUCATION(MovementType.EXPENSE),
    OTHER_EXPENSE(MovementType.EXPENSE),

    // Ingresos
    SALARY(MovementType.INCOME),
    EXTRA_WORK(MovementType.INCOME),
    SALES(MovementType.INCOME),
    REFUNDS(MovementType.INCOME),
    GIFTS(MovementType.INCOME),
    INVESTMENTS(MovementType.INCOME),
    OTHER_INCOME(MovementType.INCOME);

    companion object {
        fun of(type: MovementType): List<MovementCategory> = entries.filter { it.type == type }

        /** "Otros" del tipo: el destino de lo que no encaja (o de una categoría que ya no existe). */
        fun other(type: MovementType): MovementCategory =
            if (type == MovementType.EXPENSE) OTHER_EXPENSE else OTHER_INCOME
    }
}

/**
 * Un ingreso o un gasto. Si [repeatsMonthly], es un movimiento fijo (nómina, alquiler, Netflix):
 * cuenta todos los meses desde [date], el mismo día del mes. No se copia mes a mes: se calcula al
 * mirar cada mes ([dateIn]), así que borrarlo lo quita de todos.
 *
 * @param amountCents importe en céntimos, siempre positivo (el signo lo da [type])
 */
data class Movement(
    val id: String,
    val type: MovementType,
    val amountCents: Long,
    val category: MovementCategory,
    val date: LocalDate,
    val note: String?,
    val repeatsMonthly: Boolean
)

/**
 * Día en que el movimiento cuenta en [month], o null si ese mes no cuenta. Uno mensual cae el
 * mismo día que el original o, si el mes es más corto, el último (un cargo del 31 cae el 30 de
 * abril y el 28 de febrero), como hacen los bancos con los recibos.
 */
fun Movement.dateIn(month: YearMonth): LocalDate? {
    val start = YearMonth.from(date)
    return when {
        !repeatsMonthly -> date.takeIf { start == month }
        month.isBefore(start) -> null
        else -> month.atDay(minOf(date.dayOfMonth, month.lengthOfMonth()))
    }
}

/** Un movimiento en un mes concreto (los mensuales aparecen una vez en cada mes). */
data class MovementOccurrence(val movement: Movement, val date: LocalDate)

/** Lo que pasa con el dinero en un mes: los movimientos (más recientes primero) y los totales. */
data class MonthMovements(val month: YearMonth, val occurrences: List<MovementOccurrence>) {
    val incomeCents: Long = occurrences.filter { it.movement.type == MovementType.INCOME }.sumOf { it.movement.amountCents }
    val expenseCents: Long = occurrences.filter { it.movement.type == MovementType.EXPENSE }.sumOf { it.movement.amountCents }
    val balanceCents: Long get() = incomeCents - expenseCents

    /**
     * Tasa de ahorro: qué parte de lo que entra se queda (balance / ingresos), en %. Es el
     * indicador que mira un asesor (un 20 % es un buen objetivo). Null sin ingresos: no se puede
     * calcular. Negativa si se gasta más de lo que entra.
     */
    val savingsRatePercent: Int?
        get() = if (incomeCents <= 0) null else (balanceCents * 100 / incomeCents).toInt()
}

fun monthMovements(month: YearMonth, movements: List<Movement>): MonthMovements = MonthMovements(
    month = month,
    occurrences = movements
        .mapNotNull { movement -> movement.dateIn(month)?.let { MovementOccurrence(movement, it) } }
        .sortedByDescending { it.date }
)

/** Importe máximo de un movimiento: mil millones de euros (más es casi seguro un error al teclear). */
const val MAX_AMOUNT_CENTS = 100_000_000_000L

private val AMOUNT = Regex("""^\d{1,10}([.,]\d{1,2})?$""")

/**
 * Convierte lo que se teclea ("12", "12,5", "12.50") en céntimos. Coma o punto como decimal, sin
 * separador de miles, como mucho dos decimales. Null si no es un importe válido mayor que 0.
 */
fun parseEuroCents(text: String): Long? {
    val clean = text.trim().removeSuffix("€").trim()
    if (!AMOUNT.matches(clean)) return null
    val parts = clean.replace(',', '.').split('.')
    val euros = parts[0].toLong()
    val cents = parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0L
    val total = euros * 100 + cents
    return total.takeIf { it in 1..MAX_AMOUNT_CENTS }
}

class InvalidAmountException :
    IllegalArgumentException("Introduce un importe mayor que 0")
