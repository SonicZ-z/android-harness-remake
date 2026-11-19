# Changelog

## 1.0.0 - 2026-11-19

### Added

- Discord bot settings for a user-supplied bot token and a single allowed owner account.
- Foreground Discord Gateway service with Pause and Stop controls, runtime status, and optional restart restoration after reboot.
- Plan-mode AI conversations started by the owner in a private message or by mentioning the bot in a server. Server replies are delivered privately to the configured owner.
- Named Python command editor. The bot can invoke saved command names only; it does not expose a chat-driven shell or arbitrary Python input.
- Configuration-generation checks and cancellation prevent an in-flight reply from crossing to a newly configured owner.
- Gateway shutdown cleanup and heartbeat acknowledgement monitoring trigger reconnects when the connection stops responding.
- Timed Python commands have bounded output and process-group cleanup so spawned children are stopped on completion or timeout.
- WeChat status card explaining why a public callback service is required for an official integration.

### Changed

- Set the installed application ID to `com.androidharness.remake`, app name to Android Harness Remake, and version to 1.0.0.
- Compile against Android API 37 to meet dependency requirements while retaining target SDK 36.
- Added a `releaseRepository` Gradle property for in-app updates. It is empty by default so this remake does not query or install Sanuu7's upstream releases.
- Added `HARNESS_DISABLE_NDK=1` for SDK build environments without the NDK shader compiler. The prepared APK omits the optional native and GPU runtime.
- Removed the copied workflow that could upload GPU packages to Sanuu7's separate repository and removed all Actions artifact uploads. CI may build transiently but does not publish APKs or other artifacts.
- Replaced the copied privacy page with a remake-specific data summary and preserved the original as `UPSTREAM_PRIVACY.md`.
- Preserved the original upstream `CHANGELOG.md` content as `UPSTREAM_CHANGELOG.md`.

## Attribution

This remake is based on Sanuu7's MIT-licensed [AndroidHarness](https://github.com/Sanuu7/AndroidHarness), copied from upstream commit `9b7c108`. The original `LICENSE` file is preserved unchanged. See `README.md` for scope, setup and limitations.
