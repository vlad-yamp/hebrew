package com.example.hebrew.ui.cards

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.hebrew.HebrewApplication
import com.example.hebrew.data.Card
import kotlinx.coroutines.launch

class CardListViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as HebrewApplication).repository
    private val prefs = app.getSharedPreferences("hebrew_prefs", Context.MODE_PRIVATE)

    val allCards = repository.allCards.asLiveData()

    fun getThreshold(): Int = prefs.getInt("repetitions_count", 4)

    fun clearAll() = viewModelScope.launch { repository.deleteAll() }

    fun resetAll() = viewModelScope.launch { repository.resetAllKnownCounts() }

    fun deleteCard(card: Card) = viewModelScope.launch { repository.delete(card) }
}
