package com.example.radiolyric.di

import com.example.radiolyric.data.radio.OmriUsbRadioSource
import com.example.radiolyric.data.radio.RealRadioSourceProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Release-flavor binding: real `OmriUsbRadioSource` driving the Mekede USB tuner. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReleaseRadioBindings {

    @Binds
    @Singleton
    abstract fun bindOmriUsbRadioSource(impl: OmriUsbRadioSource): RealRadioSourceProvider
}
