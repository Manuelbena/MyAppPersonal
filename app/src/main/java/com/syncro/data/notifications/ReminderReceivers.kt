package com.syncro.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.syncro.domain.model.dueBetween
import com.syncro.domain.repository.TaskRepository
import com.syncro.domain.usecase.ObserveRemindersUseCase
import com.syncro.domain.usecase.ToggleTaskCompletionUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime

// Acceso a Hilt con EntryPoint, como el resumen diario
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun observeReminders(): ObserveRemindersUseCase
    fun reminderNotifier(): ReminderNotifier
    fun reminderScheduler(): ReminderAlarmScheduler
    fun toggleTaskCompletion(): ToggleTaskCompletionUseCase
    fun taskRepository(): TaskRepository
    fun clock(): Clock
}

internal fun Context.reminderEntryPoint(): ReminderEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("Reminders", "Fallo en un aviso", e)
        } finally {
            pendingResult.finish()
        }
    }
}

/**
 * Suena a la hora del siguiente aviso: publica los que tocan desde la última vez (si dos avisos
 * coinciden, salen los dos) y programa el siguiente. También recibe los pospuestos.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val entryPoint = context.reminderEntryPoint()
        runAsync {
            val reminders = entryPoint.observeReminders()().first()
            val now = LocalDateTime.now(entryPoint.clock())
            when (intent.action) {
                ACTION_SNOOZED -> {
                    // Solo si sigue pendiente: si se completó o se borró mientras tanto, nada
                    val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return@runAsync
                    reminders.firstOrNull { it.itemId == itemId }?.let { entryPoint.reminderNotifier().show(it) }
                }
                ACTION_REMINDER -> {
                    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val clock = entryPoint.clock()
                    val since = prefs.getLong(KEY_LAST_CHECK, -1L).takeIf { it > 0 }
                        ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), clock.zone) }
                        ?: now.minusMinutes(1)
                    // Primero se apunta la comprobación y la siguiente alarma: si falla al publicar, la cadena no se corta
                    prefs.edit().putLong(KEY_LAST_CHECK, clock.millis()).apply()
                    entryPoint.reminderScheduler().scheduleNext(reminders)
                    reminders.dueBetween(since, now).forEach { entryPoint.reminderNotifier().show(it) }
                }
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.syncro.action.ITEM_REMINDER"
        const val ACTION_SNOOZED = "com.syncro.action.ITEM_REMINDER_SNOOZED"
        const val EXTRA_ITEM_ID = "item_id"
        private const val PREFS = "reminder_prefs"
        private const val KEY_LAST_CHECK = "last_check"
    }
}

/** Los botones de la notificación: "Posponer 10 min" y, en las tareas, "Hecha". */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(ReminderAlarmReceiver.EXTRA_ITEM_ID) ?: return
        val entryPoint = context.reminderEntryPoint()
        entryPoint.reminderNotifier().cancel(itemId)
        when (intent.action) {
            ACTION_SNOOZE -> entryPoint.reminderScheduler().snooze(itemId, SNOOZE_MINUTES)
            ACTION_DONE -> runAsync {
                // Solo si sigue sin hacer: marcar "Hecha" dos veces no debe volver a dejarla pendiente
                val task = entryPoint.taskRepository().getTaskById(itemId)
                if (task != null && !task.isCompleted) entryPoint.toggleTaskCompletion()(itemId)
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.syncro.action.ITEM_REMINDER_SNOOZE"
        const val ACTION_DONE = "com.syncro.action.ITEM_REMINDER_DONE"
        const val SNOOZE_MINUTES = 10L
    }
}
