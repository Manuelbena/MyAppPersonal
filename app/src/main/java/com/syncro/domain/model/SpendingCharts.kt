package com.syncro.domain.model

import java.time.LocalDate

/*
 * Los datos de las gráficas del desglose de Ahorros: cómo se acumula el gasto día a día y en qué
 * categorías se va. Solo cuentas: la forma de dibujarlas está en la presentación.
 */

/** Lo gastado desde el primer día del mes hasta [date], incluido. */
data class CumulativeSpend(val date: LocalDate, val cents: Long)

/**
 * Lo gastado acumulado en cada día del periodo, del primero al último (también los días sin gastos,
 * para que la línea sea continua). Cuenta también lo previsto (gastos que se repiten y aún no han
 * llegado): ya está comprometido. Los ingresos no cuentan.
 */
fun MonthMovements.cumulativeSpending(): List<CumulativeSpend> {
    val byDay = occurrences
        .filter { it.movement.type == MovementType.EXPENSE }
        .groupBy({ it.date }, { it.movement.amountCents })
        .mapValues { (_, amounts) -> amounts.sum() }
    var total = 0L
    return generateSequence(period.start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(period.end) }
        .map { day ->
            total += byDay[day] ?: 0L
            CumulativeSpend(day, total)
        }
        .toList()
}

/**
 * Lo previsto para gastar en el mes según el 50/30/20: lo que les toca a Necesidades y Caprichos
 * juntos (el 80 % de la base). Null si este mes no ha entrado dinero (no hay base).
 */
val BudgetPlan.spendingLimitCents: Long?
    get() = groups.mapNotNull { it.targetCents }.takeIf { it.size == groups.size }?.sum()

/** Una barra de "¿En qué se va?": una categoría, o las demás juntas ([category] null = "Otros"). */
data class CategoryShare(val category: MovementCategory?, val cents: Long, val percent: Int)

/**
 * Las [top] categorías con más gasto y, si quedan más, las demás juntas en "Otros" al final (la
 * categoría "Otros gastos" va siempre ahí, aunque sea grande: no dice en qué se fue). Así la lista
 * no crece sin fin con categorías de pocos euros. Los % son sobre el total del mes.
 */
fun MonthMovements.expenseShares(top: Int = TOP_EXPENSE_CATEGORIES): List<CategoryShare> {
    val totals = totalsByCategory(MovementType.EXPENSE)
    val total = totals.sumOf { it.cents }
    if (total == 0L) return emptyList()
    val named = totals.filter { it.category != MovementCategory.OTHER_EXPENSE }
    val shown = named.take(top)
    val rest = totals.filter { it !in shown }.sumOf { it.cents }
    fun percent(cents: Long) = (cents * 100 / total).toInt()
    return shown.map { CategoryShare(it.category, it.cents, percent(it.cents)) } +
        listOfNotNull(rest.takeIf { it > 0 }?.let { CategoryShare(null, it, percent(it)) })
}

const val TOP_EXPENSE_CATEGORIES = 5
