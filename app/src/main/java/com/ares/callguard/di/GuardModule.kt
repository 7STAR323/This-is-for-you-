package com.ares.callguard.di

import com.ares.callguard.alert.AlertPlayer
import com.ares.callguard.alert.AndroidAlertPlayer
import com.ares.callguard.guard.AndroidPhoneSoundState
import com.ares.callguard.guard.PhoneSoundState
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
}
