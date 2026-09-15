package com.iris.assistant.agent

import com.iris.assistant.memory.ShortTermMemory

/**
 * The whole app is now built around the API-based model (OpenAiIntentModel).
 * This wrapper tries that first; if it's unreachable for any reason — no key
 * set yet, no internet, provider is down, account has no credit/quota — it
 * falls back to the fully-offline rule-based engine instead of the request
 * just failing outright, and prefixes the reply so it's clear which mode
 * answered.
 */
class HybridIntentModel(
    private val primary: OpenAiIntentModel,
    private val fallback: RuleBasedAIModel
) : IntentModel {

    override suspend fun decide(userText: String, availableTools: Collection<Tool>, memory: ShortTermMemory): Decision {
        return try {
            primary.decide(userText, availableTools, memory)
        } catch (e: Exception) {
            val warning = "⚠️ (بدون اتصال به هوش مصنوعی آنلاین: ${e.message}) "
            val fb = fallback.decide(userText, availableTools, memory)
            if (fb.spokenReply != null) fb.copy(spokenReply = warning + fb.spokenReply) else fb
        }
    }
}
