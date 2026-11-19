# Android Harness Remake

An unofficial Android remake based on [Sanuu7's AndroidHarness](https://github.com/Sanuu7/AndroidHarness), copied from upstream commit [`9b7c108`](https://github.com/Sanuu7/AndroidHarness/commit/9b7c108). The upstream project is licensed under MIT. Its original `LICENSE` file is preserved in this repository.

This repository is not affiliated with or endorsed by Sanuu7. AndroidHarness is the name of the upstream project and is credited here as the source project.

## What is included

This app retains the upstream AndroidHarness source, including its Kotlin and Jetpack Compose interface, coding agent, workspace tools, provider support, and existing Telegram remote-control feature. The installed application ID is `com.androidharness.remake` and the displayed app name is **Android Harness Remake**.

The remake adds a limited Discord bot option under **Settings > Telegram > More bot platforms**:

- Enter a Discord application bot token and one numeric owner user ID. The app validates the token with Discord before saving it in Android Keystore-backed encrypted preferences.
- Only the configured owner is accepted. In a server, the owner must mention the bot. Replies from server channels are delivered to the owner's private Discord chat.
- AI conversations use the app's selected model and Plan mode. Discord messages cannot trigger file edits or arbitrary shell commands through the bot.
- Custom commands are named Python scripts created locally in the app. The bot can invoke only saved names, for example `!run weather [input]`. Chat text is passed on standard input and is never concatenated into a shell command. Scripts run in the app's managed Linux environment with a 15 second time limit and output cap, without root or Shizuku privileges. Scripts are user-authored code and can access app-private files available to the app user, so review every script before saving it.
- Enable **Message Content Intent** in the Discord Developer Portal and grant the bot permission to view and send messages. A running bot is shown by a foreground notification with Pause and Stop actions.

The original Telegram feature remains the upstream remote-control implementation. Discord in this release does not duplicate every Telegram project, model, approval, button, or saved-work control.

## WeChat status

WeChat is shown as an informational option only. The [official message callback documentation](https://developers.weixin.qq.com/doc/offiaccount/Message_Management/Receiving_standard_messages.html) says WeChat POSTs messages to a developer-configured server URL. [Integration guidance](https://langbot.app/docs/en/recipes/n8n/wechat-official-account) describes the publicly reachable HTTPS endpoint needed for that setup. A phone-local Android app cannot receive those inbound callbacks on its own. This build does not accept WeChat passwords, tokens, or account credentials.

## Build and install

Use JDK 21 and Android SDK Platform 37 or newer. The project compiles against API 37, targets Android API 36, and supports Android 8.0 or newer.

```bash
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew :app:assembleDebug
```

The included build environment did not provide the NDK shader compiler `glslc`, so the APK was built without the optional native and GPU runtime. To reproduce that build, set `HARNESS_DISABLE_NDK=1`:

```bash
ANDROID_HOME="$HOME/Android/Sdk" HARNESS_DISABLE_NDK=1 ./gradlew :app:assembleRelease
```

The release APK is signed with a local debug key for sideload testing. It is not a Play Store signing configuration. The installable APK produced by the release task is at `app/build/outputs/apk/release/app-release.apk`.

In-app update checks are disabled unless the build is given `-PreleaseRepository=OWNER/android-harness-remake`. This prevents the remake from checking or installing APKs from the upstream project.

The prepared APK does not include a GitHub OAuth client ID. GitHub sign-in can use the app's manual token flow, or a developer can set `GITHUB_CLIENT_ID` in ignored `local.properties` when building a personal copy.

For unit tests, run:

```bash
ANDROID_HOME="$HOME/Android/Sdk" HARNESS_DISABLE_NDK=1 ./gradlew :app:testDebugUnitTest
```

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full remake release notes. The original upstream history is preserved in [UPSTREAM_CHANGELOG.md](UPSTREAM_CHANGELOG.md).

### 1.0.0

- Created a distinct Android app project and application ID from the upstream AndroidHarness source.
- Preserved the upstream MIT license and added direct credit to Sanuu7 and the source repository.
- Kept the existing Telegram remote-control feature.
- Added owner-only Discord bot connection, private replies, Plan-mode AI conversations, and named local Python commands.
- Added a WeChat information card that explains the official account and public callback requirement.
- Added an optional native-build opt-out for SDK environments without `glslc`; this APK does not include the optional native or GPU runtime.
- Removed the upstream cross-repository publisher and all Actions artifact uploads; CI does not publish APKs or test/build artifacts.
- Disabled the in-app updater until this remake has its own configured release repository.

## Credits and notices

- **Sanuu7** created [AndroidHarness](https://github.com/Sanuu7/AndroidHarness), the MIT-licensed source project used as the base for this remake. The upstream copyright and permission notice remain in `LICENSE`.
- Upstream acknowledgements for the open-source libraries and projects used by AndroidHarness are preserved in the copied source tree. Third-party code and assets retain their original notices.
- Discord is a service of Discord Inc. This project is an independent client integration and is not endorsed by Discord.

## Privacy

See [PRIVACY.md](PRIVACY.md) for this remake's data handling summary. The original upstream statement is preserved in [UPSTREAM_PRIVACY.md](UPSTREAM_PRIVACY.md).

## License

The source is distributed under the MIT License. See [`LICENSE`](LICENSE) for the preserved upstream notice and terms. Dependencies and bundled assets may have separate license terms in their own files.
