package com.example.radiolyric.di

import com.example.radiolyric.BuildConfig
import com.example.radiolyric.data.radio.DabzBridgeRadioSource
import com.example.radiolyric.data.radio.FakeRadioSource
import com.example.radiolyric.data.radio.OmriUsbRadioSource
import com.example.radiolyric.data.radio.RealRadioSourceProvider
import com.example.radiolyric.devtools.AppLog as Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Debug binding chooser — picks between [OmriUsbRadioSource] (real USB DAB tuner),
 * [FakeRadioSource] (scripted-fixture playback), and [DabzBridgeRadioSource] (consume DAB-Z's
 * MediaBrowserService) at build time based on the `radio.source` Gradle property surfaced via
 * `BuildConfig.RADIO_SOURCE`.
 *
 * Default is `real` so the fast `installDebug` cycle uses the actual tuner. Pass
 * `-Pradio.source=fake` for a hardware-free build, or `-Pradio.source=dabz` to consume DAB-Z. The
 * unused sides of the `when` are never instantiated thanks to `javax.inject.Provider`.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugRadioBindings {

    @Provides
    @Singleton
    fun provideRadioSourceProvider(
            omriUsbProvider: Provider<OmriUsbRadioSource>,
            fakeProvider: Provider<FakeRadioSource>,
            dabzProvider: Provider<DabzBridgeRadioSource>,
    ): RealRadioSourceProvider {
        Log.i("RadioBindings", "radio source = ${BuildConfig.RADIO_SOURCE}")
        return when (BuildConfig.RADIO_SOURCE) {
            "fake" -> fakeProvider.get()
            "dabz" -> dabzProvider.get()
            else -> omriUsbProvider.get()
        }
    }
}
