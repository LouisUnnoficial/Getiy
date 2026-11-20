# Changelog

All notable changes to **Getiy** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-11-19

### Added
- **Native On-Device Engine Stack:** Integrated `youtubedl-android` (`0.18.1`) with bundled `yt-dlp`, `FFmpeg`, and optional `Aria2c` multi-connection acceleration, plus live engine diagnostics and verification in Settings.
- **Full-Screen Onboarding & Midnight Navy Material 3 UI:** Clean full-screen onboarding flow, adaptive phone/tablet navigation, and custom adaptive launcher icon.
- **Real-Time Progress Indicators:** Material 3 `LinearProgressIndicator` feedback on the Home fetch card, bottom Fetch Command Dock, live active download banner, and Queue screen (with indeterminate connecting/muxing states and smooth determinate transfer progress).
- **Video, Audio, Trim & Playlist Workflows:** Support for 4K/MP4/MKV/WEBM video, MP3/M4A/Opus/FLAC audio extraction, inline clip trimming, playlist item picker, and ringtone maker.
- **System Integrations:** Android Share Sheet (`ACTION_SEND`) link capture, Quick Settings Tile, foreground Download Service, and built-in offline Audio Playback Service.
- **Public Release Package & Open-Source Docs:** Finalized package ID `com.getiy.tpxstudio`, `Theme.Getiy` splash/app theme, `GPL-3.0` `LICENSE`, `README.md` with screenshots, and hardened `.gitignore`.

### Fixed
- Eliminated cold-start Welcome/Setup screen flash for returning users by awaiting persisted DataStore preferences before rendering the initial route.
- Hardened `yt-dlp` argument pairing, `FFmpeg` binary/`LD_LIBRARY_PATH` resolution, locale-safe timestamps, per-call cookie jars, and write-verified storage fallbacks.
