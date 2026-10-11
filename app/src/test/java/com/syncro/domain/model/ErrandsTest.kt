package com.syncro.domain.model

import com.syncro.testutil.DAY
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrandsTest {

    @Test
    fun `junta las salidas pendientes del dia en orden`() {
        val events = listOf(
            anEvent(title = "Banco", categoryText = "Recados", startTime = at("12:00"), endTime = at("12:30")),
            anEvent(title = "Correos", categoryText = "Recados", startTime = at("10:00"), endTime = at("10:30")),
            anEvent(title = "Mercadona", categoryText = "Compras", startTime = at("18:00"), endTime = at("19:00")),
            anEvent(title = "ITV", categoryText = "Recados", isCompleted = true),
            anEvent(title = "Reunión", categoryText = "Trabajo"),
            anEvent(title = "Tintorería", categoryText = "Recados", date = DAY.plusDays(1))
        )

        assertEquals(ErrandsDay(DAY, listOf("Correos", "Banco", "Mercadona")), errandsOf(events, DAY))
    }

    @Test
    fun `una sola salida no hay con que juntarla`() {
        assertNull(errandsOf(listOf(anEvent(title = "Correos", categoryText = "Recados")), DAY))
    }

    @Test
    fun `el asistente propone hacerlas del tiron`() {
        val message = assistantConversation(
            answer = DigestAnswer.ACCEPTED,
            notificationsAllowed = true,
            errands = ErrandsDay(DAY, listOf("Correos", "Banco", "Mercadona"))
        ).messages.single { it.id == "errands-$DAY" }

        assertEquals(
            "🧭 Hoy tienes 3 salidas: Correos, Banco y Mercadona. Si puedes, hazlas del tirón y te ahorras viajes. 🚶",
            message.text
        )
    }
}
