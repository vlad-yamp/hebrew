package com.example.hebrew.ui.translation

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
import kotlinx.coroutines.launch

class TranslationViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as HebrewApplication).repository
    private val prefs = app.getSharedPreferences("hebrew_prefs", Context.MODE_PRIVATE)

    private val _translationState = MutableLiveData<TranslationState>(TranslationState.Idle)
    val translationState: LiveData<TranslationState> = _translationState

    private val _examplesState = MutableLiveData<ExamplesState>(ExamplesState.Idle)
    val examplesState: LiveData<ExamplesState> = _examplesState

    private val _cardSaved = MutableLiveData(false)
    val cardSaved: LiveData<Boolean> = _cardSaved

    private var currentHebrew: String = ""
    private var selectedTranslation: String = ""

    fun translate(hebrewText: String) {
        currentHebrew = hebrewText
        val apiKey = prefs.getString("openai_api_key", "") ?: ""
        if (apiKey.isBlank()) {
            _translationState.value = TranslationState.Error("Введите API ключ в настройках")
            return
        }

        _translationState.value = TranslationState.Loading
        viewModelScope.launch {
            try {
                val prompt = """Переведи следующее слово или фразу с иврита на русский язык.
Если есть несколько значений, дай до 5 вариантов перевода, пронумеровав их (1. ... 2. ... и т.д.).
Если значение одно — просто напиши перевод без нумерации.
Только переводы, без объяснений.

Иврит: $hebrewText"""

                val response = OpenAIClient.service.getCompletion(
                    auth = "Bearer $apiKey",
                    request = ChatRequest(
                        messages = listOf(ChatMessage("user", prompt))
                    )
                )
                val content = response.choices.firstOrNull()?.message?.content?.trim() ?: ""
                val variants = parseVariants(content)
                if (variants.size > 1) {
                    _translationState.value = TranslationState.MultipleVariants(variants)
                } else {
                    val single = variants.firstOrNull() ?: content
                    selectedTranslation = single
                    _translationState.value = TranslationState.SingleTranslation(single)
                }
            } catch (e: Exception) {
                _translationState.value = TranslationState.Error(e.message ?: "Ошибка")
            }
        }
    }

    fun selectVariant(translation: String) {
        selectedTranslation = translation
    }

    fun loadExamples() {
        val apiKey = prefs.getString("openai_api_key", "") ?: ""
        if (apiKey.isBlank()) return

        _examplesState.value = ExamplesState.Loading
        viewModelScope.launch {
            try {
                val prompt = """Дай 5 примеров использования слова или фразы «$currentHebrew» (иврит) в предложениях.
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
                _examplesState.value = ExamplesState.Done(content)
            } catch (e: Exception) {
                _examplesState.value = ExamplesState.Error(e.message ?: "Ошибка")
            }
        }
    }

    fun saveCard() {
        if (currentHebrew.isBlank() || selectedTranslation.isBlank()) return
        viewModelScope.launch {
            repository.insert(Card(hebrew = currentHebrew, russian = selectedTranslation))
            _cardSaved.value = true
        }
    }

    fun onCardSavedHandled() { _cardSaved.value = false }

    private fun parseVariants(content: String): List<String> {
        val lines = content.lines()
        val numbered = lines.filter { it.matches(Regex("^\\d+[.)].+")) }
        return if (numbered.size > 1) {
            numbered.map { it.replace(Regex("^\\d+[.)]\\s*"), "").trim() }
        } else {
            listOf(content.trim())
        }
    }
}

sealed class TranslationState {
    object Idle : TranslationState()
    object Loading : TranslationState()
    data class SingleTranslation(val text: String) : TranslationState()
    data class MultipleVariants(val variants: List<String>) : TranslationState()
    data class Error(val message: String) : TranslationState()
}

sealed class ExamplesState {
    object Idle : ExamplesState()
    object Loading : ExamplesState()
    data class Done(val text: String) : ExamplesState()
    data class Error(val message: String) : ExamplesState()
}
