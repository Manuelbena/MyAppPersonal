package com.syncro.domain.usecase

import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.repository.SavingsAccountRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * Apunta el gasto de un evento desde Inicio (ver `expenseDraft`). Va a la cuenta que se está viendo
 * en Ahorros, igual que si se hubiera apuntado allí.
 */
class SaveEventExpenseUseCase @Inject constructor(
    private val saveMovement: SaveMovementUseCase,
    private val accounts: SavingsAccountRepository
) {
    suspend operator fun invoke(
        amountCents: Long,
        category: MovementCategory,
        date: LocalDate,
        note: String,
        repeatsMonthly: Boolean
    ): Result<Unit> = saveMovement(
        type = MovementType.EXPENSE,
        amountCents = amountCents,
        category = category,
        date = date,
        note = note,
        repeatsMonthly = repeatsMonthly,
        accountId = accounts.observeAccounts().first().active.id
    )
}
