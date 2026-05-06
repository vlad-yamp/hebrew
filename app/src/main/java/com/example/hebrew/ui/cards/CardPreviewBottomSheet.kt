package com.example.hebrew.ui.cards

import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.hebrew.api.ChatMessage
import com.example.hebrew.api.ChatRequest
import com.example.hebrew.api.OpenAIClient
import com.example.hebrew.data.Card
import com.example.hebrew.databinding.BottomSheetCardPreviewBinding
import com.example.hebrew.databinding.ItemExampleBinding
import com.example.hebrew.ui.learning.ExampleItem
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class CardPreviewBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCardPreviewBinding? = null
    private val binding get() = _binding!!

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingSpeak: (() -> Unit)? = null
    private var isFlipped = false
    private val slowKeys = mutableSetOf<String>()

    private fun speakToggle(key: String, text: String) {
        if (!isTtsReady) {
            pendingSpeak = { speakToggle(key, text) }
            return
        }
        val isSlow = slowKeys.contains(key)
        tts?.setSpeechRate(if (isSlow) 0.5f else 1.0f)
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        if (result == TextToSpeech.SUCCESS) {
            if (isSlow) slowKeys.remove(key) else slowKeys.add(key)
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val ARG_HEBREW = "hebrew"
        private const val ARG_RUSSIAN = "russian"

        fun newInstance(card: Card) = CardPreviewBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_HEBREW, card.hebrew)
                putString(ARG_RUSSIAN, card.russian)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCardPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val hebrew = arguments?.getString(ARG_HEBREW) ?: ""
        val russian = arguments?.getString(ARG_RUSSIAN) ?: ""

        binding.tvHebrewWord.text = hebrew
        binding.tvRussianWord.text = russian

        initTts()

        binding.btnSpeak.setOnClickListener {
            speakToggle("main", hebrew)
        }

        binding.btnExamples.setOnClickListener {
            loadExamples(hebrew)
        }

        val tapListener = View.OnClickListener {
            if (!isFlipped) animateFlipToBack() else animateFlipToFront()
        }
        binding.cardFront.setOnClickListener(tapListener)
        binding.cardBack.setOnClickListener(tapListener)
        binding.cardContainer.setOnClickListener(tapListener)
    }

    private fun loadExamples(hebrew: String) {
        val prefs = requireContext().getSharedPreferences("hebrew_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("openai_api_key", "") ?: ""
        if (apiKey.isBlank()) {
            Toast.makeText(requireContext(), "Введите API ключ в настройках", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressExamples.visibility = View.VISIBLE
        binding.examplesContainer.removeAllViews()

        val examplesCount = prefs.getInt("examples_count", 5)
        scope.launch {
            try {
                val prompt = """Дай $examplesCount примеров использования слова или фразы «$hebrew» (иврит) в предложениях.
Формат каждого примера:
[предложение на иврите]
[перевод на русский]

Разделяй примеры пустой строкой."""
                val response = withContext(Dispatchers.IO) {
                    OpenAIClient.service.getCompletion(
                        auth = "Bearer $apiKey",
                        request = ChatRequest(
                            messages = listOf(ChatMessage("user", prompt)),
                            max_tokens = 1000
                        )
                    )
                }
                val content = response.choices.firstOrNull()?.message?.content?.trim() ?: ""
                val examples = parseExamples(content)
                binding.progressExamples.visibility = View.GONE
                showExamples(examples)
            } catch (e: Exception) {
                binding.progressExamples.visibility = View.GONE
                Toast.makeText(requireContext(), e.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
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

    private fun showExamples(examples: List<ExampleItem>) {
        binding.examplesContainer.removeAllViews()
        examples.forEachIndexed { index, item ->
            val itemBinding = ItemExampleBinding.inflate(
                layoutInflater, binding.examplesContainer, false
            )
            itemBinding.tvExampleHebrew.text = item.hebrew
            itemBinding.tvExampleRussian.text = item.russian
            itemBinding.btnSpeakExample.setOnClickListener { speakToggle("ex_$index", item.hebrew) }
            binding.examplesContainer.addView(itemBinding.root)
        }
    }

    private fun initTts() {
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                var result = tts?.setLanguage(Locale("iw"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = tts?.setLanguage(Locale("he"))
                }
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                isTtsReady = true
                pendingSpeak?.invoke()
                pendingSpeak = null
            }
        }
    }

    private fun animateFlipToBack() {
        val density = resources.displayMetrics.density
        binding.cardFront.cameraDistance = 8000 * density
        binding.cardBack.cameraDistance = 8000 * density

        val flipOut = ObjectAnimator.ofFloat(binding.cardFront, "rotationY", 0f, -90f).apply { duration = 180 }
        val flipIn  = ObjectAnimator.ofFloat(binding.cardBack, "rotationY", 90f, 0f).apply { duration = 180 }

        flipOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                isFlipped = true
                binding.cardFront.visibility = View.GONE
                binding.cardBack.visibility = View.VISIBLE
                flipIn.start()
            }
        })
        flipOut.start()
    }

    private fun animateFlipToFront() {
        val density = resources.displayMetrics.density
        binding.cardFront.cameraDistance = 8000 * density
        binding.cardBack.cameraDistance = 8000 * density

        val flipOut = ObjectAnimator.ofFloat(binding.cardBack, "rotationY", 0f, 90f).apply { duration = 180 }
        val flipIn  = ObjectAnimator.ofFloat(binding.cardFront, "rotationY", -90f, 0f).apply { duration = 180 }

        flipOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                isFlipped = false
                binding.cardBack.visibility = View.GONE
                binding.cardFront.visibility = View.VISIBLE
                flipIn.start()
            }
        })
        flipOut.start()
    }

    override fun onDestroyView() {
        scope.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroyView()
        _binding = null
    }
}
