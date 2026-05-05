package com.example.hebrew.ui.voice

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class VoiceViewModel : ViewModel() {

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
