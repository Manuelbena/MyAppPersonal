package com.syncro.domain.model

import java.time.LocalDate

/*
 * Presupuestos: un límite de gasto al mes por categoría ("Supermercado: 300 €"). Vale para todos
 * los meses; cada mes se compara con lo gastado en esa categoría.
 */

/** Límite mensual de gasto de una categoría (solo de gastos). */
data class Budget(val category: MovementCategory, val limitCents: Long)

/** A partir de qué parte del límite se avisa: con un 20 % de margen aún se puede frenar. */
const val BUDGET_WARNING_PERCENT = 80

enum class BudgetLevel {
    /** Por debajo del 80 %. */
    OK,
    /** Del 80 % al 100 %: ojo, queda poco. */
    WARNING,
    /** Pasado del límite. */
    EXCEEDED
}

/** Cómo va un presupuesto en un mes. */
data class BudgetStatus(val budget: Budget, val spentCents: Long) {
    val percent: Int get() = (spentCents * 100 / budget.limitCents).toInt()
    /** Lo que queda hasta el límite; negativo si se ha pasado. */
    val remainingCents: Long get() = budget.limitCents - spentCents
    val level: BudgetLevel
        get() = when {
            spentCents > budget.limitCents -> BudgetLevel.EXCEEDED
            spentCents * 100 >= budget.limitCents * BUDGET_WARNING_PERCENT -> BudgetLevel.WARNING
            else -> BudgetLevel.OK
        }
}

/**
 * Cómo va cada presupuesto en el mes, los más apurados primero. Cuenta todos los gastos del mes de
 * esa categoría, también los previstos (un recibo a fin de mes ya está comprometido), igual que
 * los totales del mes.
 */
fun MonthMovements.budgetStatuses(budgets: List<Budget>): List<BudgetStatus> = budgets
    .map { budget ->
        BudgetStatus(
            budget,
            occurrences
                .filter { it.movement.type == MovementType.EXPENSE && it.movement.category == budget.category }
                .sumOf { it.movement.amountCents }
        )
    }
    .sortedWith(compareByDescending<BudgetStatus> { it.spentCents * 1000 / it.budget.limitCents }.thenBy { it.budget.category.ordinal })

/**
 * Un aviso del asistente: el día en que lo gastado en una categoría llegó al 80 % del límite
 * ([BudgetLevel.WARNING]) o lo pasó ([BudgetLevel.EXCEEDED]), y cuánto se llevaba ese día.
 */
data class BudgetAlert(val budget: Budget, val level: BudgetLevel, val date: LocalDate, val spentCents: Long)

/**
 * Los avisos de presupuesto del mes hasta [today]: se recorren los gastos de cada categoría por
 * fecha y se anota el primer día en que se cruzó cada umbral. Los gastos previstos (después de
 * [today]) no avisan: aún no han pasado.
 */
fun MonthMovements.budgetAlerts(budgets: List<Budget>, today: LocalDate): List<BudgetAlert> = budgets.flatMap { budget ->
    val expenses = occurrences
        .filter { it.movement.type == MovementType.EXPENSE && it.movement.category == budget.category && !it.date.isAfter(today) }
        .sortedBy { it.date }
    var spent = 0L
    var warned = false
    buildList {
        for (occurrence in expenses) {
            spent += occurrence.movement.amountCents
            val level = BudgetStatus(budget, spent).level
            if (level == BudgetLevel.EXCEEDED) {
                // Si un solo gasto lo pasa de golpe, basta con este aviso (no dos el mismo día)
                add(BudgetAlert(budget, BudgetLevel.EXCEEDED, occurrence.date, spent))
                break
            }
            if (!warned && level == BudgetLevel.WARNING) {
                add(BudgetAlert(budget, BudgetLevel.WARNING, occurrence.date, spent))
                warned = true
            }
        }
    }
}

class InvalidBudgetException(message: String) : IllegalArgumentException(message)
