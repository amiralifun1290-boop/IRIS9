package com.iris.assistant.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.IrisApp
import com.iris.assistant.agent.AgentBootstrap
import com.iris.assistant.agent.AgentState
import com.iris.assistant.agent.AgentStateHolder
import com.iris.assistant.data.db.ActivityLogEntity
import com.iris.assistant.data.db.ConversationEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IrisViewModel(app: Application) : AndroidViewModel(app) {
    private val db = IrisApp.instance.database

    // AgentCore replaces the old CommandProcessor as the real decision
    // maker. Every step it takes is persisted into the same Activity Log
    // the rest of the app already shows.
    private val agentCore = AgentBootstrap.buildAgentCore(app) { logLine ->
        viewModelScope.launch {
            db.activityLogDao().insert(ActivityLogEntity(action = logLine, isSuccess = true))
        }
    }

    val agentState: StateFlow<AgentState> = AgentStateHolder.state

    val messages = db.conversationDao().getAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val activityLogs = db.activityLogDao().getAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /** Both typed and voice-transcribed input come through here into the real Agent Loop. */
    fun sendMessage(text: String) {
        viewModelScope.launch {
            db.conversationDao().insert(ConversationEntity(role = "user", content = text))
            val reply = try {
                agentCore.handleRequest(text)
            } catch (e: Exception) {
                "خطا در اجرای دستور: ${e.message}"
            }
            db.conversationDao().insert(ConversationEntity(role = "assistant", content = reply))
        }
    }
}
