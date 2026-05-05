package com.example.hebrew.ui.learning

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.hebrew.HebrewApplication
import com.example.hebrew.api.ChatMessage
import com.example.hebrew.api.ChatRequest
import com.example.hebrew.api.OpenAIClient
import com.example.hebrew.data.Card
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

class LearningViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as HebrewApplication).repository
    private val prefs = app.getSharedPreferences("hebrew_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _state = MutableLiveData<LearningState>(LearningState.Loading)
    val state: LiveData<LearningState> = _state

    private val _examplesState = MutableLiveData<ExamplesState>(ExamplesState.Idle)
    val examplesState: LiveData<ExamplesState> = _examplesState

    private var allCards: List<Card> = emptyList()
    private var sessionQueue: MutableList<Long> = mutableListOf()
    private var currentIndex = 0

    private fun threshold() = prefs.getInt("repetitions_count", 4)

    init { loadSession() }

    private fun loadSession() {
        viewModelScope.launch {
            allCards = repository.getLearningCards(threshold())
            if (allCards.isEmpty()) {
                _state.value = LearningState.AllLearned
                return@launch
            }

            val savedJson = prefs.getString("session_queue", null)
            val savedIndex = prefs.getInt("session_index", 0)

            if (!savedJson.isNullOrBlank()) {
                val type = object : TypeToken<List<Long>>() {}.type
                val saved: List<Long> = gson.fromJson(savedJson, type)
                val validIds = allCards.map { it.id }.toSet()
                val filtered = saved.filter { it in validIds }.toMutableList()
                if (filtered.isNotEmpty()) {
                    sessionQueue = filtered
                    currentIndex = savedIndex.coerceIn(0, filtered.size - 1)
                    showCurrentCard()
                    return@launch
                }
            }
            startNewSession()
        }
    }

    private fun startNewSession() {
        sessionQueue = allCards.map { it.id }.shuffled().toMutableList()
        currentIndex = 0
        persistSession()
        showCurrentCard()
    }

    private fun showCurrentCard() {
        _examplesState.value = ExamplesState.Idle
        if (currentIndex >= sessionQueue.size) {
            reloadAfterRound()
            return
        }
        val id = sessionQueue[currentIndex]
        val card = allCards.find { it.id == id } ?: run { advanceAndShow(); return }
        _state.value = LearningState.ShowCard(
            card = card,
            current = currentIndex + 1,
            total = sessionQueue.size,
            isFlipped = false
        )
    }

    fun toggleFlip() {
        val current = _state.value as? LearningState.ShowCard ?: return
        _state.value = current.copy(isFlipped = !current.isFlipped)
    }

    fun markKnown() {
        val current = _state.value as? LearningState.ShowCard ?: return
        val card = current.card
        viewModelScope.launch {
            val updated = card.copy(knownCount = card.knownCount + 1)
            repository.update(updated)
            allCards = allCards.map { if (it.id == updated.id) updated else it }
            advanceAndShow()
        }
    }

fun markUnknown() {
        advanceAndShow()
    }

    private fun advanceAndShow() {
        currentIndex++
        persistSession()
        if (currentIndex >= sessionQueue.size) reloadAfterRound()
        else showCurrentCard()
    }

    private fun reloadAfterRound() {
        viewModelScope.launch {
            allCards = repository.getLearningCards(threshold())
            if (allCards.isEmpty()) {
                clearSession()
                _state.value = LearningState.AllLearned
            } else {
                startNewSession()
            }
        }
    }

    fun loadExamples() {
        val card = (_state.value as? LearningState.ShowCard)?.card ?: return
        val apiKey = prefs.getString("openai_api_key", "") ?: ""
        if (apiKey.isBlank()) return

        _examplesState.value = ExamplesState.Loading
        viewModelScope.launch {
            try {
                val prompt = """Дай 5 примеров использования слова или фразы «${card.hebrew}» (иврит) в предложениях.
Формат каждого примера:
[предложение на иврите]
[перевод на русский]

Разделяй примеры пустой строкой."""
                val response = OpenAIClient.service.getCompletion(
                    auth = "Bearer $apiKey",
                    request = ChatRequest(
                        messages = listOf(ChatMessage("user", prompt)),
                        max_tokens = 1000
                    )
                )
                val content = response.choices.firstOrNull()?.message?.content?.trim() ?: ""
                _examplesState.value = ExamplesState.Done(parseExamples(content))
            } catch (e: Exception) {
                _examplesState.value = ExamplesState.Error(e.message ?: "Ошибка")
            }
        }
    }

    private fun parseExamples(content: String): List<ExampleItem> {
        val blocks = content.trim().split(Regex("\\n\\s*\\n"))
        return blocks.mapNotNull { block ->
            val lines = block.trim().lines()
                .map { it.replace(Regex("^\\d+[.)\\s]+"), "").trim() }
                .filter { it.isNotBlank() }
            if (lines.size >= 2) ExampleItem(lines[0], lines[1]) else null
        }
    }

    fun restartLearning() {
        viewModelScope.launch {
            repository.resetAllKnownCounts()
            allCards = repository.getLearningCards(threshold())
            clearSession()
            startNewSession()
        }
    }

    private fun persistSession() {
        prefs.edit()
            .putString("session_queue", gson.toJson(sessionQueue))
            .putInt("session_index", currentIndex)
            .apply()
    }

    private fun clearSession() {
        prefs.edit().remove("session_queue").remove("session_index").apply()
    }
}

sealed class LearningState {
    object Loading : LearningState()
    object AllLearned : LearningState()
    data class ShowCard(
        val card: Card,
        val current: Int,
        val total: Int,
        val isFlipped: Boolean
    ) : LearningState()
}

sealed class ExamplesState {
    object Idle : ExamplesState()
    object Loading : ExamplesState()
    data class Done(val examples: List<ExampleItem>) : ExamplesState()
    data class Error(val message: String) : ExamplesState()
}

data class ExampleItem(val hebrew: String, val russian: String)
