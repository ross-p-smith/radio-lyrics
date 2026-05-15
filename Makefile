# radio-lyric developer commands. All targets honour DEVICE_IP (default 192.168.1.54).
DEVICE_IP ?= 192.168.1.54
DEVICE     := $(DEVICE_IP):5555
PKG        := com.example.radiolyric.debug
ADB        := adb
GRADLEW    := ./gradlew

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help.
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "} {printf "  %-18s %s\n", $$1, $$2}'

.PHONY: connect
connect: ## adb connect to DEVICE_IP (default 192.168.1.54).
	$(ADB) connect $(DEVICE)
	$(ADB) devices

.PHONY: build
build: ## Assemble the debug APK.
	$(GRADLEW) :app:assembleDebug

.PHONY: install
install: connect ## Install the debug APK on the connected device (arm64).
	$(GRADLEW) :app:installDebug -Pandroid.injected.build.abi=arm64-v8a

.PHONY: install-fake
install-fake: connect ## Install the debug APK with the fake radio source.
	$(GRADLEW) :app:installDebug -Pradio.source=fake -Pandroid.injected.build.abi=arm64-v8a

.PHONY: install-dabz
install-dabz: connect ## Install the debug APK in DAB-Z bridge mode (consumes com.zoulou.dab).
	$(GRADLEW) :app:installDebug -Pradio.source=dabz -Pandroid.injected.build.abi=arm64-v8a

.PHONY: run
run: install ## Install + launch the app.
	$(ADB) shell monkey -p $(PKG) -c android.intent.category.LAUNCHER 1

.PHONY: stop
stop: ## Force-stop our app on the device.
	$(ADB) shell am force-stop $(PKG)

.PHONY: stop-dabz
stop-dabz: ## Force-stop DAB-Z so it releases the USB dongle.
	$(ADB) shell am force-stop com.zoulou.dab

.PHONY: logs
logs: ## Tail filtered logcat for our app + omri-usb subsystems.
	$(ADB) logcat -v time | grep --line-buffered -iE 'omri|UsbHelper|TunerUsbImpl|RaonTunerInput|RadioPlayer|OmriUsb|DabEnsemble|LockStat|tunerScanFinished|TunerStatus|ForegroundService|PlaybackService|radiolyric'

.PHONY: logs-dabz
logs-dabz: ## Tail logcat filtered to the DAB-Z bridge tags.
	$(ADB) logcat -v time -s DabzMediaBrowserClient:* DabzBridgeRadioSource:* DabzMediaListener:* RadioBindings:* PlaybackService:*

.PHONY: verify-dabz
verify-dabz: connect ## Run Phase 5.1 on-device verification (media_session + DabMediaBrowserService + NLS).
	@echo "--- DAB-Z media_session entry ---"
	$(ADB) shell dumpsys media_session | sed -n '/com.zoulou.dab/,/^$$/p'
	@echo "--- DabMediaBrowserService export ---"
	$(ADB) shell dumpsys package com.zoulou.dab | grep -A3 -iE 'mediabrowser|exported=true' || true
	@echo "--- enabled NotificationListeners ---"
	$(ADB) shell settings get secure enabled_notification_listeners
	@echo "--- our requestAudioFocus calls (should be empty) ---"
	$(ADB) shell dumpsys audio | grep $(PKG) || echo "(none — read-only consumer OK)"

.PHONY: logs-clear
logs-clear: ## Clear the device logcat buffer.
	$(ADB) logcat -c

.PHONY: capture
capture: ## Capture 60 s of filtered logcat into /tmp/radio-lyric-<ts>.log.
	@LOGFILE=/tmp/radio-lyric-$$(date +%Y%m%d-%H%M%S).log; \
	  echo "Capturing to $$LOGFILE"; \
	  timeout 60 $(ADB) logcat -v time | tee "$$LOGFILE" >/dev/null; \
	  echo "Saved $$(wc -l <"$$LOGFILE") lines."

.PHONY: test
test: ## Run JVM unit tests.
	$(GRADLEW) :app:testDebugUnitTest

.PHONY: lint
lint: ## Run Android lint on debug.
	$(GRADLEW) :app:lintDebug

.PHONY: clean
clean: ## Gradle clean.
	$(GRADLEW) clean

.PHONY: lyrics-seed-refresh
lyrics-seed-refresh: ## Refresh prewarmed lyrics seed from Heart last played songs.
	bash ./update_lyrics.sh

.PHONY: lyrics-seed-validate
lyrics-seed-validate: ## Validate seed JSONL structure and print sample entries.
	@FILE=app/src/main/assets/lyrics/seed_lyrics_cache.jsonl; \
	echo "Line count:"; wc -l $$FILE; \
	echo "Validation:"; jq -e '.artist and .title and .provider' $$FILE >/dev/null && echo "OK"; \
	echo "Sample tracks:"; head -n 10 $$FILE | jq -r '"- \(.artist) | \(.title)"'
