package com.ares.callguard.ui.setup

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.ActivityCompat
import com.ares.callguard.detection.CallNotificationListener
import com.ares.callguard.domain.model.SetupStep
import com.ares.callguard.guard.PermissionOrchestrator

/**
 * Returns a function that takes the user straight to the place where [SetupStep] is granted:
 * the system permission dialog where one exists, otherwise the exact settings page.
 */
@Composable
fun rememberSetupLauncher(onReturned: () -> Unit): (SetupStep) -> Unit {
    val activity = LocalActivity.current
    val returned = rememberUpdatedState(onReturned)

    val settingsPage = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { returned.value() }

    val permissionDialog = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val refused = results.filterValues { granted -> !granted }.keys
        // Once Android stops showing the dialog, the app's own settings page is the only way in.
        val dialogNoLongerShown = activity != null && refused.any {
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        if (dialogNoLongerShown) settingsPage.launch(appSettings(activity!!)) else returned.value()
    }

    return remember(activity) {
        launcher@{ step: SetupStep ->
            val context = activity ?: return@launcher
            when (step) {
                SetupStep.SHOW_NOTIFICATIONS ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionDialog.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    } else {
                        settingsPage.launchSafely(notificationSettings(context), context)
                    }

                SetupStep.SEE_PHONE_CALLS ->
                    permissionDialog.launch(PermissionOrchestrator.PHONE_PERMISSIONS.toTypedArray())

                SetupStep.SEE_APP_CALLS -> settingsPage.launchSafely(notificationAccess(context), context)
                SetupStep.FULL_SCREEN_ALERTS -> settingsPage.launchSafely(fullScreenAlerts(context), context)
                SetupStep.KEEP_AWAKE -> settingsPage.launchSafely(batteryExemption(context), context)
                SetupStep.DO_NOT_DISTURB_ACCESS ->
                    settingsPage.launchSafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), context)
            }
        }
    }
}

@Composable
fun rememberAppSettingsOpener(): () -> Unit {
    val activity = LocalActivity.current
    return remember(activity) {
        { if (activity != null) activity.startActivity(appSettings(activity)) }
    }
}

// Phone makers sometimes remove individual settings pages; the app's own page always exists.
private fun ActivityResultLauncher<Intent>.launchSafely(
    intent: Intent,
    context: Activity,
) {
    runCatching { launch(intent) }.onFailure { runCatching { launch(appSettings(context)) } }
}

private fun packageUri(context: Context) = Uri.fromParts("package", context.packageName, null)

private fun appSettings(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

private fun notificationSettings(context: Context) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun notificationAccess(context: Context) =
    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
        ComponentName(context, CallNotificationListener::class.java).flattenToString(),
    )

private fun fullScreenAlerts(context: Context) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context))
    } else {
        appSettings(context)
    }

@Suppress("BatteryLife") // Staying alive to catch a call is the whole purpose of the app.
private fun batteryExemption(context: Context) =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
