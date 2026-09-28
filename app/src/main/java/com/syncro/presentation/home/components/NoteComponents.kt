package com.syncro.presentation.home.components

import com.syncro.presentation.theme.NotebookFontFamily
import com.syncro.presentation.theme.toColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncro.domain.model.SyncroItem
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun NotesSection(
    notes: List<SyncroItem.Note>,
    onNoteClick: (SyncroItem.Note) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mi libreta",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = NotebookFontFamily,
                    fontStyle = FontStyle.Italic
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Spacer(Modifier.width(10.dp))
            CountBadge(notes.size)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSeeAllClick) {
                Text("Ver todas", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(notes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    onClick = { onNoteClick(note) },
                    // Alto mínimo y no fijo: con la letra grande del sistema la tarjeta crece en vez de cortar texto
                    modifier = Modifier
                        .width(200.dp)
                        .heightIn(min = 176.dp)
                )
            }
        }
    }
}

/** Número de notas junto al título de la libreta. */
@Composable
fun CountBadge(count: Int) {
    Text(
        text = count.toString(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 2.dp)
    )
}

/**
 * Tarjeta de nota: degradado del color de la nota, punto de color con la fecha y el título en
 * serif cursiva (el toque de libreta). La usan el carrusel de Hoy y la cuadrícula de la libreta.
 */
@Composable
fun NoteCard(
    note: SyncroItem.Note,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentMaxLines: Int = 3
) {
    val noteColor = note.color.toColor()

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, noteColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(noteColor.copy(alpha = 0.28f), noteColor.copy(alpha = 0.06f))))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(noteColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatRelativeTime(note.createdAt),
                    maxLines = 1,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = note.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = NotebookFontFamily,
                    fontStyle = FontStyle.Italic
                ),
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (note.content.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = note.content,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = contentMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun formatRelativeTime(dateTime: LocalDateTime): String {
    val now = LocalDateTime.now()
    val duration = Duration.between(dateTime, now)

    return when {
        duration.toMinutes() < 1 -> "AHORA"
        duration.toMinutes() < 60 -> "HACE ${duration.toMinutes()} MIN"
        duration.toHours() < 24 -> "HACE ${duration.toHours()} H"
        duration.toDays() == 1L -> "AYER"
        else -> dateTime.format(DateTimeFormatter.ofPattern("dd MMM"))
    }.uppercase()
}
