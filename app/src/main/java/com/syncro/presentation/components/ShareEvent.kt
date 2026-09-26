package com.syncro.presentation.components

import android.content.Context
import android.content.Intent
import com.syncro.domain.model.SyncroItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHARE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale("es", "ES"))

/**
 * Texto con el que se comparte un evento, por ejemplo:
 * "Cena\nsáb 26 sept, 21:30 – 01:00 (dom 27 sept)\nUbicación: Casa de Ana\n\nLlevar postre"
 */
fun SyncroItem.Event.toShareText(): String = buildString {
    appendLine(title)
    appendLine(whenText())
    location?.takeIf { it.isNotBlank() }?.let { appendLine("Ubicación: $it") }
    description?.takeIf { it.isNotBlank() }?.let { append('\n').appendLine(it) }
}.trim()

private fun SyncroItem.Event.whenText(): String {
    val start = date.formatted()
    return when {
        isAllDay && endDate == date -> "$start, todo el día"
        isAllDay -> "$start – ${endDate.formatted()}, todo el día"
        endDate == date -> "$start, ${startTime.toDisplayTime()} – ${endTime.toDisplayTime()}"
        else -> "$start, ${startTime.toDisplayTime()} – ${endTime.toDisplayTime()} (${endDate.formatted()})"
    }
}

private fun LocalDate.formatted(): String = format(SHARE_DATE_FORMAT)

/** Abre el menú de compartir de Android (WhatsApp, correo…) con el evento como texto. */
fun Context.shareEvent(event: SyncroItem.Event) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, event.title)
        putExtra(Intent.EXTRA_TEXT, event.toShareText())
    }
    startActivity(Intent.createChooser(send, "Compartir evento"))
}
