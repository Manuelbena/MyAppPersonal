package com.syncro.presentation.savings

import android.annotation.SuppressLint
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.syncro.domain.model.MovementOccurrence
import com.syncro.domain.model.MovementType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Ticket de un ingreso o gasto: un justificante con aspecto de tique de caja (estrecho, letra
 * monoespaciada, líneas de puntos) que se imprime con el sistema de impresión de Android. Desde ahí
 * también se puede "Guardar como PDF" sin impresora.
 */

private val SPANISH = Locale("es", "ES")
private val TICKET_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", SPANISH)
private val TICKET_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", SPANISH)

/** Nombre del trabajo de impresión (y del PDF): "Syncro - Gasto 02-10-2026". */
fun ticketJobName(occurrence: MovementOccurrence): String =
    "Syncro - ${occurrence.movement.typeLabel} ${occurrence.date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))}"

/**
 * El ticket en HTML. Lleva una referencia corta (del id) para identificarlo y avisa de que es solo
 * informativo: no es una factura ni un justificante bancario.
 */
fun movementTicketHtml(occurrence: MovementOccurrence, printedAt: LocalDateTime): String {
    val movement = occurrence.movement
    val rows = buildList {
        add("Fecha" to occurrence.date.format(TICKET_DATE))
        add("Tipo" to movement.typeLabel)
        add("Categoría" to movement.category.label)
        movement.note?.let { add("Concepto" to it) }
        if (movement.repeatsMonthly) add("Frecuencia" to "Mensual")
    }
    val rowsHtml = rows.joinToString("\n") { (label, value) ->
        """<tr><td class="label">${label.escapeHtml()}</td><td class="value">${value.escapeHtml()}</td></tr>"""
    }
    return """
        <!doctype html>
        <html lang="es">
        <head>
        <meta charset="utf-8">
        <style>
          @page { margin: 6mm; }
          body { margin: 0; font-family: 'Courier New', monospace; color: #111; }
          .ticket { width: 72mm; margin: 0 auto; padding: 4mm 0; font-size: 11pt; }
          .center { text-align: center; }
          .brand { font-size: 16pt; font-weight: bold; letter-spacing: 3px; }
          .subtitle { font-size: 10pt; margin-top: 1mm; }
          .rule { border-top: 1px dashed #111; margin: 3mm 0; }
          table { width: 100%; border-collapse: collapse; }
          td { padding: 1mm 0; vertical-align: top; }
          .label { width: 40%; }
          .value { text-align: right; word-break: break-word; }
          .total { font-size: 15pt; font-weight: bold; display: flex; justify-content: space-between; }
          .small { font-size: 8.5pt; }
        </style>
        </head>
        <body>
        <div class="ticket">
          <div class="center brand">SYNCRO</div>
          <div class="center subtitle">Justificante de ${movement.typeLabel.lowercase(SPANISH)}</div>
          <div class="rule"></div>
          <table>
        $rowsHtml
          </table>
          <div class="rule"></div>
          <div class="total"><span>TOTAL</span><span>${formatSignedEuros(movement.amountCents, movement.type).escapeHtml()}</span></div>
          <div class="rule"></div>
          <div class="center small">Ref. ${movement.reference}</div>
          <div class="center small">Emitido el ${printedAt.format(TICKET_DATE_TIME)}</div>
          <div class="center small" style="margin-top:2mm">Documento informativo generado por Syncro.<br>No es una factura ni un justificante bancario.</div>
        </div>
        </body>
        </html>
    """.trimIndent()
}

/**
 * Abre el diálogo de impresión de Android con el ticket. Necesita el contexto de la Activity (el
 * diálogo es suyo). El WebView se crea solo para maquetar el ticket y se imprime al terminar de
 * cargar; queda vivo mientras el sistema lo necesite porque el adaptador de impresión lo retiene.
 */
fun Context.printMovementTicket(occurrence: MovementOccurrence, printedAt: LocalDateTime) {
    val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = ticketJobName(occurrence)
    pendingTicket = WebView(this).apply {
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                printManager.print(
                    jobName,
                    view.createPrintDocumentAdapter(jobName),
                    PrintAttributes.Builder().build()
                )
                // Desde aquí lo retiene el trabajo de impresión
                if (pendingTicket === view) pendingTicket = null
            }
        }
        loadDataWithBaseURL(null, movementTicketHtml(occurrence, printedAt), "text/html", "utf-8", null)
    }
}

/**
 * El WebView del ticket mientras carga: sin una referencia, el recolector de basura podía
 * llevárselo antes de terminar y el diálogo de impresión no llegaba a abrirse. Retiene la Activity
 * solo mientras se maqueta el ticket (milisegundos) y se suelta al empezar a imprimir.
 */
@SuppressLint("StaticFieldLeak")
private var pendingTicket: WebView? = null

private val com.syncro.domain.model.Movement.typeLabel: String
    get() = if (type == MovementType.INCOME) "Ingreso" else "Gasto"

/** Referencia corta del ticket: las 8 primeras letras o cifras del id, en mayúsculas. */
private val com.syncro.domain.model.Movement.reference: String
    get() = id.filter { it.isLetterOrDigit() }.take(8).uppercase(SPANISH)

private fun String.escapeHtml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")
