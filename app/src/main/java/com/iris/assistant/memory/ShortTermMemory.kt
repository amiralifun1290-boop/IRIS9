package com.iris.assistant.memory

/**
 * Very simple short-term memory for the agent loop.
 * Keeps the last few user / assistant turns so the rule-based model
 * can have a tiny bit of context.
 */
class ShortTermMemory(private val maxTurns: Int = 6) {
    private val turns = ArrayDeque<Pair<String, String>>() // role to content

    fun add(role: String, content: String) {
        turns.addLast(role to content)
        while (turns.size > maxTurns) {
            turns.removeFirst()
        }
    }

    fun recent(): List<Pair<String, String>> = turns.toList()

    fun clear() = turns.clear()
}
