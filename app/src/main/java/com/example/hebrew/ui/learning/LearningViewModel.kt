package com.example.hebrew.ui.learning

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.hebrew.HebrewApplication
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

    private var allCards: List<Card> = emptyList()
    private var sessionQueue: MutableList<Long> = mutableListOf()
    private var currentIndex = 0

    init { loadSession() }

    private fun loadSession() {
        viewModelScope.launch {
            allCards = repository.getLearningCards()
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
        if (currentIndex >= sessionQueue.size) {
            reloadAfterRound()
            return
        }
        val id = sessionQueue[currentIndex]
        val card = allCards.find { it.id == id } ?: run {
            advanceAndShow()
            return
        }
        _state.value = LearningState.ShowCard(
            card = card,
            current = currentIndex + 1,
            total = sessionQueue.size,
            isFlipped = false
        )
    }

    fun flipCard() {
        val current = _state.value as? LearningState.ShowCard ?: return
        _state.value = current.copy(isFlipped = true)
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
        if (currentIndex >= sessionQueue.size) {
            reloadAfterRound()
        } else {
            showCurrentCard()
        }
    }

    private fun reloadAfterRound() {
        viewModelScope.launch {
            allCards = repository.getLearningCards()
            if (allCards.isEmpty()) {
                clearSession()
                _state.value = LearningState.AllLearned
            } else {
                startNewSession()
            }
        }
    }

    fun restartLearning() {
        viewModelScope.launch {
            repository.resetAllKnownCounts()
            allCards = repository.getLearningCards()
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
        prefs.edit()
            .remove("session_queue")
            .remove("session_index")
            .apply()
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
