package com.example.radiolyric.data.radio

/**
 * A tunable DAB radio service.
 *
 * @param sid Service Identifier (16-bit, e.g. `0xCFD1` for Heart UK).
 * @param eid Ensemble Identifier (16-bit, e.g. `0xC18C` for Digital One).
 * @param frequencyKhz Centre frequency of the ensemble in kHz (e.g. `222_064` for D1 / 11D).
 * @param label Human-readable station label.
 */
data class Station(
        val sid: Int,
        val eid: Int,
        val frequencyKhz: Int,
        val label: String,
)
