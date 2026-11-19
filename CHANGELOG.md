# Changelog

## 1.0.2 - 2026-11-19

### Added

- Bot-specific OpenAI-compatible Chat Completions settings for an encrypted API key, base URL, and model, separate from the app's normal AI provider.
- Up to 10 recent Discord conversation messages stored with the encrypted bot configuration. The `!new` command clears the conversation.
- Endpoint validation that requires HTTPS for remote services, permits plain HTTP only for localhost or emulator loopback, rejects URL credentials/query strings, and does not follow redirects.
- Unit tests for legacy config defaults, secret redaction, endpoint validation, and owner/tag message routing.
- Palette-aware gradient panels, a shared aurora shell background, and retuned central motion timings.

### Changed

- Replace the app-shared Plan-mode AI bridge with the configured bot-specific endpoint. Owner direct messages can start a conversation; server conversations require an owner message tagging the bot. Replies to server messages remain private to the owner.
- Replace the custom Python list wording with a Command Manager that clearly adds, edits, and deletes saved named commands. Execution remains limited to locally saved scripts with time and output bounds.
- Remove the unused bot-platform placeholder card from the Discord settings page.
- Update README and privacy text for encrypted bot AI settings, conversation data flow, endpoint constraints, and command management.
- Bump the app to version 1.0.2 with version code 3. The source ZIP and test-signed APK are published in the public v1.0.2 release.

## 1.0.1 - 2026-11-19 (prepared, not released)

### Fixed

- Do not release the cached native local model when Android reports that the app UI became hidden. Reserve teardown for actual memory-pressure callbacks.
- Catch and log ordinary background lifecycle cleanup errors so a service cleanup failure cannot terminate the runtime-policy observer.
- Temporarily stop the Discord service when background execution is disabled without clearing the user's enabled preference; restore it when the app returns to the foreground.
- Add regression tests covering UI-hidden, low-memory, critical-memory, and ordinary trim levels.

### Added

- Dedicated Discord bot settings page, independent from the upstream Telegram page.
- In-app Discord setup walkthrough covering owner access, bot permissions, background behavior, and Python command safety.
- Configurable command prefix and independent AI-reply and custom-Python-command switches.
- Edit and remove actions for saved Python commands.
- About-page links for remake creator SonicZ and the remake source, plus an editable friend-profile placeholder.

### Changed

- Bump the prepared app version to 1.0.1 with version code 2. No APK release was published.
- Keep custom Python execution restricted to locally saved command names, with time and output limits. Scripts execute as the app's Linux user and may access resources available to that user.

## 1.0.0 - 2026-11-19

### Added

- Discord bot settings for a user-supplied bot token and a single allowed owner account.
- Foreground Discord Gateway service with Pause and Stop controls, runtime status, and optional restart restoration after reboot.
- Plan-mode AI conversations started by the owner in a private message or by mentioning the bot in a server. Server replies are delivered privately to the configured owner.
- Named Python command editor. The bot can invoke saved command names only; it does not expose a chat-driven shell or arbitrary Python input.
- Configuration-generation checks and cancellation prevent an in-flight reply from crossing to a newly configured owner.
- Gateway shutdown cleanup and heartbeat acknowledgement monitoring trigger reconnects when the connection stops responding.
- Timed Python commands have bounded output and process-group cleanup so spawned children are stopped on completion or timeout.
- Informational bot-platform card explaining why a public callback service is required for one unsupported integration.

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
