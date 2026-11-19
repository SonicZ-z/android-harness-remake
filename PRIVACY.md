# Privacy and Data Handling

Last updated: 2026-11-19

Android Harness Remake is an unofficial app based on Sanuu7's MIT-licensed AndroidHarness source. The app runs on your device and connects to services that you choose and configure. It does not operate a central chat or bot server.

## Data stored on the device

Chats, project settings, workspace files and app configuration are stored locally. Provider credentials, the Discord bot token, and the Discord-specific AI API key and endpoint settings are saved in encrypted preferences backed by Android Keystore. The Discord owner user ID and up to 10 recent AI conversation messages are saved with that encrypted configuration. The app never displays the saved AI key again. Android backup rules exclude the encrypted credential preferences and the local Discord Python command directory from cloud backups and device transfers.

Custom Python command source is stored in the app's private files directory. Scripts run only when invoked by the configured Discord owner, through a saved command name, in the app's managed Linux environment. The app does not run a bot message itself as a shell command. User-authored scripts are trusted code and can access files available to the app user.

## Data sent to services

When you use an ordinary app AI feature, the app sends the prompt and the context needed to answer it to the AI provider selected in the app. That context can include file contents or command output. If you use a local model, model inference can run on the device, but web search, MCP tools, GitHub and other connected services may still receive data as needed for those features.

If you enable the Discord bot, the app sends the bot token to Discord for authenticated API requests. Only messages from the configured owner are processed. Owner direct messages can start AI conversations; in a server, the owner must tag the bot. For AI replies, the app sends the new message and recent conversation history to the OpenAI-compatible endpoint configured in Discord settings. That endpoint receives the API key in its authorization header and the selected model name in the request. The key and conversation history are stored locally in encrypted preferences. Changing the AI endpoint or key, clearing the key, or using `!new` clears saved conversation history. Server replies are delivered to the owner's private Discord chat.

The Discord-specific AI endpoint is separate from the app's normal AI provider configuration. Use HTTPS for remote endpoints. Plain HTTP is accepted only for localhost or Android emulator loopback. The endpoint service has its own privacy policy and logging practices.

Search queries, GitHub requests, MCP traffic and other integrations go to the service you select or configure. Those services have their own privacy policies and logging practices.

## Analytics and backups

The source does not include built-in advertising or analytics features. App data may still be included in device backups according to Android's backup settings, except for the credential preferences and Discord command directory excluded by the app's backup rules.

## Reporting concerns

Use private vulnerability reporting through the repository host when that feature is available. Do not put bot tokens, API keys, passwords, private prompts or unredacted logs in public issues.

## Source and upstream policy

The original upstream privacy statement is preserved in [`UPSTREAM_PRIVACY.md`](UPSTREAM_PRIVACY.md). This remake-specific summary covers the additional Discord feature. Read the project source and the policies of any connected provider or service for details about their behavior.
