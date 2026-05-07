package com.example.radiolyric.di

import com.example.radiolyric.BuildConfig
import com.example.radiolyric.devtools.AppLog as Log
import com.example.radiolyric.data.radio.FakeRadioSource
import com.example.radiolyric.data.radio.OmriUsbRadioSource
import com.example.radiolyric.data.radio.RealRadioSourceProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Debug binding chooser — picks between [OmriUsbRadioSource] (real USB DAB tuner) and
 * [FakeRadioSource] (scripted-fixture playback) at build time based on the `radio.source`
 * Gradle property surfaced via `BuildConfig.RADIO_SOURCE`.
 *
 * Default is `real` so the fast `installDebug` cycle uses the actual tuner. Pass
 * `-Pradio.source=fake` to switch to the fake without editing source. The unused side of the
 * `when` is never instantiated thanks to `javax.inject.Provider`.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugRadioBindings {

    @Provides
    @Singleton
    fun provideRadioSourceProvider(
            omriUsbProvider: Provider<OmriUsbRadioSource>,
            fakeProvider: Provider<FakeRadioSource>,
    ): RealRadioSourceProvider {
        Log.i("RadioBindings", "radio source = ${BuildConfig.RADIO_SOURCE}")
        return when (BuildConfig.RADIO_SOURCE) {
            "fake" -> fakeProvider.get()
            else -> omriUsbProvider.get()
        }
    }
}
