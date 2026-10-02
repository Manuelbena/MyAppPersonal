package com.syncro.presentation.savings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.syncro.domain.model.MovementType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Tickets para compartir (WhatsApp, Telegram, correo…): una imagen con aspecto de tique de caja
 * (papel blanco con bordes rasgados, letra de máquina, líneas de puntos) y un texto corto que las
 * apps de chat usan como pie de foto. Lo usan el ticket de un movimiento y el resumen del mes.
 */

/** Una pieza del ticket, de arriba abajo. */
sealed interface TicketBlock {
    /** Etiqueta a la izquierda y valor a la derecha (en varias líneas si es largo). */
    data class Row(val label: String, val value: String, val labelWeight: Float = 0.4f) : TicketBlock

    /** Título de sección en mayúsculas ("GASTOS POR CATEGORÍA"). */
    data class Heading(val text: String) : TicketBlock

    /** Línea de puntos. */
    data object Rule : TicketBlock

    /** Importe destacado; [type] le da color (verde si entra, rojo si sale) o null para neutro. */
    data class Total(val label: String, val value: String, val type: MovementType?) : TicketBlock
}

/** Lo que dice el ticket, sin pintar: así se puede probar sin Android. */
data class TicketContent(
    val title: String,
    val subtitle: String,
    val blocks: List<TicketBlock>,
    val footer: List<String>
)

/**
 * Crea la imagen del ticket y abre el menú de compartir de Android con [caption] como pie de foto.
 * La imagen se guarda en la caché (carpeta "tickets", la única que expone el FileProvider) y se
 * borra la anterior, para no acumular. Necesita el contexto de la Activity para abrir el menú.
 */
suspend fun Context.shareTicket(ticket: TicketContent, caption: String, fileName: String, chooserTitle: String) {
    val file = withContext(Dispatchers.Default) {
        val bitmap = renderTicket(ticket)
        val dir = File(cacheDir, "tickets").apply {
            deleteRecursively()
            mkdirs()
        }
        File(dir, "$fileName.png").also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, caption)
        // El permiso de lectura viaja con el ClipData hasta la app que se elija
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, chooserTitle))
}

/** Ancho de la imagen en píxeles: nítida en el chat sin pesar mucho. */
private const val TICKET_WIDTH_PX = 1080

/**
 * Pinta el ticket. Las medidas van en "unidades" de un ancho de 360 (como dp en un móvil) y se
 * escalan al ancho de la imagen; el alto sale del contenido (un concepto largo o un mes con muchos
 * movimientos hacen el ticket más largo, nunca se corta).
 */
fun renderTicket(ticket: TicketContent): Bitmap {
    val u = TICKET_WIDTH_PX / 360f
    val dark = 0xFF111827.toInt()
    val gray = 0xFF64748B.toInt()

    fun textPaint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        textSize = size * u
        this.color = color
    }

    // El papel: márgenes alrededor y el contenido dentro
    val margin = 20 * u
    val paperPadding = 22 * u
    val paperLeft = margin
    val paperRight = TICKET_WIDTH_PX - margin
    val contentLeft = paperLeft + paperPadding
    val contentWidth = (paperRight - paperLeft - 2 * paperPadding).toInt()

    fun layout(text: String, paint: TextPaint, width: Int, align: Layout.Alignment) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(align).build()

    // Cada pieza sabe su alto y cómo pintarse a partir de una altura
    val blocks = mutableListOf<Pair<Float, (Canvas, Float) -> Unit>>()
    fun space(height: Float) = blocks.add(height * u to { _, _ -> })
    fun text(text: String, paint: TextPaint, align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER) {
        val l = layout(text, paint, contentWidth, align)
        blocks.add(l.height.toFloat() to { c, y -> c.withTranslation(contentLeft, y) { l.draw(this) } })
    }
    fun dashedRule() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gray
            strokeWidth = 1.5f * u
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(6 * u, 5 * u), 0f)
        }
        blocks.add(20 * u to { c, y -> c.drawLine(contentLeft, y + 10 * u, contentLeft + contentWidth, y + 10 * u, paint) })
    }
    fun row(label: String, value: String, labelPaint: TextPaint, valuePaint: TextPaint, labelWeight: Float) {
        // La etiqueta a la izquierda; el valor a la derecha y, si es largo, en varias líneas
        val labelWidth = (contentWidth * labelWeight).toInt()
        val valueWidth = contentWidth - labelWidth
        val labelLayout = layout(label, labelPaint, labelWidth, Layout.Alignment.ALIGN_NORMAL)
        val valueLayout = layout(value, valuePaint, valueWidth, Layout.Alignment.ALIGN_OPPOSITE)
        // Misma línea base aunque el tamaño sea distinto ("TOTAL" y el importe, más grande)
        val baselineGap = (valueLayout.getLineBaseline(0) - labelLayout.getLineBaseline(0)).toFloat()
        val labelTop = maxOf(0f, baselineGap)
        val valueTop = maxOf(0f, -baselineGap)
        val height = maxOf(labelTop + labelLayout.height, valueTop + valueLayout.height) + 6 * u
        blocks.add(height to { c, y ->
            c.withTranslation(contentLeft, y + labelTop) { labelLayout.draw(this) }
            c.withTranslation(contentLeft + labelWidth, y + valueTop) { valueLayout.draw(this) }
        })
    }

    text(ticket.title, textPaint(26f, dark, bold = true).apply { letterSpacing = 0.25f })
    space(4f)
    text(ticket.subtitle, textPaint(13f, gray))
    space(8f)
    ticket.blocks.forEach { block ->
        when (block) {
            is TicketBlock.Row -> row(block.label, block.value, textPaint(13f, gray), textPaint(13f, dark), block.labelWeight)
            is TicketBlock.Heading -> {
                space(2f)
                text(block.text, textPaint(12f, dark, bold = true).apply { letterSpacing = 0.1f }, Layout.Alignment.ALIGN_NORMAL)
                space(6f)
            }
            TicketBlock.Rule -> dashedRule()
            is TicketBlock.Total -> {
                space(4f)
                val color = block.type?.color?.toArgb() ?: dark
                row(block.label, block.value, textPaint(18f, dark, bold = true), textPaint(20f, color, bold = true), 0.4f)
            }
        }
    }
    space(4f)
    ticket.footer.forEach { line ->
        text(line, textPaint(10.5f, gray))
        space(4f)
    }

    // Alto: bordes rasgados + relleno + contenido
    val tooth = 12 * u
    val paperTop = margin + tooth
    val contentHeight = blocks.sumOf { it.first.toDouble() }.toFloat()
    val paperBottom = paperTop + paperPadding + contentHeight + paperPadding
    val height = (paperBottom + tooth + margin).toInt()

    val bitmap = createBitmap(TICKET_WIDTH_PX, height)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xFFEEF2F6.toInt())

    // Papel con dientes arriba y abajo, como un tique recién cortado
    val paper = Path().apply {
        val teeth = ((paperRight - paperLeft) / tooth).toInt()
        val step = (paperRight - paperLeft) / teeth
        moveTo(paperLeft, paperTop)
        for (i in 0 until teeth) {
            lineTo(paperLeft + (i + 0.5f) * step, paperTop - tooth)
            lineTo(paperLeft + (i + 1) * step, paperTop)
        }
        lineTo(paperRight, paperBottom)
        for (i in teeth - 1 downTo 0) {
            lineTo(paperLeft + (i + 0.5f) * step, paperBottom + tooth)
            lineTo(paperLeft + i * step, paperBottom)
        }
        close()
    }
    canvas.drawPath(paper, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })

    var y = paperTop + paperPadding
    blocks.forEach { (blockHeight, draw) ->
        draw(canvas, y)
        y += blockHeight
    }
    return bitmap
}
