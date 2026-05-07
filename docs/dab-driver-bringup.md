# DAB Driver Bring-Up — yonghx 16C0:05DC on the Mekede DUDU7

How the vendored `omri-usb` driver was made to lock and start scanning the
yonghx `0x16C0:0x05DC` DAB+ dongle on the Mekede DUDU7 head unit, and why
the original "doesn't work" verdict was wrong. Captures three commits on top
of `hradio/omri-usb@12f4b8e` so the diagnosis isn't re-walked next time.

## 1. The original symptom

When the bring-up plan was first attempted on 2026-05-05, the verdict was
"`omri-usb` doesn't drive this dongle". The `OmriUsbRadioSource` threw

```
Tuner did not reach INITIALIZED within 10000ms
```

at `OmriUsbRadioSource.kt:217`, and DAB-Z (the OEM app `com.zoulou.dab`) was
the only thing on the device that could play DAB. The path of least
resistance looked like reverse-engineering DAB-Z.

That verdict was wrong on two counts: the bind site **did** match the
dongle, and the failure was not "no bind" but "PowerUp failed mid-init".

## 2. Diagnosis — why the original verdict was wrong

`omri-usb/omriusb/src/main/java/org/omri/radio/impl/RadioImpl.java:170`
already registers `0x16C0:0x05DC`, logs `"Found Siano device!"`, and
instantiates `TunerUsbImpl`. The bind site was always there. What was
missing was an audit of where in the init sequence the driver was stopping.

Phase 2B of the 2026-05-06 bring-up plan captured logcat with the per-class
filters in place and traced the init to `RaonTunerInput::setRegister`. The
sequence was:

1. `tunerPowerUp()` → calls `setRegister()` once.
2. `setRegister()` writes a configuration byte over the OUT endpoint.
3. The dongle replies with a one-byte `0xA1` acknowledge on the IN
   endpoint.
4. Our `setRegister()` did **not** drain that ack byte.
5. The next `readRegister()` returned the stale `0xA1` instead of the
   register value, so every subsequent decision was off-by-one.
6. `PowerUp failed!` and the tuner never reached `TUNER_STATUS_INITIALIZED`.

The reference implementation in `flohoff/wradio-c100` (a sibling DAB driver
fork) drained the ack byte explicitly. That was the missing piece.

## 3. Fix #1 — drain the setRegister ack byte (commit `2470c04`)

Cherry-picked the 10-line ack-drain from `flohoff/wradio-c100` into our
`omri-usb` fork.

* **File**: `omri-usb/omriusb/src/main/cpp/platformspecific/android/raontunerinput.cpp`
* **Function**: `RaonTunerInput::setRegister` (~line 432)
* **Diff size**: `+10 lines, 0 deletions`
* **Mechanism**: After the existing
  `writeBulkTransferData(RAON_ENDPOINT_OUT, setRegData)`, issue a
  one-byte `readBulkTransferData(RAON_ENDPOINT_IN, ackBuffer)` to drain the
  per-write `0xA1` acknowledge.

### Effect (verbatim from Phase 2B logcat, 2026-05-06)

```
05-07 22:30:37.xxx I/std [RaonUsbTuner]  PowerUp okay!
05-07 22:30:37.xxx I/std [RaonUsbTuner] Setting up MSC Threshold...
05-07 22:30:37.xxx I/std [RaonUsbTuner] Setting up FIC Memory...
05-07 22:30:37.xxx D/TunerUsb  getting Services at TunerStatus: TUNER_STATUS_INITIALIZED
05-07 22:30:37.xxx I/std [RaonUsbTuner] Starting service scan!
... 11 ensemble transitions across 174.928 - 194.064 MHz ...
```

Phase 2B's tie-break analysis had predicted the cherry-pick would also need
to cover `tdmbInitFEC`. In practice, `tdmbInitFEC` is byte-identical between
us and `flohoff/wradio-c100`; the only behavioural difference along
`tunerPowerUp → setRegister → readRegister` was the ack drain. Ten lines
took the driver from "PowerUp failed!" all the way to "Starting service
scan!".

## 4. Fix #2 — direct-bulk-transfer fast path (commit `0e4518e`)

DAB-Z owns the dongle reliably because it uses a direct `USBDEVFS_BULK`
ioctl path rather than the slower `UsbDeviceConnection.bulkTransfer()` JNI
round-trip. The FIC reader on the wRadio C100 needs that throughput to
keep up with the dongle long enough for `LockStat: 1` to assert during a
real ensemble lock.

We reconstructed the same fast path inside our `omri-usb` fork.

### What landed

| File                                                                                            | Change                                                                                          |
| ----------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `omri-usb/omriusb/src/main/cpp/platformspecific/android/jusbdevice.h`                           | Class declaration: adds `m_directBulkEnabled`, cached `m_fd`, ioctl method declarations, public setter. |
| `omri-usb/omriusb/src/main/cpp/platformspecific/android/jusbdevice.cpp`                         | Class definition: binds `getFileDescriptor()` method ID, caches FD in `permissionGranted`, adds ioctl read/write variants, branches in existing read/write methods. |
| `omri-usb/omriusb/src/main/cpp/platformspecific/android/native-lib.cpp`                         | JNI registry: adds `Java_org_omri_radio_impl_UsbHelper_setDirectBulkTransferEnabled` shim.       |
| `omri-usb/omriusb/src/main/java/org/omri/radio/impl/UsbHelper.java`                             | Java side: declares `private native void setDirectBulkTransferEnabled` + `setDirectBulkTransferModeEnabled` package-private wrapper. |
| `omri-usb/omriusb/src/main/java/org/omri/radio/impl/TunerUsbImpl.java`                          | Calls the wrapper before `initializeTuner()` so the fast path is live for the very first FIC poll. |

### Default state

The toggle is **on by default** before `initializeTuner()`. DAB-Z caches the
JNI method IDs and FD on first transfer and cannot switch paths after that;
we mirror that ordering. The shape of the toggle matches DAB-Z's
`alternativeUsbComm` SharedPreference so a future Settings UI exposure
(WI-DBT-01 in `docs/project-status.md` §4) is a one-screen wiring task.

## 5. Fix #3 — IRT IP/Shoutcast subsystem removal (commits `d103ebc` + `899799d`)

The vendored `omri-usb` carried a closed-source IRT IP/Shoutcast subsystem
that this app does not consume. Leaving it in the AAR meant the public
surface advertised capabilities the app did not deliver, and the dead code
made future audits harder. The removal pass:

* Deleted six implementation files (`TunerIpShoutcast` and friends) in
  commit `d103ebc`.
* Cleaned up the residue in commit `899799d`: 3 public enum constants
  (`TUNER_TYPE_IP_SHOUTCAST`, `RADIOSERVICE_TYPE_IP`,
  `METADATA_TEXTUAL_TYPE_ICY_TEXT`), 5 orphan public types
  (`RadioServiceIp`, `RadioServiceIpStream`, `TextualIpIcy`,
  `RadioServiceIpStreamImpl`, `TextualIpIcyImpl`), 2 dead code sites
  (a `case RADIOSERVICE_TYPE_IP` switch arm in `RadioImpl.java`, the
  `EnrichServicesData` AsyncTask in `TunerUsbImpl.java`), 9 stale
  migration-breadcrumb comments, 2 unused fields
  (`SERVICES_JSON_IP`, `mFirstInitIp`), 2 unused imports, and 2 misleading
  README claims.

### What's preserved

The `INTERNET` permission and the `TunerEdistream` HTTP path remain — both
are load-bearing for EDI streams, which are a separate code path from
IP/Shoutcast and are still in scope for `omri-usb`.

## 6. Verification status

| Check                                                                                                                            | Status                                              |
| -------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| `./gradlew :omri-usb:compileDebugJavaWithJavac`                                                                                  | **BUILD SUCCESSFUL** (verified 2026-05-07)          |
| `./gradlew :app:assembleDebug`                                                                                                   | **BUILD SUCCESSFUL** (verified 2026-05-07)          |
| Permission grant + `openDevice` on DUDU7 (Android 13)                                                                            | **Verified** in upstream-PR research logcat         |
| `Found Siano device!` → `PowerUp okay!` → `Starting service scan!`                                                               | **Verified** by Phase 2B logcat (2026-05-06)        |
| 11 ensemble transitions across UK band-III plan                                                                                  | **Verified** by Phase 2B logcat (2026-05-06)        |
| `LockStat: 1` on at least one ensemble                                                                                           | **Pending** — needs DAB band-III RF coverage (HW-V4) |
| Live audio render (`pcmAudioData → OmriUsbRadioSource._audio → RadioPlayer → MediaSession`)                                      | **Pending** — gated on `LockStat: 1` (HW-V4)        |
| Direct-bulk-transfer fast path measurable latency reduction                                                                      | **Pending** — instrumentation deferred (WI-DBT-04)  |

## 7. Cross-references

* `.copilot-tracking/research/2026-05-06/omri-usb-wradio-c100-bringup-research.md` — full research feeding Fix #1.
* `.copilot-tracking/plans/logs/2026-05-06/omri-usb-wradio-c100-bringup-log.md` — Phase 2B outcome with the verbatim logcat block.
* `.copilot-tracking/research/2026-05-06/wradio-c100-direct-bulk-transfer-research.md` — research feeding Fix #2.
* `.copilot-tracking/plans/2026-05-06/wradio-c100-direct-bulk-transfer-plan.instructions.md` — implementation plan for Fix #2.
* `.copilot-tracking/research/2026-05-07/irt-ip-shoutcast-cleanup-research.md` — research feeding Fix #3 (full residue inventory).
* `.copilot-tracking/plans/2026-05-07/irt-ip-shoutcast-cleanup-plan.instructions.md` — implementation plan for Fix #3.
* `docs/project-status.md` — current overall status; lists hardware-blocked items HW-V3/HW-V4 referenced above.
* `docs/target-device-facts.md` — authoritative live-captured facts about the DUDU7 (Android 13, UNISOC UMS9620, SDK 33).
