package com.syncro.presentation.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/*
 * El permiso de notificaciones se pide desde dos sitios (el chat del asistente y Ajustes); aquí está
 * lo que comparten para que se comporten igual.
 */

/** Si Android deja publicar avisos: permiso concedido (Android 13+) y notificaciones sin silenciar. */
fun Context.notificationsAllowed(): Boolean {
    val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return permissionGranted && NotificationManagerCompat.from(this).areNotificationsEnabled()
}

/** Los ajustes de notificaciones de la app en el sistema (ahí se quitan o se reactivan). */
fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/*
 * Android no dice si el permiso está "denegado para siempre", pero se deduce: si ya se preguntó y
 * Android no quiere que se explique el motivo (shouldShowRequestPermissionRationale), el diálogo ya
 * no volverá a salir. Antes de la primera vez tampoco se explica, por eso hay que recordar que ya se
 * preguntó.
 */
private const val PERMISSION_PREFS = "permission_prefs"
private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"

/** Llamar justo antes de lanzar el diálogo del permiso. */
fun Context.markNotificationPermissionAsked() {
    getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
}

/** Si Android aún mostrará el diálogo del permiso; si no, solo se puede activar en los ajustes. */
fun Context.canAskNotificationPermission(): Boolean {
    val askedBefore = getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED_NOTIFICATIONS, false)
    val activity = findActivity() ?: return true
    return !askedBefore ||
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
