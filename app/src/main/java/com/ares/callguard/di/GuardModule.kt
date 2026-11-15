package com.ares.callguard.di

import com.ares.callguard.alert.AlertDisplay
import com.ares.callguard.alert.AlertNotifier
import com.ares.callguard.alert.AlertPlayer
import com.ares.callguard.alert.AndroidAlertPlayer
import com.ares.callguard.guard.AndroidPhoneSoundState
import com.ares.callguard.guard.CallSessionManager
import com.ares.callguard.guard.PhoneSoundState
import com.ares.callguard.guard.PriorityCallSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GuardModule {
    @Binds
    abstract fun alertPlayer(player: AndroidAlertPlayer): AlertPlayer

    @Binds
    abstract fun phoneSoundState(state: AndroidPhoneSoundState): PhoneSoundState

    @Binds
    abstract fun alertDisplay(notifier: AlertNotifier): AlertDisplay

    @Binds
    abstract fun priorityCallSink(sessions: CallSessionManager): PriorityCallSink
}
