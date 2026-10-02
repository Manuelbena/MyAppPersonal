package com.syncro.presentation.savings

import android.content.Context
import com.syncro.domain.model.MonthMovements
import com.syncro.domain.model.Movement
import com.syncro.domain.model.MovementOccurrence
import com.syncro.domain.model.MovementType
import com.syncro.domain.model.totalsByCategory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/*
 * Qué dicen los tickets de Ahorros: el de un ingreso o gasto y el resumen de un mes (un extracto,
 * como el de un banco). Se pintan y comparten con Ticket.kt.
 */

private val SPANISH = Locale("es", "ES")
private val TICKET_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", SPANISH)
private val TICKET_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", SPANISH)
private val TICKET_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", SPANISH)

/** Firma de los textos compartidos (_cursiva_ en WhatsApp). */
private const val SIGNATURE = "✨ _Enviado con Syncro_"

private const val NOT_AN_INVOICE = "Documento informativo generado por Syncro. No es una factura ni un justificante bancario."

// region Ticket de un movimiento

/**
 * El ticket de un movimiento en el día en que cae (los mensuales, el del mes que se mira). Lleva
 * una referencia corta (del id) y avisa de que es solo informativo.
 */
fun movementTicket(occurrence: MovementOccurrence, issuedAt: LocalDateTime): TicketContent {
    val movement = occurrence.movement
    return TicketContent(
        title = "SYNCRO",
        subtitle = "Justificante de ${movement.typeLabel.lowercase(SPANISH)}",
        blocks = buildList {
            add(TicketBlock.Rule)
            add(TicketBlock.Row("Fecha", occurrence.date.format(TICKET_DATE)))
            add(TicketBlock.Row("Tipo", movement.typeLabel))
            add(TicketBlock.Row("Categoría", movement.category.label))
            movement.note?.let { add(TicketBlock.Row("Concepto", it)) }
            if (movement.repeatsMonthly) add(TicketBlock.Row("Frecuencia", "Mensual"))
            add(TicketBlock.Rule)
            add(TicketBlock.Total("TOTAL", formatSignedEuros(movement.amountCents, movement.type), movement.type))
            add(TicketBlock.Rule)
        },
        footer = listOf(
            "Ref. ${movement.reference}",
            "Emitido el ${issuedAt.format(TICKET_DATE_TIME)}",
            NOT_AN_INVOICE
        )
    )
}

/**
 * Pie de foto al compartir, con el formato de WhatsApp (*negrita*) y emojis:
 *
 * /// 🧾 *GASTO* · 🛒 Supermercado ///
 * 💸 *−45,90 €*
 * 📅 01/10/2026
 * 📝 Mercadona
 * ✨ _Enviado con Syncro_
 */
fun movementShareText(occurrence: MovementOccurrence): String {
    val movement = occurrence.movement
    val isIncome = movement.type == MovementType.INCOME
    return buildString {
        appendLine("/// 🧾 *${movement.typeLabel.uppercase(SPANISH)}* · ${movement.category.emoji} ${movement.category.label} ///")
        appendLine("${if (isIncome) "💰" else "💸"} *${formatSignedEuros(movement.amountCents, movement.type)}*")
        appendLine("📅 ${occurrence.date.format(TICKET_DATE)}")
        movement.note?.let { appendLine("📝 $it") }
        if (movement.repeatsMonthly) appendLine("🔁 Se repite cada mes")
        append(SIGNATURE)
    }
}

suspend fun Context.shareMovementTicket(occurrence: MovementOccurrence, issuedAt: LocalDateTime) =
    shareTicket(
        ticket = movementTicket(occurrence, issuedAt),
        caption = movementShareText(occurrence),
        fileName = "ticket-${occurrence.date}",
        chooserTitle = "Compartir ticket"
    )

// endregion

// region Resumen del mes

/**
 * Resumen de un mes, como un extracto: los totales y el balance, la tasa de ahorro, el desglose
 * por categoría (gastos e ingresos, con su %) y todos los movimientos. Los que aún no han llegado
 * a fecha de [issuedAt] se marcan con "*" (previstos).
 */
fun monthStatement(month: MonthMovements, issuedAt: LocalDateTime): TicketContent {
    val today = issuedAt.toLocalDate()
    val hasUpcoming = month.occurrences.any { it.date.isAfter(today) }
    return TicketContent(
        title = "SYNCRO",
        subtitle = "Resumen de ${month.monthName()}",
        blocks = buildList {
            add(TicketBlock.Rule)
            add(TicketBlock.Row("Ingresos", formatSignedEuros(month.incomeCents, MovementType.INCOME)))
            add(TicketBlock.Row("Gastos", formatSignedEuros(month.expenseCents, MovementType.EXPENSE)))
            add(TicketBlock.Rule)
            add(TicketBlock.Total("BALANCE", month.balanceCents.signedEuros(), month.balanceType()))
            month.savingsRatePercent?.let { add(TicketBlock.Row("Tasa de ahorro", "$it %", labelWeight = 0.6f)) }

            listOf(MovementType.EXPENSE to "GASTOS POR CATEGORÍA", MovementType.INCOME to "INGRESOS POR CATEGORÍA")
                .forEach { (type, heading) ->
                    val totals = month.totalsByCategory(type)
                    if (totals.isNotEmpty()) {
                        add(TicketBlock.Rule)
                        add(TicketBlock.Heading(heading))
                        totals.forEach { add(TicketBlock.Row(it.category.label, "${formatEuros(it.cents)} · ${it.percent} %")) }
                    }
                }

            add(TicketBlock.Rule)
            add(TicketBlock.Heading("MOVIMIENTOS (${month.occurrences.size})"))
            // Del primero al último del mes, como un extracto
            month.occurrences.sortedBy { it.date }.forEach { occurrence ->
                val movement = occurrence.movement
                val mark = if (occurrence.date.isAfter(today)) " *" else ""
                add(
                    TicketBlock.Row(
                        "${occurrence.date.format(TICKET_DAY)} ${movement.note ?: movement.category.label}$mark",
                        formatSignedEuros(movement.amountCents, movement.type),
                        labelWeight = 0.6f
                    )
                )
            }
            add(TicketBlock.Rule)
        },
        footer = buildList {
            if (hasUpcoming) add("* Previsto: aún no ha llegado.")
            add("Emitido el ${issuedAt.format(TICKET_DATE_TIME)}")
            add(NOT_AN_INVOICE)
        }
    )
}

/**
 * Pie de foto del resumen, con el formato de WhatsApp y emojis:
 *
 * /// 📊 *RESUMEN DE OCTUBRE DE 2026* ///
 * 💰 Ingresos: *+2.000,00 €*
 * 💸 Gastos: *−1.000,00 €*
 * ⚖️ Balance: *+1.000,00 €*
 * 🐷 Ahorro: *50 %* 💪
 * 🏆 Mayor gasto: 🏠 Vivienda (75 %)
 * ✨ _Enviado con Syncro_
 */
fun monthShareText(month: MonthMovements): String = buildString {
    appendLine("/// 📊 *RESUMEN DE ${month.monthName().uppercase(SPANISH)}* ///")
    appendLine("💰 Ingresos: *${formatSignedEuros(month.incomeCents, MovementType.INCOME)}*")
    appendLine("💸 Gastos: *${formatSignedEuros(month.expenseCents, MovementType.EXPENSE)}*")
    appendLine("⚖️ Balance: *${month.balanceCents.signedEuros()}*")
    month.savingsRatePercent?.let { rate ->
        val mood = when {
            rate >= 20 -> " 💪"
            rate > 0 -> " 👍"
            else -> " ⚠️"
        }
        appendLine("🐷 Ahorro: *$rate %*$mood")
    }
    month.totalsByCategory(MovementType.EXPENSE).firstOrNull()?.let { top ->
        appendLine("🏆 Mayor gasto: ${top.category.emoji} ${top.category.label} (${top.percent} %)")
    }
    append(SIGNATURE)
}

suspend fun Context.shareMonthStatement(month: MonthMovements, issuedAt: LocalDateTime) =
    shareTicket(
        ticket = monthStatement(month, issuedAt),
        caption = monthShareText(month),
        fileName = "resumen-${month.month}",
        chooserTitle = "Compartir resumen del mes"
    )

private fun MonthMovements.monthName(): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, SPANISH) + " de ${month.year}"

/** Verde si se ahorró, rojo si se gastó de más, neutro si quedó a cero. */
private fun MonthMovements.balanceType(): MovementType? = when {
    balanceCents > 0 -> MovementType.INCOME
    balanceCents < 0 -> MovementType.EXPENSE
    else -> null
}

/** "+1.006,06 €", "−20,00 €" o "0,00 €". */
private fun Long.signedEuros(): String = when {
    this > 0 -> formatSignedEuros(this, MovementType.INCOME)
    this < 0 -> formatSignedEuros(abs(this), MovementType.EXPENSE)
    else -> formatEuros(0)
}

// endregion

private val Movement.typeLabel: String
    get() = if (type == MovementType.INCOME) "Ingreso" else "Gasto"

/** Referencia corta del ticket: las 8 primeras letras o cifras del id, en mayúsculas. */
private val Movement.reference: String
    get() = id.filter { it.isLetterOrDigit() }.take(8).uppercase(SPANISH)
