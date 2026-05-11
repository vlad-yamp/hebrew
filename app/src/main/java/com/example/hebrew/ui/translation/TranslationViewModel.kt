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
import com.example.hebrew.data.HistoryEntry
import com.example.hebrew.ui.learning.ExampleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TranslationViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as HebrewApplication).repository
    private val historyRepository = (app as HebrewApplication).historyRepository
    private val prefs = app.getSharedPreferences("hebrew_prefs", Context.MODE_PRIVATE)

    private val _translationState = MutableLiveData<TranslationState>(TranslationState.Idle)
    val translationState: LiveData<TranslationState> = _translationState

    private val _examplesState = MutableLiveData<ExamplesState>(ExamplesState.Idle)
    val examplesState: LiveData<ExamplesState> = _examplesState

    private val _analysisState = MutableLiveData<AnalysisState>(AnalysisState.Idle)
    val analysisState: LiveData<AnalysisState> = _analysisState

    private val _cardSaved = MutableLiveData(false)
    val cardSaved: LiveData<Boolean> = _cardSaved

    private val _duplicateCard = MutableLiveData(false)
    val duplicateCard: LiveData<Boolean> = _duplicateCard

    // inputText — what the user spoke/typed
    // selectedVariant — chosen translation (Russian when isHebrewInput, Hebrew when !isHebrewInput)
    private var inputText: String = ""
    var isHebrewInput: Boolean = true
        private set
    private var selectedVariant: String = ""

    // Always the Hebrew text, for TTS and examples
    val currentHebrew: String get() = if (isHebrewInput) inputText else selectedVariant

    fun translate(text: String, isHebrewInput: Boolean, gender: String? = null) {
        this.isHebrewInput = isHebrewInput
        this.inputText = text
        this.selectedVariant = ""
        _analysisState.value = AnalysisState.Idle

        val apiKey = prefs.getString("openai_api_key", "") ?: ""
        if (apiKey.isBlank()) {
            _translationState.value = TranslationState.Error("Введите API ключ в настройках")
            return
        }

        _translationState.value = TranslationState.Loading
        viewModelScope.launch {
            try {
                val prompt = if (isHebrewInput) {
                    """Переведи следующее слово или фразу с иврита на русский язык.
Если есть несколько значений, дай до 5 вариантов перевода, пронумеровав их (1. ... 2. ... и т.д.).
Если значение одно — просто напиши перевод без нумерации.
Только переводы, без объяснений.

Иврит: $text"""
                } else {
                    val genderHint = when (gender) {
                        "female" -> "\nАдресат — женщина, используй женскую форму обращения."
                        "male" -> "\nАдресат — мужчина, используй мужскую форму обращения."
                        else -> ""
                    }
                    """Переведи следующее слово или фразу с русского на иврит.$genderHint
Если есть несколько вариантов написания или значений, дай до 5 вариантов, пронумеровав их (1. ... 2. ... и т.д.).
Если вариант один — просто напиши перевод без нумерации.
Только переводы на иврите, без транслитерации и объяснений.

Русский: $text"""
                }

                val fullText = StringBuilder()
                withContext(Dispatchers.IO) {
                    val response = OpenAIClient.service.getCompletionStream(
                        auth = "Bearer $apiKey",
                        request = ChatRequest(
                            messages = listOf(ChatMessage("user", prompt)),
                            max_tokens = 200,
                            stream = true
                        )
                    )
                    val body = response.body() ?: throw Exception("Empty response")
                    body.source().use { source ->
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data: ")) continue
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") break
                            try {
                                val token = JSONObject(data)
                                    .getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("delta")
                                    .optString("content", "")
                                if (token.isNotEmpty()) {
                                    fullText.append(token)
                                    _translationState.postValue(TranslationState.Streaming(fullText.toString()))
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                val content = fullText.toString().trim()
                val variants = parseVariants(content)
                val firstVariant: String
                if (variants.size > 1) {
                    firstVariant = variants[0]
                    selectedVariant = firstVariant
                    _translationState.value = TranslationState.MultipleVariants(variants)
                } else {
                    firstVariant = variants.firstOrNull() ?: content
                    selectedVariant = firstVariant
                    _translationState.value = TranslationState.SingleTranslation(firstVariant)
                }
                saveToHistory(text, isHebrewInput, firstVariant)
            } catch (e: Exception) {
                _translationState.value = TranslationState.Error(e.message ?: "Ошибка")
            }
        }
    }

    fun selectVariant(text: String) {
        selectedVariant = text
    }

    private fun saveToHistory(inputText: String, isHebrewInput: Boolean, translation: String) {
        if (translation.isBlank()) return
        val hebrew = if (isHebrewInput) inputText else translation
        val russian = if (isHebrewInput) translation else inputText
        if (hebrew.isBlank() || russian.isBlank()) return
        val maxCount = prefs.getInt("history_count", 30)
        viewModelScope.launch {
            historyRepository.insert(HistoryEntry(hebrew = hebrew, russian = russian))
            historyRepository.trimToMax(maxCount)
        }
    }

    fun loadExamples() {
        val apiKey = prefs.getString("openai_api_key", "") ?: ""
        if (apiKey.isBlank()) return
        val hebrew = currentHebrew
        if (hebrew.isBlank()) return

        val examplesCount = prefs.getInt("examples_count", 5)
        _examplesState.value = ExamplesState.Loading
        viewModelScope.launch {
            try {
                val prompt = """Дай $examplesCount примеров использования слова или фразы «$hebrew» (иврит) в предложениях.
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

    fun saveCard() {
        val hebrew = if (isHebrewInput) inputText else selectedVariant
        val russian = if (isHebrewInput) selectedVariant else inputText
        if (hebrew.isBlank() || russian.isBlank()) return
        viewModelScope.launch {
            if (repository.findByHebrew(hebrew) != null) {
                _duplicateCard.value = true
                return@launch
            }
            repository.insert(Card(hebrew = hebrew, russian = russian))
            _cardSaved.value = true
        }
    }

    fun onCardSavedHandled() { _cardSaved.value = false }
    fun onDuplicateHandled() { _duplicateCard.value = false }

    fun updateHistoryRussian(newRussian: String) {
        if (!isHebrewInput) return
        viewModelScope.launch {
            historyRepository.updateRussianByHebrew(inputText, newRussian)
        }
    }

    fun loadConjugation() = loadAnalysis("conj")
    fun loadSyntaxAnalysis() = loadAnalysis("syntax")
    fun clearAnalysis() { _analysisState.value = AnalysisState.Idle }

    private fun loadAnalysis(type: String) {
        val hebrew = currentHebrew
        if (hebrew.isBlank()) return
        val apiKey = prefs.getString("openai_api_key", "") ?: ""
        if (apiKey.isBlank()) return

        _analysisState.value = AnalysisState.Loading
        viewModelScope.launch {
            try {
                val prompt = if (type == "conj") {
                    """В фразе на иврите «$hebrew» найди все глаголы. Для каждого глагола отдельно проспрягай его во ВСЕХ временах: прошедшее (עבר), настоящее (הווה), будущее (עתיד) и повелительное (ציווי) — по всем лицам и числам. Для каждой формы укажи форму на иврите и транслитерацию русскими буквами в скобках. Используй маркированный список, без таблиц. Ответ на русском языке."""
                } else {
                    """Сделай синтаксический разбор фразы на иврите «$hebrew»: для каждого слова укажи само слово на иврите, его перевод на русский, часть речи и синтаксическую функцию в предложении. Оформи как нумерованный список. Ответ на русском языке."""
                }
                val response = OpenAIClient.service.getCompletion(
                    auth = "Bearer $apiKey",
                    request = ChatRequest(
                        messages = listOf(ChatMessage("user", prompt)),
                        max_tokens = 800
                    )
                )
                val content = response.choices.firstOrNull()?.message?.content?.trim() ?: ""
                _analysisState.value = AnalysisState.Done(type, content)
            } catch (e: Exception) {
                _analysisState.value = AnalysisState.Error(e.message ?: "Ошибка")
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
    data class Streaming(val partialText: String) : TranslationState()
    data class SingleTranslation(val text: String) : TranslationState()
    data class MultipleVariants(val variants: List<String>) : TranslationState()
    data class Error(val message: String) : TranslationState()
}

sealed class AnalysisState {
    object Idle : AnalysisState()
    object Loading : AnalysisState()
    data class Done(val type: String, val text: String) : AnalysisState()
    data class Error(val message: String) : AnalysisState()
}

sealed class ExamplesState {
    object Idle : ExamplesState()
    object Loading : ExamplesState()
    data class Done(val examples: List<ExampleItem>) : ExamplesState()
    data class Error(val message: String) : ExamplesState()
}
