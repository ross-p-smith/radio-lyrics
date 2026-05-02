package com.example.radiolyric.data.radio

/**
 * Hard-coded UK Digital One (D1) ensemble stations seeded on first run.
 *
 * - SId / EId values per UK Spectrum & DAB ensemble allocations (see research §A.1).
 * - Block 11D centre frequency = 222.064 MHz (= 222_064 kHz).
 * - Heart UK is the default tile per project requirements.
 */
object Stations {
    val HeartUK =
            Station(sid = 0xCFD1, eid = 0xC18C, frequencyKhz = 222_064, label = "Heart UK")

    val all: List<Station> = listOf(HeartUK)
}
