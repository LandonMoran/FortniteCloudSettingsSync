# Changelog

All notable changes to this project are documented here.

## [1.0.0] - 2026-06-25

First release of the **Android app**. It brings the desktop tool's Epic Games
cloud-storage features to your phone, reusing the same Python backend so the
auth and cloud logic stays identical to the desktop script.

### Added
- **Android app** built with Jetpack Compose, embedding the Python backend via
  Chaquopy (the desktop auth/cloud-storage code runs unchanged on-device).
- **In-app Epic Games sign-in** — log in inside the app with a built-in browser
  (supports Epic, Xbox, and 2FA); the authorization code is captured
  automatically, with no copy/paste. A manual paste flow remains as a fallback.
- **Cloud file management** — list your Epic cloud storage files, with optional
  filtering of restricted/platform files (Switch, UUID-named saves).
- **Downloads** — a per-file download button (one tap grabs just that file) plus
  "Download All". Files are saved to the public **`Downloads/FortniteCloudSync/`**
  folder so they're visible and shareable in the Files app. Re-downloads replace
  rather than duplicate.
- **Uploads & delete** — upload one or more files (replacing same-named cloud
  files) and delete cloud files with confirmation.
- **Status log** with a copy-to-clipboard button on every screen for easy
  troubleshooting.

### Build & distribution
- Every build is signed with one shared key, so updates install in place with no
  uninstall/reinstall, and a future store release can update over sideloaded
  builds.
- CI publishes a debug APK to a rolling `android-latest` GitHub Release for
  direct, no-zip installation; tagged `v*` releases publish versioned APKs.

### Notes
- Targets `arm64-v8a` devices.
- `.sav` files are encrypted by Epic; the app downloads/uploads them intact but
  cannot read their contents (only Fortnite can apply them).
