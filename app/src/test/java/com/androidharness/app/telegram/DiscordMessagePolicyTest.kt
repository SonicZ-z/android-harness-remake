package com.androidharness.app.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordMessagePolicyTest {
    @Test
    fun ownerDmCanStartAConversationWithoutMention() {
        assertTrue(DiscordMessagePolicy.shouldProcess(isOwner = true, isBotAuthor = false, isGuildMessage = false, botMentioned = false))
    }

    @Test
    fun ownerServerMessageNeedsBotMention() {
        assertFalse(DiscordMessagePolicy.shouldProcess(isOwner = true, isBotAuthor = false, isGuildMessage = true, botMentioned = false))
        assertTrue(DiscordMessagePolicy.shouldProcess(isOwner = true, isBotAuthor = false, isGuildMessage = true, botMentioned = true))
    }

    @Test
    fun otherUsersAndBotsCannotTriggerReplies() {
        assertFalse(DiscordMessagePolicy.shouldProcess(isOwner = false, isBotAuthor = false, isGuildMessage = true, botMentioned = true))
        assertFalse(DiscordMessagePolicy.shouldProcess(isOwner = true, isBotAuthor = true, isGuildMessage = false, botMentioned = false))
    }

    @Test
    fun botMentionIsRecognizedAndRemovedFromTheAiPrompt() {
        val message = "<@!123456789012345678> hello"
        assertTrue(DiscordMessagePolicy.mentionsBot(message, "123456789012345678"))
        assertEquals(" hello", DiscordMessagePolicy.stripBotMention(message, "123456789012345678"))
    }
}
