package com.example.radiolyric.autostart

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Verifies that every supported autostart broadcast is fanned to a service
 * start by [BootReceiver]. The intent-builder details (target component,
 * `EXTRA_TRIGGER`) cannot be asserted without Android framework code on the
 * unit-test classpath (Intent is stubbed) — those are exercised on-device by
 * the validation steps in the implementation plan (Phase 5.2).
 *
 * Live evidence (docs/target-device-facts.md §9): `BOOT_COMPLETED`,
 * `ACTION_POWER_CONNECTED`, and `com.nwd.action.ACTION_MCU_STATE_CHANGE` all
 * reach the receiver on the Mekede DUDU7. `SCREEN_ON` is the OEM-emitted ACC
 * heartbeat. The parameterised test ensures all four resolve to the same
 * service-start path.
 */
@RunWith(Parameterized::class)
class BootReceiverTest(private val action: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun actions(): List<String> =
                listOf(
                        Intent.ACTION_BOOT_COMPLETED,
                        Intent.ACTION_POWER_CONNECTED,
                        "com.nwd.action.ACTION_MCU_STATE_CHANGE",
                        Intent.ACTION_SCREEN_ON,
                )
    }

    @Test
    fun receiverStartsAServiceForEverySupportedAction() {
        val context = mockk<Context>(relaxed = true)
        val incoming = mockk<Intent>(relaxed = true)
        every { incoming.action } returns action

        BootReceiver().onReceive(context, incoming)

        // JVM unit tests run with `Build.VERSION.SDK_INT == 0`, so the
        // receiver takes the legacy `startService` branch. Either call
        // satisfies the contract that the receiver fans every supported
        // action to the service.
        verify(atLeast = 1) {
            context.startService(any())
        }
    }
}
