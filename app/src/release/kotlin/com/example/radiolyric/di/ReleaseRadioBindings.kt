package com.example.radiolyric.di

import com.example.radiolyric.BuildConfig
import com.example.radiolyric.devtools.AppLog as Log
import com.example.radiolyric.data.radio.DabzBridgeRadioSource
import com.example.radiolyric.data.radio.OmriUsbRadioSource
import com.example.radiolyric.data.radio.RealRadioSourceProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Release-flavor binding chooser. Mirrors the debug chooser minus the `fake` arm:
 * - default / `real` → [OmriUsbRadioSource] (USB DAB tuner via the vendored `omri-usb` driver)
 * - `dabz` → [DabzBridgeRadioSource] (consume DAB-Z's `MediaBrowserService`)
 *
 * Selection is keyed on `BuildConfig.RADIO_SOURCE` which is set from the `radio.source` Gradle
 * property. WI-06 tracks whether release should ever expose `fake`.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReleaseRadioBindings {

    @Provides
    @Singleton
    fun provideRadioSourceProvider(
            omriUsbProvider: Provider<OmriUsbRadioSource>,
            dabzProvider: Provider<DabzBridgeRadioSource>,
    ): RealRadioSourceProvider {
        Log.i("RadioBindings", "radio source = ${BuildConfig.RADIO_SOURCE}")
        return when (BuildConfig.RADIO_SOURCE) {
            "dabz" -> dabzProvider.get()
            else -> omriUsbProvider.get()
        }
    }
}
