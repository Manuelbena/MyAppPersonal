package com.syncro.domain.usecase

import com.syncro.domain.model.DataLossSummary
import com.syncro.domain.repository.AccountDataRepository
import javax.inject.Inject

/** Qué se perdería al cerrar sesión ahora, para avisar antes de hacerlo. */
class GetDataLossSummaryUseCase @Inject constructor(
    private val accountData: AccountDataRepository
) {
    suspend operator fun invoke(): DataLossSummary = accountData.dataLossSummary()
}
