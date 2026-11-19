# Android Harness Remake

An unofficial Android remake based on [Sanuu7's AndroidHarness](https://github.com/Sanuu7/AndroidHarness), copied from upstream commit [`9b7c108`](https://github.com/Sanuu7/AndroidHarness/commit/9b7c108). The upstream project is licensed under MIT. Its original `LICENSE` file is preserved in this repository.

This repository is not affiliated with or endorsed by Sanuu7. AndroidHarness is the name of the upstream project and is credited here as the source project.

## What is included

This app retains the upstream AndroidHarness source, including its Kotlin and Jetpack Compose interface, coding agent, workspace tools, provider support, and existing Telegram remote-control feature. The installed application ID is `com.androidharness.remake` and the displayed app name is **Android Harness Remake**.

The remake adds a Discord bot page under **Settings > Discord bot**:

- Enter a Discord application bot token and one numeric owner user ID. The token is stored in encrypted Android Keystore-backed preferences.
- Only the configured owner can control the bot. Owner direct messages can start an AI conversation. In a server, an owner message must tag the bot, for example `@bot hello`. Replies to server messages are delivered privately to the owner's Discord chat.
- Configure a bot-specific OpenAI-compatible Chat Completions API key, base URL, and model. The default base URL is `https://api.openai.com/v1`; choose a model supported by your endpoint. The key, endpoint settings, and up to 10 recent conversation messages are stored in the encrypted Discord settings record and are excluded from Android backups. `!new` clears the conversation history.
- The AI endpoint receives the conversation text. Discord AI has no app tools, file access, or command execution. It does not change the AI provider selected for ordinary app chats.
- Choose a command prefix from `!`, `?`, `~`, or `.` and independently disable AI replies or custom Python commands.
- Use the in-app **Command Manager** to add, edit, and delete named Python scripts. The bot can invoke only saved names, for example `!run weather [input]` or `!weather [input]`. Chat text is passed on standard input and is never concatenated into a shell command. Scripts run in the app's managed Linux environment with a 15 second time limit and output cap, without root or Shizuku privileges. Scripts are user-authored code and can access files and services available to the app's Linux user, so review every script before saving it.
- Enable **Message Content Intent** in the Discord Developer Portal and grant the bot permission to view and send messages. A running bot is shown by a foreground notification with Pause and Stop actions.
- If app background running is disabled, Discord is temporarily suspended while the app is hidden and resumes when the app returns. Enable **Settings > Background > Run in background** to keep it online, subject to Android battery restrictions.

The original Telegram feature remains the upstream remote-control implementation. Discord in this release does not duplicate every Telegram project, model, approval, button, or saved-work control.

## App appearance

The 1.0.2 release adds a palette-aware aurora gradient behind the main shell and primary conversation, settings, and onboarding surfaces. Gradient settings panels use the active system, custom, or selected palette. Shared motion timings are softened while the existing navigation and layout behavior remain in place.

## Build and install

Use JDK 21 and Android SDK Platform 37 or newer. The project compiles against API 37, targets Android API 36, and supports Android 8.0 or newer.

```bash
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew :app:assembleDebug
```

The included build environment did not provide the NDK shader compiler `glslc`, so the APK is built without the optional native and GPU runtime. To reproduce that build, set `HARNESS_DISABLE_NDK=1`:

```bash
ANDROID_HOME="$HOME/Android/Sdk" HARNESS_DISABLE_NDK=1 ./gradlew :app:assembleRelease
```

The local release APK is signed with a debug key for sideload testing. It is not a Play Store signing configuration. The installable APK produced by the release task is at `app/build/outputs/apk/release/app-release.apk`.

In-app update checks are disabled unless the build is given `-PreleaseRepository=OWNER/android-harness-remake`. This prevents the remake from checking or installing APKs from the upstream project.

The prepared APK does not include a GitHub OAuth client ID. GitHub sign-in can use the app's manual token flow, or a developer can set `GITHUB_CLIENT_ID` in ignored `local.properties` when building a personal copy.

For unit tests, run:

```bash
ANDROID_HOME="$HOME/Android/Sdk" HARNESS_DISABLE_NDK=1 ./gradlew :app:testDebugUnitTest
```

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full remake release notes. The original upstream history is preserved in [UPSTREAM_CHANGELOG.md](UPSTREAM_CHANGELOG.md).

Version 1.0.2 is published in the public repository with the bot-specific AI settings, Command Manager, visual refresh, source ZIP, and test-signed APK.

## Credits and notices

- **SonicZ** created this remake. The About page links to the [SonicZ GitHub profile](https://github.com/SonicZ-z) and the [remake source repository](https://github.com/SonicZ-z/android-harness-remake).
- **Friend collaborator:** profile details are pending confirmation. The About-page credit name and profile URL are marked as editable placeholders in the source.
- **Sanuu7** created [AndroidHarness](https://github.com/Sanuu7/AndroidHarness), the MIT-licensed source project used as the base for this remake. The upstream copyright and permission notice remain in `LICENSE`.
- Upstream acknowledgements for the open-source libraries and projects used by AndroidHarness are preserved in the copied source tree. Third-party code and assets retain their original notices.
- Discord is a service of Discord Inc. This project is an independent client integration and is not endorsed by Discord.

## Privacy

See [PRIVACY.md](PRIVACY.md) for this remake's data handling summary. The original upstream statement is preserved in [UPSTREAM_PRIVACY.md](UPSTREAM_PRIVACY.md).

## License

The source is distributed under the MIT License. See [`LICENSE`](LICENSE) for the preserved upstream notice and terms. Dependencies and bundled assets may have separate license terms in their own files.
