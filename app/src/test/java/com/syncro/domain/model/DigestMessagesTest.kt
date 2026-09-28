package com.syncro.domain.model

import com.syncro.testutil.DAY
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Redacción de los avisos diarios.
 * Responsabilidades: resumir el día (mañana) y cómo ha ido (noche) en tono cercano, con el detalle
 * en líneas y un adelanto de mañana. Riesgos: plurales, días vacíos, eventos que vienen de ayer
 * (no son "lo primero" del día), nombre desconocido y listas largas.
 */
class DigestMessagesTest {

    private fun digest(
        moment: DigestMoment,
        tasks: List<SyncroItem.Task> = emptyList(),
        events: List<SyncroItem.Event> = emptyList(),
        tomorrowTasks: List<SyncroItem.Task> = emptyList(),
        tomorrowEvents: List<SyncroItem.Event> = emptyList(),
        firstName: String = "Ana"
    ) = DailyDigest(moment, DAY, firstName, tasks, events, tomorrowTasks, tomorrowEvents)

    // region Mañana

    @Test
    fun `por la manana resume el dia y dice que es lo primero`() {
        val message = digest(
            DigestMoment.MORNING,
            tasks = listOf(aTask(title = "Llamar al banco", time = at("12:30")), aTask(title = "Comprar pan", time = LocalTime.MIDNIGHT)),
            events = listOf(anEvent(title = "Reunión", startTime = at("10:00"), endTime = at("11:00")))
        ).toMessage()

        assertTrue(message.title.contains("Ana"))
        assertEquals("Hoy tienes 2 tareas y 1 evento. Lo primero: Reunión a las 10:00.", message.text)
        // En el orden del día: lo de todo el día primero, luego por hora
        assertEquals(
            listOf("◻️ Comprar pan", "🗓️ 10:00 · Reunión", "◻️ 12:30 · Llamar al banco"),
            message.lines.take(3)
        )
    }

    @Test
    fun `por la manana un dia vacio se celebra como dia libre`() {
        val message = digest(DigestMoment.MORNING).toMessage()

        assertTrue(message.lines.isEmpty())
        assertFalse(message.text.contains("tienes 0"))
    }

    @Test
    fun `por la manana si ya estan hechas todas las tareas lo reconoce`() {
        val message = digest(DigestMoment.MORNING, tasks = listOf(aTask(isCompleted = true))).toMessage()

        assertEquals("Ya tienes hecho todo lo de hoy. ¡Vas sobrado! 🚀", message.text)
    }

    // Regresión de diseño: un evento de ayer a las 23:00 no es "lo primero" de hoy
    @Test
    fun `un evento que viene de ayer no es lo primero y se muestra con su hora de fin`() {
        val message = digest(
            DigestMoment.MORNING,
            tasks = listOf(aTask(title = "Comer", time = at("12:00"))),
            events = listOf(anEvent(title = "Fiesta", date = DAY.minusDays(1), endDate = DAY, startTime = at("23:00"), endTime = at("02:00")))
        ).toMessage()

        assertTrue(message.text.endsWith("Lo primero: Comer a las 12:00."))
        assertTrue(message.lines.contains("🗓️ Hasta las 02:00 · Fiesta"))
    }

    @Test
    fun `con muchas cosas se muestran cinco y se resume el resto`() {
        val tasks = (1..7).map { aTask(title = "Tarea $it", time = at("1$it:00")) }

        val message = digest(DigestMoment.MORNING, tasks = tasks).toMessage()

        assertTrue(message.lines.contains("…y 2 más"))
        assertEquals(5, message.lines.count { it.startsWith("◻️") })
    }

    // endregion

    // region Noche

    @Test
    fun `por la noche con todo hecho felicita`() {
        val message = digest(DigestMoment.EVENING, tasks = listOf(aTask(isCompleted = true), aTask(isCompleted = true))).toMessage()

        assertEquals("Has hecho las 2 tareas de hoy. Descansa, te lo has ganado ✨", message.text)
        assertEquals(listOf("🔜 Mañana tienes la agenda libre."), message.lines)
    }

    @Test
    fun `por la noche con una sola tarea hecha usa el singular`() {
        val message = digest(DigestMoment.EVENING, tasks = listOf(aTask(isCompleted = true))).toMessage()

        assertTrue(message.text.startsWith("Has hecho tu tarea de hoy."))
    }

    @Test
    fun `por la noche a medias dice cuantas quedan y cuales`() {
        val message = digest(
            DigestMoment.EVENING,
            tasks = listOf(
                aTask(title = "Hecha", isCompleted = true),
                aTask(title = "Llamar al banco", time = LocalTime.MIDNIGHT),
                aTask(title = "Gimnasio", time = at("19:00"))
            )
        ).toMessage()

        assertEquals("Has hecho 1 de 3 tareas. Te quedan 2 tareas: aún estás a tiempo, o lo dejamos para mañana 💪", message.text)
        assertTrue(message.lines.containsAll(listOf("◻️ Llamar al banco", "◻️ 19:00 · Gimnasio")))
    }

    @Test
    fun `por la noche con pendientes la notificacion lleva al chat para decidir`() {
        val message = digest(DigestMoment.EVENING, tasks = listOf(aTask(), aTask(isCompleted = true))).toMessage()

        assertTrue(message.opensAssistant)
        assertEquals("👉 Toca para decidir qué hacer con ellas", message.lines.last())
    }

    @Test
    fun `sin pendientes la notificacion no lleva al chat`() {
        assertFalse(digest(DigestMoment.EVENING, tasks = listOf(aTask(isCompleted = true))).toMessage().opensAssistant)
        // Una sola tarea: no hay prioridades que elegir
        assertFalse(digest(DigestMoment.MORNING, tasks = listOf(aTask())).toMessage().opensAssistant)
    }

    @Test
    fun `por la manana con varias tareas invita a elegir las prioridades`() {
        val message = digest(DigestMoment.MORNING, tasks = listOf(aTask(), aTask())).toMessage()

        assertTrue(message.opensAssistant)
        assertEquals("👉 Toca para elegir tus 3 prioridades", message.lines.last())
    }

    @Test
    fun `por la manana con las prioridades ya elegidas no vuelve a invitar`() {
        val message = digest(DigestMoment.MORNING, tasks = listOf(aTask(), aTask())).copy(offerFocus = false).toMessage()

        assertFalse(message.opensAssistant)
    }

    @Test
    fun `por la noche sin nada hecho anima sin agobiar`() {
        val message = digest(DigestMoment.EVENING, tasks = listOf(aTask())).toMessage()

        assertEquals("Hoy te queda 1 tarea. No pasa nada: mañana será otro día 💪", message.text)
    }

    @Test
    fun `por la noche adelanta lo primero de manana`() {
        val message = digest(
            DigestMoment.EVENING,
            tomorrowEvents = listOf(anEvent(title = "Dentista", date = DAY.plusDays(1), startTime = at("09:00"), endTime = at("10:00"))),
            tomorrowTasks = listOf(aTask(title = "Recoger paquete", date = DAY.plusDays(1), time = LocalTime.MIDNIGHT))
        ).toMessage()

        assertEquals(
            "Hoy no tenías tareas apuntadas. Mañana tienes 1 evento y 1 tarea; empiezas a las 09:00 con Dentista.",
            message.text
        )
    }

    // endregion

    @Test
    fun `sin nombre el saludo no deja una coma suelta`() {
        val message = digest(DigestMoment.MORNING, firstName = "").toMessage()

        assertFalse(message.title.contains(","))
    }

    @Test
    fun `la frase del dia es estable dentro del mismo dia`() {
        val first = digest(DigestMoment.MORNING).toMessage()
        val second = digest(DigestMoment.MORNING).toMessage()

        assertEquals(first, second)
    }

    @Test
    fun `el siguiente aviso es hoy si aun no ha pasado la hora y si no manana`() {
        assertEquals(DAY.atTime(9, 0), DigestMoment.MORNING.nextAfter(DAY.atTime(8, 0)))
        // Justo a su hora ya cuenta como pasado: la alarma que suena no se reprograma para ese instante
        assertEquals(DAY.plusDays(1).atTime(9, 0), DigestMoment.MORNING.nextAfter(DAY.atTime(9, 0)))
        assertEquals(DAY.atTime(21, 0), DigestMoment.EVENING.nextAfter(DAY.atTime(9, 0)))
        assertEquals(DAY.plusDays(1).atTime(21, 0), DigestMoment.EVENING.nextAfter(DAY.atTime(22, 30)))
    }
}
