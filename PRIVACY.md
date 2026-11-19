# Privacy and Data Handling

Last updated: 2026-11-19

Android Harness Remake is an unofficial app based on Sanuu7's MIT-licensed AndroidHarness source. The app runs on your device and connects to services that you choose and configure. It does not operate a central chat or bot server.

## Data stored on the device

Chats, project settings, workspace files and app configuration are stored locally. Provider credentials and the Discord bot token are saved in the app's encrypted preferences backed by Android Keystore. The Discord owner user ID and bot setup are stored with that encrypted configuration. Android backup rules exclude the encrypted credential preferences and the local Discord Python command directory from cloud backups and device transfers.

Custom Python command source is stored in the app's private files directory. Scripts run only when invoked by the configured Discord owner, through a saved command name, in the app's managed Linux environment. The app does not run the bot message itself as a shell command. User-authored scripts are trusted code and can access files available to the app user.

## Data sent to services

When you use an AI feature, the app sends the prompt and the context needed to answer it to the AI provider selected in the app. That context can include file contents or command output. If you use a local model, model inference can run on the device, but web search, MCP tools, GitHub and other connected services may still receive data as needed for those features.

If you enable the Discord bot, the app sends the bot token to Discord for authenticated API requests. Discord messages from the configured owner are received by the app. For AI requests, the message text is sent through the selected AI provider, and the response is sent back through Discord. Server-channel replies are delivered to the owner's private Discord chat. The app does not publish the configured token in messages.

Search queries, GitHub requests, MCP traffic and other integrations go to the service you select or configure. Those services have their own privacy policies and logging practices.

## Analytics and backups

The source does not include built-in advertising or analytics features. App data may still be included in device backups according to Android's backup settings, except for the credential preferences and Discord command directory excluded by the app's backup rules.

## WeChat

WeChat bot credentials are not collected in this build. The WeChat settings card is informational only.

## Reporting concerns

Use private vulnerability reporting through the repository host when that feature is available. Do not put bot tokens, API keys, passwords, private prompts or unredacted logs in public issues.

## Source and upstream policy

The original upstream privacy statement is preserved in [`UPSTREAM_PRIVACY.md`](UPSTREAM_PRIVACY.md). This remake-specific summary covers the additional Discord feature. Read the project source and the policies of any connected provider or service for details about their behavior.
