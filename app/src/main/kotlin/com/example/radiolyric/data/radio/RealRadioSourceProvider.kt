package com.example.radiolyric.data.radio

/**
 * Marker interface used by the Hilt graph to bind a concrete [RadioSource] per build variant.
 *
 * - `debug` source set provides this via a chooser keyed on `BuildConfig.RADIO_SOURCE`
 *   (`OmriUsbRadioSource` by default, `FakeRadioSource` when `-Pradio.source=fake`).
 * - `release` source set binds `OmriUsbRadioSource`.
 *
 * Keeping the variant binding behind this marker lets the shared `RadioModule` in main do
 * `@Binds RealRadioSourceProvider -> RadioSource` exactly once, while each source set owns its
 * own concrete provider.
 */
interface RealRadioSourceProvider : RadioSource
