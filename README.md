# Fortnite Cloud Settings Sync — Android

An Android app to download, upload, and sync your Fortnite **`ClientSettings.sav`**
(and related) files directly to and from your Epic Games account's cloud storage.
Use it to back up your settings, share them with friends, or apply someone else's
settings to your own account before you next launch Fortnite.

It signs you in with Epic right inside the app and reuses the original project's
Python backend (embedded on-device with Chaquopy), so the authentication and
cloud-storage logic matches the desktop tool exactly.

## Features

- **In-app Epic sign-in** — log in with Epic, Xbox, or 2FA in a built-in browser;
  the authorization code is captured automatically (no copy/paste).
- **Browse your cloud saves** — list the files in your Epic cloud storage, with an
  option to hide restricted/platform files (Switch, UUID-named saves).
- **Download** — grab a single file with its own download button, or **Download All**.
  Files are saved to **`Downloads/FortniteCloudSync/`** so they're easy to find and
  share. Re-downloading replaces instead of duplicating.
- **Upload** — upload one or more files; same-named cloud files are replaced.
- **Delete** — remove a cloud file (with confirmation).
- **Status log** with a copy button on every screen for easy troubleshooting.

## Install

1. Go to the [**latest release**](https://github.com/LandonMoran/FortniteCloudSettingsSync/releases/latest).
2. Download the **`FortniteCloudSync-v…-release.apk`** asset (the smaller, optimized
   build — ~22 MB).
3. Open it on your phone and allow "install unknown apps" if prompted.

Notes:
- Updates install **in place** (all builds share one signing key) — no uninstall
  needed between versions.
- The app targets **arm64-v8a** devices (virtually all modern phones).

## Usage

1. Tap **Sign in with Epic Games**. Log in normally (Epic / Xbox / 2FA all work);
   the app captures your authorization code and signs you in automatically. If the
   in-app login ever gets stuck, a manual URL/JSON/code paste flow is available as a
   fallback.
2. Your cloud files load automatically. Toggle **Hide restricted files** to show or
   hide Switch/UUID saves.
3. **Download** a single file with its row's download icon, or tap **Download All**.
   Files land in **`Downloads/FortniteCloudSync/`** (open them in your Files app).
4. **Upload** files with the upload button (same-named cloud files are replaced), or
   select a file and **delete** it.

> If you have Fortnite open, using the app shouldn't log you out — but opening
> Fortnite while signed in here may require you to sign in again.

## Build from source

- Open the project in **Android Studio**, or run the **`Android CI`** GitHub Actions
  workflow.
- The build uses **Chaquopy**, which bundles **Python 3.12** and installs `requests`
  automatically from `app/src/main/python/requirements.txt`.

## How it works

The UI is built with **Jetpack Compose**. Rather than reimplement Epic's auth and
cloud-storage API in Kotlin, the app embeds the original project's **Python backend**
via **Chaquopy** and calls it through a small Kotlin bridge — so the networking logic
stays identical to the proven desktop script.

## Original project

This is a fork of **[GreenBeanGravy/FortniteCloudSettingsSync](https://github.com/GreenBeanGravy/FortniteCloudSettingsSync)**,
which provides the original **desktop** (Windows/Python) version of this tool. This
fork adds the Android app. See [`CHANGELOG.md`](CHANGELOG.md) for release history.

## Notes

Fortnite `.sav` files are **encrypted by Epic**. The app downloads and uploads them
intact, but their contents aren't human-readable — only Fortnite can read or apply
them.
