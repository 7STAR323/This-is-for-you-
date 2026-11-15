package com.ares.callguard.guard

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ares.callguard.domain.model.SetupStep
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Works out which phone settings still stand between ARES and working protection. */
class SetupChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun missingSteps(): List<SetupStep> = SetupStep.entries.filterNot(::isDone)

    private fun isDone(step: SetupStep): Boolean = when (step) {
        SetupStep.SHOW_NOTIFICATIONS ->
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        SetupStep.SEE_PHONE_CALLS -> PHONE_PERMISSIONS.all(::isGranted)

        SetupStep.SEE_APP_CALLS ->
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        SetupStep.FULL_SCREEN_ALERTS ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

        SetupStep.KEEP_AWAKE ->
            context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        // The call log is what reveals the caller's number, and later whether a call that
        // stopped ringing was declined or missed.
        val PHONE_PERMISSIONS = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
        )
    }
}
