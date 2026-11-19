package com.androidharness.app.telegram

/** Pure message-routing rules shared by Discord dispatch and unit tests. */
internal object DiscordMessagePolicy {
    fun shouldProcess(
        isOwner: Boolean,
        isBotAuthor: Boolean,
        isGuildMessage: Boolean,
        botMentioned: Boolean,
    ): Boolean = isOwner && !isBotAuthor && (!isGuildMessage || botMentioned)

    fun mentionsBot(content: String, botId: String): Boolean = botId.isNotBlank() &&
        (content.contains("<@$botId>") || content.contains("<@!$botId>"))

    fun stripBotMention(content: String, botId: String): String = if (botId.isBlank()) content else
        content.replace("<@$botId>", "", ignoreCase = true)
            .replace("<@!$botId>", "", ignoreCase = true)
}
