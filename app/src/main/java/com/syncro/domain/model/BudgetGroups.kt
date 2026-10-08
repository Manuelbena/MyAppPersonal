package com.syncro.domain.model

/*
 * Presupuestos agrupados con la regla 50/30/20 (la misma del reparto de la nómina en el
 * asistente): la mitad de lo que entra para lo necesario, un 30 % para caprichos y un 20 % para
 * ahorrar. Así una lista larga de presupuestos se lee en tres líneas.
 */

/** Los grupos de gasto y qué parte de lo que entra les toca. El 20 % restante es el ahorro. */
enum class BudgetGroup(val percent: Int) {
    /** Lo que hay que pagar sí o sí: casa, facturas, comida, transporte, salud, estudios. */
    NEEDS(50),
    /** Lo que se puede recortar: salir a comer, ocio, suscripciones, compras y lo demás. */
    WANTS(30)
}

/** Parte de lo que entra que debería quedar sin gastar. */
const val SAVINGS_PERCENT = 20

/** El grupo de una categoría de gasto ("Otros" cuenta como capricho: no es imprescindible). Null en ingresos. */
val MovementCategory.budgetGroup: BudgetGroup?
    get() = when (this) {
        MovementCategory.HOUSING, MovementCategory.BILLS, MovementCategory.GROCERIES,
        MovementCategory.TRANSPORT, MovementCategory.HEALTH, MovementCategory.EDUCATION -> BudgetGroup.NEEDS
        MovementCategory.RESTAURANTS, MovementCategory.LEISURE, MovementCategory.SUBSCRIPTIONS,
        MovementCategory.SHOPPING, MovementCategory.OTHER_EXPENSE -> BudgetGroup.WANTS
        else -> null
    }

/** De dónde sale el 50/30/20: la nómina del mes o, sin nómina apuntada, todo lo que ha entrado. */
data class BudgetBase(val cents: Long, val isSalary: Boolean)

/**
 * Cómo va un grupo en el mes: lo gastado en todas sus categorías (tengan presupuesto o no), lo que
 * le toca según [BudgetBase] (null si este mes no ha entrado nada) y sus presupuestos.
 */
data class BudgetGroupSummary(
    val group: BudgetGroup,
    val spentCents: Long,
    val targetCents: Long?,
    val statuses: List<BudgetStatus>
) {
    /** Cuánto del objetivo lleva gastado, en %; null sin objetivo. */
    val percent: Int? get() = targetCents?.takeIf { it > 0 }?.let { (spentCents * 100 / it).toInt() }

    /** Como un presupuesto: ámbar desde el 80 %, rojo si se pasa. Sin objetivo, el peor de sus presupuestos. */
    val level: BudgetLevel
        get() = targetCents?.let { BudgetStatus(Budget(MovementCategory.OTHER_EXPENSE, it), spentCents).level }
            ?: statuses.maxOfOrNull { it.level } ?: BudgetLevel.OK

    /** Si conviene enseñarlo abierto: algún presupuesto suyo va justo o pasado. */
    val needsAttention: Boolean get() = statuses.any { it.level != BudgetLevel.OK }
}

/** El ahorro del mes frente al 20 %: lo que queda (ingresos − gastos) y lo que debería quedar. */
data class SavingsGoal(val savedCents: Long, val targetCents: Long) {
    val percent: Int get() = if (targetCents <= 0) 0 else (savedCents * 100 / targetCents).toInt()
    val reached: Boolean get() = savedCents >= targetCents
}

/** El 50/30/20 del mes, para la tarjeta de presupuestos. */
data class BudgetPlan(
    val base: BudgetBase?,
    val groups: List<BudgetGroupSummary>,
    val savings: SavingsGoal?
)

/**
 * El 50/30/20 de este mes con estos presupuestos. La base es la nómina si se ha apuntado (lo que
 * propone el asistente el día de cobro) y, si no, todo lo que ha entrado (autónomos, otros
 * ingresos). Los objetivos son exactos al céntimo, como [salaryPlan].
 */
fun MonthMovements.budgetPlan(budgets: List<Budget>): BudgetPlan {
    val base = salaryCents()?.let { BudgetBase(it, isSalary = true) }
        ?: incomeCents.takeIf { it > 0 }?.let { BudgetBase(it, isSalary = false) }
    val plan = base?.let { salaryPlan(it.cents) }
    val statuses = budgetStatuses(budgets)
    val groups = BudgetGroup.entries.map { group ->
        BudgetGroupSummary(
            group = group,
            spentCents = occurrences
                .filter { it.movement.type == MovementType.EXPENSE && it.movement.category.budgetGroup == group }
                .sumOf { it.movement.amountCents },
            targetCents = plan?.let { if (group == BudgetGroup.NEEDS) it.needsCents else it.wantsCents },
            statuses = statuses.filter { it.budget.category.budgetGroup == group }
        )
    }
    return BudgetPlan(base, groups, plan?.let { SavingsGoal(balanceCents, it.savingsCents) })
}
