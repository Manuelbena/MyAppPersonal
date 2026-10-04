package com.syncro.data.backup

import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.BackupContent
import com.syncro.domain.model.Budget
import com.syncro.domain.model.InvalidBackupException
import com.syncro.domain.model.Movement
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.model.MAIN_ACCOUNT_ID
import com.syncro.domain.model.SavingsAccount
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.BackupCodec
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * La copia de seguridad en JSON:
 * {"app":"syncro","version":2,"notes":[…],"accounts":[…],"movements":[…],"budgets":[…]}
 *
 * La versión 1 (antes de las cuentas de ahorro) no tiene "accounts" ni "accountId": todo va a la
 * cuenta principal.
 *
 * Al leer se perdona lo que se pueda (una categoría que ya no existe va a "Otros", un presupuesto
 * de ingresos se ignora), pero un archivo que no es de Syncro se rechaza entero.
 */
class JsonBackupCodec @Inject constructor() : BackupCodec {

    override fun encode(content: BackupContent): String = JSONObject()
        .put("app", APP)
        .put("version", VERSION)
        .put("notes", JSONArray(content.notes.map { it.toJson() }))
        .put("accounts", JSONArray(content.accounts.map { it.toJson() }))
        .put("movements", JSONArray(content.movements.map { it.toJson() }))
        .put("budgets", JSONArray(content.budgets.map { it.toJson() }))
        .toString(2)

    override fun decode(text: String): BackupContent = try {
        val root = JSONObject(text)
        if (root.optString("app") != APP) throw InvalidBackupException()
        BackupContent(
            notes = root.optJSONArray("notes").objects().map { it.toNote() },
            movements = root.optJSONArray("movements").objects().map { it.toMovement() },
            budgets = root.optJSONArray("budgets").objects().mapNotNull { it.toBudget() },
            accounts = root.optJSONArray("accounts").objects().map { it.toAccount() }
        )
    } catch (e: InvalidBackupException) {
        throw e
    } catch (e: JSONException) {
        throw InvalidBackupException()
    } catch (e: RuntimeException) {
        // Fechas, tipos o importes que no se pueden leer: el archivo está dañado
        throw InvalidBackupException()
    }

    private fun SyncroItem.Note.toJson() = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("content", content)
        .put("color", color.argb)
        .put("createdAt", createdAt.toString())

    private fun JSONObject.toNote() = SyncroItem.Note(
        id = getString("id").also { require(it.isNotBlank()) },
        title = getString("title"),
        content = getString("content"),
        color = ArgbColor(getInt("color")),
        createdAt = LocalDateTime.parse(getString("createdAt"))
    )

    private fun Movement.toJson() = JSONObject()
        .put("id", id)
        .put("type", type.name)
        .put("amountCents", amountCents)
        .put("category", category.name)
        .put("date", date.toString())
        .put("note", note ?: JSONObject.NULL)
        .put("repeatsMonthly", repeatsMonthly)
        .put("accountId", accountId)

    private fun JSONObject.toMovement(): Movement {
        val type = MovementType.valueOf(getString("type"))
        return Movement(
            id = getString("id").also { require(it.isNotBlank()) },
            type = type,
            amountCents = getLong("amountCents").also { require(it > 0) },
            category = MovementCategory.entries.firstOrNull { it.name == optString("category") && it.type == type }
                ?: MovementCategory.other(type),
            date = LocalDate.parse(getString("date")),
            note = if (isNull("note")) null else optString("note").ifBlank { null },
            repeatsMonthly = optBoolean("repeatsMonthly"),
            accountId = optString("accountId").ifBlank { MAIN_ACCOUNT_ID }
        )
    }

    private fun Budget.toJson() = JSONObject()
        .put("category", category.name)
        .put("limitCents", limitCents)
        .put("accountId", accountId)

    private fun JSONObject.toBudget(): Budget? {
        val category = MovementCategory.entries
            .firstOrNull { it.name == optString("category") && it.type == MovementType.EXPENSE } ?: return null
        val limit = getLong("limitCents").takeIf { it > 0 } ?: return null
        return Budget(category, limit, optString("accountId").ifBlank { MAIN_ACCOUNT_ID })
    }

    private fun SavingsAccount.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("color", color.argb)

    private fun JSONObject.toAccount() = SavingsAccount(
        id = getString("id").also { require(it.isNotBlank()) },
        name = getString("name").also { require(it.isNotBlank()) },
        color = ArgbColor(getInt("color"))
    )

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private companion object {
        const val APP = "syncro"
        const val VERSION = 2
    }
}
