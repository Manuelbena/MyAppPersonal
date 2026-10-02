package com.syncro.domain.model

import com.syncro.testutil.aMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Plan: el CSV de ingresos y gastos. Riesgos: que Excel en español no lo abra bien (separador,
 * decimales, tildes) y que un concepto con ";" o comillas descoloque las columnas.
 */
class MovementsCsvTest {

    @Test
    fun `formato espanol, en orden de fecha y con el signo del gasto`() {
        val csv = movementsCsv(
            listOf(
                aMovement(id = "b", amountCents = 4_590, category = MovementCategory.GROCERIES, date = LocalDate.of(2026, 10, 3), note = "Mercadona"),
                aMovement(id = "a", type = MovementType.INCOME, amountCents = 185_000, category = MovementCategory.SALARY, date = LocalDate.of(2026, 10, 1), note = null, repeatsMonthly = true)
            )
        )

        // Marca para que Excel lea las tildes
        assertTrue(csv.startsWith(Char(0xFEFF).toString()))
        assertEquals(
            listOf(
                "Fecha;Tipo;Categoría;Concepto;Importe;Se repite cada mes",
                "2026-10-01;Ingreso;Nómina;;1850,00;Sí",
                "2026-10-03;Gasto;Supermercado;Mercadona;-45,90;No"
            ),
            csv.removePrefix(Char(0xFEFF).toString()).lines().filter { it.isNotEmpty() }
        )
    }

    @Test
    fun `un concepto con punto y coma o comillas no rompe las columnas`() {
        val csv = movementsCsv(listOf(aMovement(note = "Cena; con \"Ana\"", amountCents = 5)))

        assertTrue(csv.contains(";\"Cena; con \"\"Ana\"\"\";-0,05;"))
    }
}
