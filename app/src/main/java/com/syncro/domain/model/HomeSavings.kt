package com.syncro.domain.model

/**
 * El resumen de Ahorros que se ve en Inicio si se activa en Ajustes: cómo va el mes y los
 * presupuestos que van justos ([BudgetLevel.WARNING]) o pasados, los más apurados primero.
 */
data class HomeSavings(
    val period: SavingsPeriod,
    val incomeCents: Long,
    val expenseCents: Long,
    /** Si hay algo apuntado este mes; sin nada, la tarjeta invita a empezar. */
    val hasMovements: Boolean,
    val tightBudgets: List<BudgetStatus>,
    /** La cuenta que se ve, para decirla; null si solo hay una. */
    val accountName: String? = null
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}
