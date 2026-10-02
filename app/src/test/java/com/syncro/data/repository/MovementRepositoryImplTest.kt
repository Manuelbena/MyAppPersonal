package com.syncro.data.repository

import com.syncro.data.local.SyncroDatabase
import com.syncro.data.local.entity.MovementEntity
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.testutil.aMovement
import com.syncro.testutil.createInMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth

/**
 * Plan de pruebas de [MovementRepositoryImpl]
 *
 * Responsabilidades: guardar y borrar ingresos y gastos (solo locales) y devolver los que pueden
 * contar en un mes: los de ese mes y los mensuales que empezaron antes.
 * Riesgos: que un mensual no aparezca en los meses siguientes (la consulta SQL), que aparezca
 * antes de empezar, o que una fila con una categoría desconocida rompa la lista.
 */
@RunWith(RobolectricTestRunner::class)
class MovementRepositoryImplTest {

    private lateinit var db: SyncroDatabase
    private lateinit var repository: MovementRepositoryImpl

    private val october = YearMonth.of(2026, 10)

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        repository = MovementRepositoryImpl(db.movementDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `los datos del movimiento se conservan al guardar y leer`() = runTest {
        val movement = aMovement(id = "m1", type = MovementType.INCOME, amountCents = 123_456, category = MovementCategory.SALARY, date = LocalDate.of(2026, 10, 1), note = "Nómina", repeatsMonthly = true)

        repository.insertMovement(movement)

        assertEquals(listOf(movement), repository.observeForMonth(october).first())
    }

    @Test
    fun `un mes trae los suyos y los mensuales que empezaron antes, no otros`() = runTest {
        repository.insertMovement(aMovement(id = "octubre", date = LocalDate.of(2026, 10, 31)))
        repository.insertMovement(aMovement(id = "alquiler", date = LocalDate.of(2026, 3, 1), repeatsMonthly = true))
        repository.insertMovement(aMovement(id = "septiembre", date = LocalDate.of(2026, 9, 30)))
        repository.insertMovement(aMovement(id = "noviembre", date = LocalDate.of(2026, 11, 1)))
        repository.insertMovement(aMovement(id = "mensual-futuro", date = LocalDate.of(2026, 11, 1), repeatsMonthly = true))

        val ids = repository.observeForMonth(october).first().map { it.id }.toSet()

        assertEquals(setOf("octubre", "alquiler"), ids)
    }

    @Test
    fun `borrar un mensual lo quita de todos los meses`() = runTest {
        repository.insertMovement(aMovement(id = "netflix", date = LocalDate.of(2026, 1, 15), repeatsMonthly = true))

        repository.deleteMovement("netflix")

        assertEquals(emptyList<Any>(), repository.observeForMonth(october).first())
        assertEquals(0, db.movementDao.count())
    }

    @Test
    fun `una categoria que ya no existe pasa a Otros de su tipo`() = runTest {
        db.movementDao.insertMovement(
            MovementEntity("viejo", "EXPENSE", 500, "CATEGORIA_RENOMBRADA", LocalDate.of(2026, 10, 2).toEpochDay(), null, false)
        )

        assertEquals(MovementCategory.OTHER_EXPENSE, repository.observeForMonth(october).first().single().category)
    }

    @Test
    fun `una fila con un tipo desconocido no se muestra en vez de sumarse mal`() = runTest {
        db.movementDao.insertMovement(
            MovementEntity("raro", "TRANSFER", 500, "OTHER_EXPENSE", LocalDate.of(2026, 10, 2).toEpochDay(), null, false)
        )

        assertEquals(emptyList<Any>(), repository.observeForMonth(october).first())
    }
}
