package com.example.radiolyric.di

import com.example.radiolyric.data.radio.RadioSource
import com.example.radiolyric.data.radio.RealRadioSourceProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Shared binding: whatever the active source set binds to [RealRadioSourceProvider] is exposed
 * as the application's [RadioSource].
 *
 * The concrete `Provider -> Implementation` binding lives in:
 * - `app/src/debug/.../di/DebugRadioBindings.kt` — chooser between `OmriUsbRadioSource` (default)
 *   and `FakeRadioSource`, selected at build time via the `radio.source` Gradle property
 *   (`BuildConfig.RADIO_SOURCE`).
 * - `app/src/release/.../di/ReleaseRadioBindings.kt` — `OmriUsbRadioSource`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RadioModule {

    @Binds @Singleton abstract fun bindRadioSource(impl: RealRadioSourceProvider): RadioSource
}
