package com.example.radiolyric.di

import com.example.radiolyric.data.radio.OmriUsbRadioSource
import com.example.radiolyric.data.radio.RealRadioSourceProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Debug binding — temporarily wired to the real [OmriUsbRadioSource] so on-device hardware
 * iteration can use the fast `installDebug` cycle (no R8/resource-shrink). Swap back to
 * `FakeRadioSource` when working without the USB tuner attached.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugRadioBindings {

    @Binds
    @Singleton
    abstract fun bindRealRadioSource(impl: OmriUsbRadioSource): RealRadioSourceProvider
}
