package com.syncro.domain.model

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

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
    val repeatsMonthly: Boolean,
    /** La cuenta de ahorro en la que está (ver [SavingsAccount]). */
    val accountId: String = MAIN_ACCOUNT_ID
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

/**
 * Días en que el movimiento cuenta dentro de [period]. El periodo puede abarcar dos meses (del 27
 * al 26), así que uno mensual se busca en los dos y se queda el que cae dentro.
 */
fun Movement.datesIn(period: SavingsPeriod): List<LocalDate> =
    listOf(period.month, YearMonth.from(period.end))
        .distinct()
        .mapNotNull { dateIn(it) }
        .filter { it in period }

/** Un movimiento en un mes concreto (los mensuales aparecen una vez en cada mes). */
data class MovementOccurrence(val movement: Movement, val date: LocalDate)

/**
 * Lo que pasa con el dinero en un mes ([period]: el natural o de nómina a nómina): los movimientos
 * (más recientes primero) y los totales.
 */
data class MonthMovements(val period: SavingsPeriod, val occurrences: List<MovementOccurrence>) {
    val month: YearMonth get() = period.month
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

/** Lo que suma una categoría en el mes y qué parte es del total de su tipo (en %, redondeado). */
data class CategoryTotal(val category: MovementCategory, val cents: Long, val percent: Int)

/**
 * Desglose del mes por categoría para un tipo (gastos o ingresos), de mayor a menor. Los % se
 * redondean, así que pueden no sumar exactamente 100.
 */
fun MonthMovements.totalsByCategory(type: MovementType): List<CategoryTotal> {
    val ofType = occurrences.filter { it.movement.type == type }
    val total = ofType.sumOf { it.movement.amountCents }
    if (total == 0L) return emptyList()
    return ofType
        .groupBy { it.movement.category }
        .map { (category, items) ->
            val cents = items.sumOf { it.movement.amountCents }
            CategoryTotal(category, cents, ((cents * 100.0) / total).roundToInt())
        }
        .sortedByDescending { it.cents }
}

fun monthMovements(period: SavingsPeriod, movements: List<Movement>): MonthMovements = MonthMovements(
    period = period,
    occurrences = movements
        .flatMap { movement -> movement.datesIn(period).map { MovementOccurrence(movement, it) } }
        .sortedByDescending { it.date }
)

/** El mes natural (sin día de nómina). */
fun monthMovements(month: YearMonth, movements: List<Movement>): MonthMovements =
    monthMovements(SavingsPeriod.of(month), movements)

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

/**
 * "1.234,56 €": siempre dos decimales, como un extracto, con espacio no separable antes del € para
 * que no se parta en dos líneas. Exacto (BigDecimal), sin pasar por decimales.
 */
fun euros(cents: Long): String =
    java.text.NumberFormat.getCurrencyInstance(java.util.Locale("es", "ES")).apply {
        currency = java.util.Currency.getInstance("EUR")
    }.format(java.math.BigDecimal.valueOf(cents, 2))
