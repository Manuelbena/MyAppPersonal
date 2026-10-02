package com.syncro.data.backup

import com.syncro.domain.model.BackupContent
import com.syncro.domain.model.Budget
import com.syncro.domain.model.InvalidBackupException
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.testutil.aMovement
import com.syncro.testutil.aNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan de pruebas de [JsonBackupCodec] (Robolectric: org.json es de Android).
 *
 * Responsabilidades: guardar y leer una copia sin perder nada. Riesgos: perder datos en el viaje
 * (conceptos nulos, colores, fechas), aceptar un archivo que no es una copia o uno dañado, y
 * romperse con categorías que ya no existen.
 */
@RunWith(RobolectricTestRunner::class)
class JsonBackupCodecTest {

    private val codec = JsonBackupCodec()

    @Test
    fun `una copia se lee exactamente como se guardo`() {
        val content = BackupContent(
            notes = listOf(aNote(id = "n1", title = "Ideas", content = "Línea 1\nLínea \"2\"")),
            movements = listOf(
                aMovement(id = "m1", note = null),
                aMovement(id = "m2", type = MovementType.INCOME, amountCents = 185_000, category = MovementCategory.SALARY, note = "Nómina", repeatsMonthly = true)
            ),
            budgets = listOf(Budget(MovementCategory.GROCERIES, 30_000))
        )

        assertEquals(content, codec.decode(codec.encode(content)))
    }

    @Test
    fun `un archivo que no es una copia de Syncro se rechaza`() {
        listOf("", "hola", "{}", """{"app":"otra"}""", "[1,2]").forEach { text ->
            assertThrows(InvalidBackupException::class.java) { codec.decode(text) }
        }
    }

    @Test
    fun `una copia danada se rechaza entera`() {
        val broken = """{"app":"syncro","movements":[{"id":"m1","type":"EXPENSE","amountCents":100,"category":"GROCERIES","date":"no-es-fecha"}]}"""

        assertThrows(InvalidBackupException::class.java) { codec.decode(broken) }
    }

    @Test
    fun `una categoria que ya no existe pasa a Otros y un presupuesto de ingresos se ignora`() {
        val text = """{"app":"syncro","version":1,
            "movements":[{"id":"m1","type":"EXPENSE","amountCents":100,"category":"RENOMBRADA","date":"2026-10-01","note":null,"repeatsMonthly":false}],
            "budgets":[{"category":"SALARY","limitCents":100},{"category":"LEISURE","limitCents":5000}]}"""

        val content = codec.decode(text)

        assertEquals(MovementCategory.OTHER_EXPENSE, content.movements.single().category)
        assertEquals(listOf(Budget(MovementCategory.LEISURE, 5000)), content.budgets)
        assertEquals(emptyList<Any>(), content.notes)
    }
}
