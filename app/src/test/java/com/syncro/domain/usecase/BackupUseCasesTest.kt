package com.syncro.domain.usecase

import com.syncro.data.backup.JsonBackupCodec
import com.syncro.domain.model.Budget
import com.syncro.domain.model.InvalidBackupException
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.RestoreSummary
import com.syncro.testutil.FakeBudgetRepository
import com.syncro.testutil.FakeMovementRepository
import com.syncro.testutil.FakeNoteRepository
import com.syncro.testutil.aMovement
import com.syncro.testutil.aNote
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan: hacer una copia y restaurarla en otro móvil, y exportar el CSV. Riesgos: que restaurar
 * borre lo que ya hay, que duplique lo que ya está, y que un archivo malo deje datos a medias.
 */
@RunWith(RobolectricTestRunner::class) // El formato real de la copia usa org.json
class BackupUseCasesTest {

    private val codec = JsonBackupCodec()

    private class Phone {
        val notes = FakeNoteRepository()
        val movements = FakeMovementRepository()
        val budgets = FakeBudgetRepository()
    }

    private fun backupOf(phone: Phone) = CreateBackupUseCase(phone.notes, phone.movements, phone.budgets, JsonBackupCodec())
    private fun restoreOn(phone: Phone) = RestoreBackupUseCase(phone.notes, phone.movements, phone.budgets, codec)

    @Test
    fun `una copia hecha en un movil se recupera entera en otro`() = runTest {
        val old = Phone().apply {
            notes.insertNote(aNote(id = "n1"))
            movements.insertMovement(aMovement(id = "m1"))
            movements.insertMovement(aMovement(id = "m2"))
            budgets.saveBudget(Budget(MovementCategory.GROCERIES, 30_000))
        }
        val new = Phone()

        val result = restoreOn(new)(backupOf(old)())

        assertEquals(RestoreSummary(notes = 1, movements = 2, budgets = 1), result.getOrThrow())
        assertEquals(old.movements.movements.value, new.movements.movements.value)
        assertEquals(old.notes.notes.value, new.notes.notes.value)
        assertEquals(old.budgets.budgets.value, new.budgets.budgets.value)
    }

    @Test
    fun `restaurar no borra lo que ya hay ni duplica lo que coincide`() = runTest {
        val old = Phone().apply { movements.insertMovement(aMovement(id = "m1", amountCents = 1_000)) }
        val phone = Phone().apply {
            movements.insertMovement(aMovement(id = "m1", amountCents = 999))
            movements.insertMovement(aMovement(id = "solo-aqui"))
        }

        restoreOn(phone)(backupOf(old)())

        assertEquals(setOf("m1", "solo-aqui"), phone.movements.movements.value.keys)
        // Lo que coincide se actualiza con lo de la copia
        assertEquals(1_000L, phone.movements.movements.value.getValue("m1").amountCents)
    }

    @Test
    fun `un archivo que no es una copia no toca nada`() = runTest {
        val phone = Phone().apply { movements.insertMovement(aMovement(id = "m1")) }

        val result = restoreOn(phone)("esto no es una copia")

        assertTrue(result.exceptionOrNull() is InvalidBackupException)
        assertEquals(setOf("m1"), phone.movements.movements.value.keys)
    }

    @Test
    fun `el CSV lleva todos los movimientos`() = runTest {
        val phone = Phone().apply {
            movements.insertMovement(aMovement(id = "m1"))
            movements.insertMovement(aMovement(id = "m2"))
        }

        val lines = ExportMovementsCsvUseCase(phone.movements)().lines().filter { it.isNotBlank() }

        assertEquals(3, lines.size) // Cabecera + 2
    }
}
