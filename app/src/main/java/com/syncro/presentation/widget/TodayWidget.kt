package com.syncro.presentation.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.syncro.MainActivity
import com.syncro.R
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.model.TodayAgenda
import com.syncro.domain.usecase.GetTodayAgendaUseCase
import com.syncro.presentation.components.toDisplayTime
import com.syncro.presentation.theme.DarkColorScheme
import com.syncro.presentation.theme.LightColorScheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Widget "Tu día": lo que te queda hoy (tareas sin hacer y eventos sin terminar) y un "+" para
 * apuntar una tarea. Lee solo el móvil, así que funciona sin conexión.
 *
 * Los widgets se dibujan con RemoteViews: no admiten fuentes propias, así que usa la del sistema.
 */

// Acceso a Hilt con EntryPoint, como los avisos diarios: el widget no es un componente de Hilt
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodayWidgetEntryPoint {
    fun getTodayAgenda(): GetTodayAgendaUseCase
}

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val agenda = EntryPointAccessors
            .fromApplication(context.applicationContext, TodayWidgetEntryPoint::class.java)
            .getTodayAgenda()()
        provideContent {
            GlanceTheme(colors = SyncroWidgetColors) {
                TodayWidgetContent(agenda)
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** Los colores de la app, en claro u oscuro según el sistema. */
private val SyncroWidgetColors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)

private const val MAX_ITEMS = 8
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-ES"))

@Composable
fun TodayWidgetContent(agenda: TodayAgenda) {
    val openApp = actionStartActivity<MainActivity>()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(16.dp)
            .clickable(openApp)
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    "Hoy",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
                )
                Text(
                    agenda.date.format(DAY_FORMAT).replaceFirstChar { it.uppercase() },
                    style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1
                )
            }
            AddTaskButton()
        }
        Spacer(GlanceModifier.height(12.dp))

        if (agenda.items.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nada más por hoy 🎉",
                    style = TextStyle(fontSize = 15.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        } else {
            val shown = agenda.items.take(MAX_ITEMS)
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(shown) { item -> AgendaRow(item, agenda.date) }
                if (agenda.items.size > shown.size) {
                    item {
                        Text(
                            "…y ${agenda.items.size - shown.size} más",
                            modifier = GlanceModifier.padding(top = 4.dp).clickable(openApp),
                            style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                        )
                    }
                }
            }
        }
    }
}

/** "+": abre la app directamente en la hoja de nueva tarea. */
@Composable
private fun AddTaskButton() {
    Box(
        modifier = GlanceModifier
            .size(40.dp)
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.primary)
            .clickable(actionStartActivity(addTaskIntent(LocalContext.current))),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_add),
            contentDescription = "Añadir tarea",
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
            modifier = GlanceModifier.size(22.dp)
        )
    }
}

/** Una fila: la hora, un punto del color de su categoría y el título. */
@Composable
private fun AgendaRow(item: SyncroItem, today: LocalDate) {
    val (time, title, color) = when (item) {
        is SyncroItem.Event -> Triple(eventTime(item, today), item.title, Color(item.categoryColor.argb))
        is SyncroItem.Task -> Triple(
            if (item.isAllDay) "Todo el día" else item.time.toDisplayTime(),
            item.title,
            item.categoryColor?.let { Color(it.argb) }
        )
        is SyncroItem.Note -> return
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            time,
            modifier = GlanceModifier.width(52.dp),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
            maxLines = 1
        )
        // Punto del color de su categoría (gris si la tarea no tiene)
        Box(
            modifier = GlanceModifier
                .size(10.dp)
                .cornerRadius(5.dp)
                .background(color?.let { ColorProvider(it) } ?: GlanceTheme.colors.outline),
            content = {}
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            title,
            style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
            maxLines = 1
        )
    }
}

private fun eventTime(event: SyncroItem.Event, today: LocalDate): String = when {
    event.isAllDay -> "Todo el día"
    // Empezó otro día (p. ej. anoche): lo útil es cuándo termina
    event.date.isBefore(today) -> "→ ${event.endTime.toDisplayTime()}"
    else -> event.startTime.toDisplayTime()
}

/** Abre la app en la hoja de nueva tarea (ver MainActivity). */
private fun addTaskIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    .putExtra(MainActivity.EXTRA_ADD_TASK, true)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
