package com.syncro.domain.model

/*
 * Cuentas de ahorro: separan los ingresos, gastos y presupuestos (la personal, la conjunta, la
 * hucha de las vacaciones…). Siempre hay al menos una, "Principal", que es donde estaba todo antes
 * de que hubiera cuentas. En Ahorros e Inicio se ve la cuenta elegida; el asistente mira todas.
 */

/** Id de la cuenta que siempre existe (la que recibe lo anterior a las cuentas y lo que no tiene cuenta). */
const val MAIN_ACCOUNT_ID = "main"

const val MAIN_ACCOUNT_NAME = "Principal"

/** Largo máximo del nombre: tiene que caber en la cabecera de Ahorros. */
const val MAX_ACCOUNT_NAME_LENGTH = 24

data class SavingsAccount(val id: String, val name: String, val color: ArgbColor)

/** Todas las cuentas (en su orden) y la que se está viendo. */
data class SavingsAccounts(val all: List<SavingsAccount>, val active: SavingsAccount) {
    /** Con una sola cuenta no hace falta decir de cuál es cada cosa. */
    val hasSeveral: Boolean get() = all.size > 1

    fun nameOf(accountId: String): String? = all.firstOrNull { it.id == accountId }?.name
}

class InvalidAccountException(message: String) : IllegalArgumentException(message)
