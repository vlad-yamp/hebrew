package com.example.hebrew.ui.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.example.hebrew.HebrewApplication
import kotlinx.coroutines.flow.map

class VoiceViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as HebrewApplication).repository

    val cardCount: LiveData<Int> = repository.allCards
        .map { it.size }
        .asLiveData()

    private val _state = MutableLiveData<VoiceState>(VoiceState.Idle)
    val state: LiveData<VoiceState> = _state

    fun onListening() { _state.value = VoiceState.Listening }
    fun onIdle() { _state.value = VoiceState.Idle }
    fun onError(msg: String) { _state.value = VoiceState.Error(msg) }
    fun onErrorHandled() { _state.value = VoiceState.Idle }
}

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    data class Error(val message: String) : VoiceState()
}
