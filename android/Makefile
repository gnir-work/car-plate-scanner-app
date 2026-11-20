# Dev shortcuts for the Android app. Run `make` to list targets.

ANDROID_HOME ?= $(HOME)/Library/Android/sdk
ADB          := $(ANDROID_HOME)/platform-tools/adb
EMULATOR     := $(ANDROID_HOME)/emulator/emulator
APP_ID       := com.lpscanner.app

PHOTOS_DIR   := ../resources
DEVICE_DIR   := /sdcard/Download
# First AVD by default; override with `make emulator AVD=Pixel_8_API_35`.
AVD          ?= $(shell $(EMULATOR) -list-avds 2>/dev/null | head -1)

.DEFAULT_GOAL := help
.PHONY: help sync-photos emulator test test-ocr install run apk logcat clean require-device

help: ## List available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

sync-photos: require-device ## Copy every image in ../resources to the emulator's Downloads and index it
	@count=0; \
	for f in $(PHOTOS_DIR)/*.jpg $(PHOTOS_DIR)/*.jpeg $(PHOTOS_DIR)/*.png $(PHOTOS_DIR)/*.webp $(PHOTOS_DIR)/*.heic \
	         $(PHOTOS_DIR)/*.JPG $(PHOTOS_DIR)/*.JPEG $(PHOTOS_DIR)/*.PNG $(PHOTOS_DIR)/*.HEIC; do \
		[ -e "$$f" ] || continue; \
		$(ADB) push "$$f" $(DEVICE_DIR)/ >/dev/null 2>&1 && echo "  pushed $$(basename "$$f")" && count=$$((count + 1)); \
	done; \
	echo "Indexing media so the photo picker sees new files..."; \
	$(ADB) shell content call --method scan_volume --uri content://media --arg external_primary >/dev/null; \
	echo "Synced $$count photo(s) to $(DEVICE_DIR)."

emulator: ## Start the emulator (AVD=<name> to pick one) and wait until it has booted
	@if $(ADB) get-state >/dev/null 2>&1; then echo "A device is already connected."; exit 0; fi; \
	[ -n "$(AVD)" ] || { echo "No AVD found. Create one in Android Studio's Device Manager."; exit 1; }; \
	echo "Starting $(AVD)..."; \
	nohup $(EMULATOR) -avd "$(AVD)" >/dev/null 2>&1 & \
	$(ADB) wait-for-device; \
	until [ "$$($(ADB) shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done; \
	echo "Emulator ready."

test: ## Run JVM unit tests (plate heuristic, no emulator needed)
	./gradlew test

test-ocr: require-device ## Run real ML Kit OCR tests on ../resources photos (needs emulator)
	./gradlew connectedDebugAndroidTest

install: require-device ## Build and install the debug app
	./gradlew installDebug

run: install ## Build, install and launch the app
	$(ADB) shell am start -n $(APP_ID)/.MainActivity

APK          := build/PlateScanner.apk

apk: ## Build an installable APK for a phone (build/PlateScanner.apk)
	./gradlew assembleRelease
	@mkdir -p build && cp app/build/outputs/apk/release/app-release.apk $(APK)
	@echo "Ready: $$(pwd)/$(APK)"

logcat: require-device ## Stream the running app's logs
	@pid=$$($(ADB) shell pidof -s $(APP_ID) | tr -d '\r'); \
	[ -n "$$pid" ] || { echo "App isn't running. Try 'make run' first."; exit 1; }; \
	$(ADB) logcat --pid=$$pid

clean: ## Delete build outputs
	./gradlew clean

require-device:
	@$(ADB) get-state >/dev/null 2>&1 || { echo "No emulator/device connected. Run 'make emulator' first."; exit 1; }
