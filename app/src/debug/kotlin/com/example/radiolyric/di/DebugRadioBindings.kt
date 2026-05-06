package com.example.radiolyric.di

import com.example.radiolyric.data.radio.FakeRadioSource
import com.example.radiolyric.data.radio.RealRadioSourceProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Debug binding — wires [FakeRadioSource] behind the shared [RealRadioSourceProvider] hook so
 * debug builds run end-to-end without USB hardware attached. Temporarily swap to
 * `OmriUsbRadioSource` for on-device verification only.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugRadioBindings {

    @Binds
    @Singleton
    abstract fun bindFakeRadioSource(impl: FakeRadioSource): RealRadioSourceProvider
}
