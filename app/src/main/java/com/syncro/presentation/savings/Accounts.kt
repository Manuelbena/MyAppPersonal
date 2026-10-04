package com.syncro.presentation.savings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.MAX_ACCOUNT_NAME_LENGTH
import com.syncro.domain.model.SavingsAccount
import com.syncro.domain.model.SavingsAccounts
import com.syncro.presentation.theme.*

/*
 * Cuentas de ahorro: el botón de la cabecera de Ahorros con la cuenta que se ve, la hoja para
 * cambiar de cuenta y el diálogo para crear, renombrar o borrar una.
 */

/** Colores para distinguir las cuentas (los mismos tonos que el resto de la app). */
private val ACCOUNT_COLORS = listOf(Emerald500, Sky500, Indigo500, Violet500, Pink500, Rose500, Amber500, Slate500)

/** La inicial de la cuenta sobre su color: "P" de Principal, "C" de Conjunta. */
@Composable
private fun AccountBadge(account: SavingsAccount, size: Int = 36) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(account.color.toColor()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            account.name.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (size >= 36) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium
        )
    }
}

/** La cuenta que se ve, en la cabecera de Ahorros: tocarla abre la lista para cambiar de cuenta. */
@Composable
fun AccountSwitcher(active: SavingsAccount, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = active.color.toColor().copy(alpha = 0.14f),
        modifier = modifier
            .heightIn(min = 40.dp)
            .semantics { contentDescription = "Cuenta ${active.name}. Cambiar de cuenta" }
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountBadge(active, size = 24)
            Spacer(Modifier.width(8.dp))
            Text(
                active.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Las cuentas para elegir cuál se ve, con su lápiz para cambiarlas y "Nueva cuenta". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsSheet(
    accounts: SavingsAccounts,
    onSelect: (String) -> Unit,
    onSave: (name: String, color: ArgbColor, id: String?) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        AccountsContent(
            accounts = accounts,
            onSelect = {
                onSelect(it)
                onDismiss()
            },
            onSave = onSave,
            onDelete = onDelete
        )
    }
}

/** El contenido de [AccountsSheet]: la lista y los diálogos de crear o cambiar una cuenta. */
@Composable
fun AccountsContent(
    accounts: SavingsAccounts,
    onSelect: (String) -> Unit,
    onSave: (name: String, color: ArgbColor, id: String?) -> Unit,
    onDelete: (String) -> Unit
) {
    // null = cerrado; Editing(null) = cuenta nueva
    var editing by remember { mutableStateOf<Editing?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Tus cuentas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Cada cuenta tiene sus propios ingresos, gastos y presupuestos",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        accounts.all.forEach { account ->
            val isActive = account.id == accounts.active.id
            Surface(
                onClick = { onSelect(account.id) },
                shape = RoundedCornerShape(16.dp),
                color = if (isActive) account.color.toColor().copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (isActive) account.color.toColor().copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { selected = isActive }
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccountBadge(account)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isActive) Icon(Icons.Rounded.Check, contentDescription = "Es la que ves", tint = account.color.toColor())
                    IconButton(onClick = { editing = Editing(account) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Cambiar ${account.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { editing = Editing(null) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Nueva cuenta")
        }
    }

    editing?.let { target ->
        AccountDialog(
            existing = target.account,
            // La principal también se puede borrar si hay otra: lo que no se puede es quedarse sin ninguna
            canDelete = target.account != null && accounts.hasSeveral,
            suggestedColor = ACCOUNT_COLORS[accounts.all.size % ACCOUNT_COLORS.size],
            onSave = { name, color ->
                onSave(name, color, target.account?.id)
                editing = null
            },
            onDelete = {
                target.account?.let { onDelete(it.id) }
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

private data class Editing(val account: SavingsAccount?)

/** Nombre y color de una cuenta; al cambiar una que no es la única, también se puede borrar (con confirmación). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountDialog(
    existing: SavingsAccount?,
    canDelete: Boolean,
    suggestedColor: Color,
    onSave: (String, ArgbColor) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var color by remember { mutableStateOf(existing?.color?.toColor() ?: suggestedColor) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar \"${existing.name}\"?") },
            text = { Text("Se borrarán también todos sus ingresos, gastos y presupuestos. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nueva cuenta" else "Cambiar cuenta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_ACCOUNT_NAME_LENGTH) },
                    label = { Text("Nombre") },
                    placeholder = { Text("Conjunta, Vacaciones…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ACCOUNT_COLORS.forEach { option ->
                        val selected = option == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(option)
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .semantics { this.selected = selected }
                        ) {
                            Surface(onClick = { color = option }, color = Color.Transparent, modifier = Modifier.fillMaxSize()) {}
                            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
                if (canDelete) {
                    TextButton(onClick = { confirmDelete = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("Eliminar cuenta", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, color.toArgbColor()) }, enabled = name.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
