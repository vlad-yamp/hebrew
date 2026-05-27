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

enum class LearningMode { MEMORIZE, REVIEW }

class LearningViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as HebrewApplication).repository
    private val prefs = app.getSharedPreferences("hebrew_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _state = MutableLiveData<LearningState>(LearningState.Loading)
    val state: LiveData<LearningState> = _state

    private val _examplesState = MutableLiveData<ExamplesState>(ExamplesState.Idle)
    val examplesState: LiveData<ExamplesState> = _examplesState

    private val _memorizedCount = MutableLiveData(0)
    val memorizedCount: LiveData<Int> = _memorizedCount

    private val _mode = MutableLiveData(
        if (prefs.getBoolean("learning_mode_memorize", true)) LearningMode.MEMORIZE
        else LearningMode.REVIEW
    )
    val mode: LiveData<LearningMode> = _mode

    private var allCards: List<Card> = emptyList()
    private var sessionQueue: MutableList<Long> = mutableListOf()
    private var currentIndex = 0

    // Memorize-mode state (in-memory, starts fresh each time)
    private var memorizePool: List<Card> = emptyList()
    private var memorizePoolIndex = 0
    private val memorizeActiveIds: MutableSet<Long> = mutableSetOf()

    private fun threshold() = prefs.getInt("repetitions_count", 4)

    private fun refreshMemorizedCount() {
        viewModelScope.launch {
            _memorizedCount.value = repository.getMemorizedCount(threshold())
        }
    }

    init { loadSession() }

    // ── Mode switching ────────────────────────────────────────────────────────

    fun setMode(mode: LearningMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        prefs.edit().putBoolean("learning_mode_memorize", mode == LearningMode.MEMORIZE).apply()
        _examplesState.value = ExamplesState.Idle
        clearSession()
        viewModelScope.launch {
            allCards = repository.getLearningCards(threshold())
            refreshMemorizedCount()
            if (allCards.isEmpty()) { _state.value = LearningState.AllLearned; return@launch }
            if (mode == LearningMode.MEMORIZE) startMemorizeSession() else startReviewSession()
        }
    }

    // ── Session loading ───────────────────────────────────────────────────────

    private fun loadSession() {
        viewModelScope.launch {
            allCards = repository.getLearningCards(threshold())
            refreshMemorizedCount()
            if (allCards.isEmpty()) { _state.value = LearningState.AllLearned; return@launch }

            if (_mode.value == LearningMode.MEMORIZE) {
                startMemorizeSession()
                return@launch
            }

            // Review: restore saved session
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
            startReviewSession()
        }
    }

    // ── Review mode ───────────────────────────────────────────────────────────

    private fun startReviewSession() {
        sessionQueue = allCards.map { it.id }.shuffled().toMutableList()
        currentIndex = 0
        persistSession()
        showCurrentCard()
    }

    private fun reloadAfterRound() {
        viewModelScope.launch {
            allCards = repository.getLearningCards(threshold())
            refreshMemorizedCount()
            if (allCards.isEmpty()) { clearSession(); _state.value = LearningState.AllLearned }
            else startReviewSession()
        }
    }

    // ── Memorize mode ─────────────────────────────────────────────────────────

    private fun startMemorizeSession() {
        memorizePool = allCards.shuffled()
        memorizePoolIndex = 0
        memorizeActiveIds.clear()
        val take = minOf(BATCH_SIZE, allCards.size)
        memorizeActiveIds.addAll(memorizePool.take(take).map { it.id })
        memorizePoolIndex = take
        buildMemorizeQueue()
    }

    private fun buildMemorizeQueue() {
        val active = allCards.filter { it.id in memorizeActiveIds }.shuffled()
        if (active.isEmpty()) {
            if (memorizePoolIndex >= memorizePool.size) {
                clearSession(); _state.value = LearningState.AllLearned
            } else {
                refillMemorizeActive()
                buildMemorizeQueue()
            }
            return
        }
        sessionQueue = active.map { it.id }.toMutableList()
        currentIndex = 0
        showCurrentCard()
    }

    private fun refillMemorizeActive() {
        // Only add new cards — never remove unlearned ones from the active set
        var added = 0
        while (memorizePoolIndex < memorizePool.size && added < NEW_PER_ROUND) {
            val card = memorizePool[memorizePoolIndex++]
            if (allCards.any { it.id == card.id } && card.id !in memorizeActiveIds) {
                memorizeActiveIds.add(card.id)
                added++
            }
        }
    }

    private fun rebuildMemorizeRound() {
        viewModelScope.launch {
            allCards = repository.getLearningCards(threshold())
            refreshMemorizedCount()
            if (allCards.isEmpty()) { clearSession(); _state.value = LearningState.AllLearned; return@launch }

            // Remove graduated cards from active set
            memorizeActiveIds.removeAll { id -> allCards.none { it.id == id } }

            if (memorizePoolIndex < memorizePool.size) {
                // Pool not exhausted — introduce new words gradually
                refillMemorizeActive()
            } else {
                // Pool exhausted — make sure every remaining unlearned word is in the active set
                allCards.forEach { memorizeActiveIds.add(it.id) }
            }

            buildMemorizeQueue()
        }
    }

    // ── Card display ──────────────────────────────────────────────────────────

    private fun showCurrentCard() {
        _examplesState.value = ExamplesState.Idle
        if (currentIndex >= sessionQueue.size) {
            if (_mode.value == LearningMode.MEMORIZE) rebuildMemorizeRound() else reloadAfterRound()
            return
        }
        val id = sessionQueue[currentIndex]
        val card = allCards.find { it.id == id } ?: run { advanceAndShow(); return }
        _state.value = LearningState.ShowCard(card, currentIndex + 1, sessionQueue.size, false)
    }

    // ── User actions ──────────────────────────────────────────────────────────

    fun toggleFlip() {
        val cur = _state.value as? LearningState.ShowCard ?: return
        _state.value = cur.copy(isFlipped = !cur.isFlipped)
    }

    fun markKnown() {
        val cur = _state.value as? LearningState.ShowCard ?: return
        val card = cur.card
        viewModelScope.launch {
            val updated = card.copy(knownCount = card.knownCount + 1)
            repository.update(updated)
            allCards = allCards.map { if (it.id == updated.id) updated else it }
            if (updated.knownCount >= threshold()) refreshMemorizedCount()
            advanceAndShow()
        }
    }

    fun markUnknown() {
        val cur = _state.value as? LearningState.ShowCard ?: return
        val card = cur.card
        if (card.knownCount > 0) {
            viewModelScope.launch {
                val reset = card.copy(knownCount = 0)
                repository.update(reset)
                allCards = allCards.map { if (it.id == card.id) reset else it }
            }
        }
        advanceAndShow()
    }

    fun updateRussian(newText: String) {
        val cur = _state.value as? LearningState.ShowCard ?: return
        val updated = cur.card.copy(russian = newText)
        viewModelScope.launch {
            repository.update(updated)
            allCards = allCards.map { if (it.id == updated.id) updated else it }
            _state.value = cur.copy(card = updated)
        }
    }

    private fun advanceAndShow() {
        currentIndex++
        if (_mode.value == LearningMode.REVIEW) persistSession()
        showCurrentCard()
    }

    // ── Examples ──────────────────────────────────────────────────────────────

    fun loadExamples() {
        val card = (_state.value as? LearningState.ShowCard)?.card ?: return
        val apiKey = prefs.getString("openai_api_key", "") ?: ""
        if (apiKey.isBlank()) return

        val examplesCount = prefs.getInt("examples_count", 5)
        _examplesState.value = ExamplesState.Loading
        viewModelScope.launch {
            try {
                val prompt = """Дай $examplesCount примеров использования слова или фразы «${card.hebrew}» (иврит) в предложениях.
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

    // ── Restart ───────────────────────────────────────────────────────────────

    fun restartLearning() {
        viewModelScope.launch {
            repository.resetAllKnownCounts()
            allCards = repository.getLearningCards(threshold())
            refreshMemorizedCount()
            clearSession()
            memorizeActiveIds.clear()
            memorizePoolIndex = 0
            if (_mode.value == LearningMode.MEMORIZE) startMemorizeSession() else startReviewSession()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseExamples(content: String): List<ExampleItem> {
        val blocks = content.trim().split(Regex("\\n\\s*\\n"))
        return blocks.mapNotNull { block ->
            val lines = block.trim().lines()
                .map { it.replace(Regex("^\\d+[.)\\s]+"), "").trim() }
                .filter { it.isNotBlank() }
            if (lines.size >= 2) ExampleItem(lines[0], lines[1]) else null
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

    companion object {
        private const val BATCH_SIZE = 10
        private const val NEW_PER_ROUND = 7
    }
}

sealed class LearningState {
    object Loading : LearningState()
    object AllLearned : LearningState()
    data class ShowCard(val card: Card, val current: Int, val total: Int, val isFlipped: Boolean) : LearningState()
}

sealed class ExamplesState {
    object Idle : ExamplesState()
    object Loading : ExamplesState()
    data class Done(val examples: List<ExampleItem>) : ExamplesState()
    data class Error(val message: String) : ExamplesState()
}

data class ExampleItem(val hebrew: String, val russian: String)
