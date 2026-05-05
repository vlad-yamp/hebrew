package com.example.hebrew.ui.cards

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.hebrew.HebrewApplication
import kotlinx.coroutines.launch

class CardListViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as HebrewApplication).repository

    val allCards = repository.allCards.asLiveData()

    fun clearAll() = viewModelScope.launch { repository.deleteAll() }
}
