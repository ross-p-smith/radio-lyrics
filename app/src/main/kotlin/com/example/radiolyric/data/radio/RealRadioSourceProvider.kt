package com.example.radiolyric.data.radio

/**
 * Marker interface used by the Hilt graph to bind a concrete [RadioSource] per build flavor.
 *
 * - `debug` source set binds `FakeRadioSource` to this type.
 * - `release` source set binds `OmriUsbRadioSourceStub` (Phase 3) / `OmriUsbRadioSource` (Phase 4).
 *
 * Keeping the variant binding behind this marker lets the shared `RadioModule` in main do `@Binds
 * RealRadioSourceProvider -> RadioSource` exactly once, while each source set owns its own `@Binds
 * Concrete -> RealRadioSourceProvider`.
 */
interface RealRadioSourceProvider : RadioSource
