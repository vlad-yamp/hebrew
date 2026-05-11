package com.example.hebrew.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hebrew.HebrewApplication
import com.example.hebrew.data.HistoryEntry
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as HebrewApplication).historyRepository

    val allEntries = repository.allEntries

    fun delete(entry: HistoryEntry) {
        viewModelScope.launch { repository.delete(entry) }
    }

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAll() }
    }
}
