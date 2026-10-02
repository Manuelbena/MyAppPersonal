package com.syncro.domain.model

/*
 * Lo que solo existe en el móvil (notas, ingresos y gastos, presupuestos) y por eso se puede
 * exportar o guardar en una copia. Tareas y eventos ya están en la cuenta de Google.
 */

/** El contenido de una copia de seguridad. */
data class BackupContent(
    val notes: List<SyncroItem.Note>,
    val movements: List<Movement>,
    val budgets: List<Budget>
)

/** Cuántas cosas se recuperaron al restaurar una copia. */
data class RestoreSummary(val notes: Int, val movements: Int, val budgets: Int) {
    val isEmpty: Boolean get() = notes == 0 && movements == 0 && budgets == 0
}

class InvalidBackupException : IllegalArgumentException("Este archivo no es una copia de Syncro")

/**
 * Ingresos y gastos en CSV para abrir en Excel o Google Sheets, con el formato español: separador
 * ";" y coma decimal, y una marca (BOM) para que Excel lea bien las tildes. Un movimiento mensual
 * sale una vez, con su primera fecha y "Sí" en la columna de repetición.
 */
fun movementsCsv(movements: List<Movement>): String = buildString {
    append(UTF8_BOM)
    appendLine("Fecha;Tipo;Categoría;Concepto;Importe;Se repite cada mes")
    movements.sortedWith(compareBy({ it.date }, { it.id })).forEach { movement ->
        val sign = if (movement.type == MovementType.EXPENSE) "-" else ""
        val amount = "$sign${movement.amountCents / 100},${"%02d".format(movement.amountCents % 100)}"
        appendLine(
            listOf(
                movement.date.toString(),
                if (movement.type == MovementType.INCOME) "Ingreso" else "Gasto",
                movement.category.label,
                movement.note.orEmpty(),
                amount,
                if (movement.repeatsMonthly) "Sí" else "No"
            ).joinToString(";") { it.csvField() }
        )
    }
}

/** Entre comillas si lleva ";", comillas o saltos de línea (y las comillas, dobladas). */
private fun String.csvField(): String =
    if (any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"${replace("\"", "\"\"")}\"" else this

/** Marca de UTF-8 al principio del CSV: sin ella, Excel abre las tildes mal ("CategorÃ­a"). */
private val UTF8_BOM = Char(0xFEFF)
