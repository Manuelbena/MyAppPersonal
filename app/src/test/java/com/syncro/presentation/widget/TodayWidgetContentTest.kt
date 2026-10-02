package com.syncro.presentation.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.syncro.domain.model.TodayAgenda
import com.syncro.testutil.DAY
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalTime

/**
 * Contenido del widget "Tu día": fecha, cada elemento con su hora, el "+" y el mensaje sin
 * pendientes. Riesgos: horas mal puestas (todo el día, eventos que vienen de anoche) y listas largas.
 */
@RunWith(RobolectricTestRunner::class)
class TodayWidgetContentTest {

    private fun show(agenda: TodayAgenda, assertions: androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { TodayWidgetContent(agenda) }
            assertions()
        }

    @Test
    fun `muestra la fecha y cada elemento con su hora`() = show(
        TodayAgenda(
            DAY,
            listOf(
                anEvent(title = "Dentista", startTime = at("10:00"), endTime = at("11:00")),
                aTask(title = "Llamar al banco", time = at("17:30")),
                aTask(title = "Comprar pan", time = LocalTime.MIDNIGHT)
            )
        )
    ) {
        onNode(hasText("Sábado 26 de septiembre")).assertExists()
        onNode(hasText("10:00")).assertExists()
        onNode(hasText("Dentista")).assertExists()
        onNode(hasText("17:30")).assertExists()
        onNode(hasText("Llamar al banco")).assertExists()
        // Tarea sin hora: "Todo el día" en lugar de 00:00, como en Inicio
        onNode(hasText("Todo el día")).assertExists()
    }

    @Test
    fun `un evento que viene de anoche muestra cuando termina`() = show(
        TodayAgenda(DAY, listOf(anEvent(title = "Guardia", date = DAY.minusDays(1), startTime = at("22:00"), endTime = at("08:00")).copy(endDate = DAY)))
    ) {
        onNode(hasText("→ 08:00")).assertExists()
    }

    @Test
    fun `sin nada pendiente lo celebra`() = show(TodayAgenda(DAY, emptyList())) {
        onNode(hasText("Nada más por hoy 🎉")).assertExists()
    }

    @Test
    fun `con muchas tareas dice cuantas mas hay`() = show(
        TodayAgenda(DAY, (1..10).map { aTask(title = "Tarea $it", time = at("09:00")) })
    ) {
        onNode(hasText("…y 2 más")).assertExists()
    }
}
