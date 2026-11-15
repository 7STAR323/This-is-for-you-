package com.ares.callguard.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.ares.callguard.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hears about SIM calls from the system. Being a manifest receiver, it is delivered even if
 * ColorOS has killed the app's process.
 */
@AndroidEntryPoint
class SimCallReceiver : BroadcastReceiver() {

    @Inject lateinit var tracker: SimCallTracker

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        // Deprecated, but still the only way a non-dialer app is told who is calling.
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val pending = goAsync()
        scope.launch {
            try {
                tracker.onPhoneState(state, number)
            } finally {
                pending.finish()
            }
        }
    }
}
