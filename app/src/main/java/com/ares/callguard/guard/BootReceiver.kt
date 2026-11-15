package com.ares.callguard.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Brings protection back after a reboot or an app update, if it was ACTIVE before. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var guard: Guard

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESTORE_ACTIONS) return
        // Creating the guard is what restarts protection; the recheck covers settings that
        // changed while the phone was off.
        guard.recheckSetup()
    }

    private companion object {
        val RESTORE_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
