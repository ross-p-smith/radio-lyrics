package com.example.radiolyric.di

import com.example.radiolyric.data.radio.FakeRadioSource
import com.example.radiolyric.data.radio.RealRadioSourceProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Debug-only binding — wires [FakeRadioSource] behind the shared [RealRadioSourceProvider] hook.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugRadioBindings {

    @Binds
    @Singleton
    abstract fun bindFakeRadioSource(impl: FakeRadioSource): RealRadioSourceProvider
}
