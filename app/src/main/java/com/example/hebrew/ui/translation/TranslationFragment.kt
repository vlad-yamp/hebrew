package com.example.hebrew.ui.translation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.hebrew.R
import com.example.hebrew.api.TransliterationHelper
import com.example.hebrew.databinding.FragmentTranslationBinding
import com.example.hebrew.databinding.ItemExampleBinding
import com.example.hebrew.ui.learning.ExampleItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class TranslationFragment : Fragment() {

    private var _binding: FragmentTranslationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TranslationViewModel by viewModels()
    private var tts: TextToSpeech? = null
    private val slowKeys = mutableSetOf<String>()

    private var isHebrewInput = true
    private var inputText = ""
    private var gender: String? = null
    private var suppressTranslationSave = false
    private var editJob: Job? = null

    private var voiceSpeechRecognizer: SpeechRecognizer? = null
    private var pendingVoiceButton: android.widget.ImageButton? = null

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val btn = pendingVoiceButton ?: return@registerForActivityResult
        pendingVoiceButton = null
        if (granted) startTranslationVoiceInput(btn)
        else Toast.makeText(requireContext(), "Нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTranslationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isHebrewInput = arguments?.getBoolean("isHebrewInput", true) ?: true
        inputText = arguments?.getString("inputText") ?: arguments?.getString("hebrewText") ?: ""
        gender = arguments?.getString("gender")

        applyInputLanguage()
        initTts()
        setupObservers()
        setupClickListeners()

        if (viewModel.translationState.value is TranslationState.Idle) {
            viewModel.translate(inputText, isHebrewInput, gender)
        }
    }

    private fun applyInputLanguage() {
        binding.etInputText.setText(inputText)

        if (isHebrewInput) {
            binding.tvInputLabel.text = "Иврит"
            binding.etInputText.textDirection = View.TEXT_DIRECTION_RTL
            binding.etInputText.gravity = Gravity.END
            binding.tvTranslationLabel.text = "Перевод"
            binding.tvSingleTranslation.textDirection = View.TEXT_DIRECTION_LOCALE
            binding.tvSingleTranslation.gravity = Gravity.START
            binding.btnSpeakHebrew.visibility = View.VISIBLE
            binding.btnTransliterateHebrew.visibility = View.VISIBLE
            binding.btnConjugate.visibility = View.VISIBLE
            binding.btnSyntax.visibility = View.VISIBLE
            binding.hebrewActionsBar.visibility = View.GONE
            binding.btnVoiceTranslation.visibility = View.VISIBLE
            binding.btnVoiceVariants.visibility = View.VISIBLE
        } else {
            binding.tvInputLabel.text = "Русский"
            binding.etInputText.textDirection = View.TEXT_DIRECTION_LOCALE
            binding.etInputText.gravity = Gravity.START
            binding.tvTranslationLabel.text = "Иврит"
            binding.tvSingleTranslation.textDirection = View.TEXT_DIRECTION_RTL
            binding.tvSingleTranslation.gravity = Gravity.END
            binding.btnSpeakHebrew.visibility = View.GONE
            binding.btnTransliterateHebrew.visibility = View.GONE
            binding.btnConjugate.visibility = View.GONE
            binding.btnSyntax.visibility = View.GONE
            binding.hebrewActionsBar.visibility = View.GONE
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
            }
        }
    }

    private fun setupObservers() {
        viewModel.translationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is TranslationState.Idle -> {}

                is TranslationState.Loading -> {
                    binding.btnRetranslate.isEnabled = false
                    binding.btnRetranslate.alpha = 0.3f
                    binding.progressTranslation.visibility = View.VISIBLE
                    binding.cardSingleTranslation.visibility = View.GONE
                    binding.cardVariants.visibility = View.GONE
                    binding.hebrewActionsBar.visibility = View.GONE
                    binding.btnExamples.visibility = View.GONE
                    binding.btnSaveCard.visibility = View.GONE
                    binding.btnNewInput.visibility = View.GONE
                    binding.progressExamples.visibility = View.GONE
                    binding.examplesContainer.removeAllViews()
                    binding.tvTransliterationInput.visibility = View.GONE
                    binding.tvTransliterationInput.text = ""
                    binding.tvTransliterationResult.visibility = View.GONE
                    binding.tvTransliterationResult.text = ""
                }

                is TranslationState.Streaming -> {
                    binding.btnRetranslate.isEnabled = false
                    binding.btnRetranslate.alpha = 0.3f
                    binding.progressTranslation.visibility = View.GONE
                    binding.cardSingleTranslation.visibility = View.VISIBLE
                    suppressTranslationSave = true
                    binding.tvSingleTranslation.setText(state.partialText)
                    suppressTranslationSave = false
                    binding.cardVariants.visibility = View.GONE
                    binding.btnExamples.visibility = View.GONE
                    binding.btnSaveCard.visibility = View.GONE
                    binding.btnNewInput.visibility = View.GONE
                }

                is TranslationState.SingleTranslation -> {
                    binding.btnRetranslate.isEnabled = true
                    binding.btnRetranslate.alpha = 1f
                    binding.progressTranslation.visibility = View.GONE
                    binding.cardSingleTranslation.visibility = View.VISIBLE
                    suppressTranslationSave = true
                    binding.tvSingleTranslation.setText(state.text)
                    suppressTranslationSave = false
                    binding.cardVariants.visibility = View.GONE
                    binding.btnExamples.visibility = View.VISIBLE
                    binding.btnSaveCard.visibility = View.VISIBLE
                    binding.btnNewInput.visibility = View.VISIBLE
                    if (!isHebrewInput) binding.hebrewActionsBar.visibility = View.VISIBLE
                }

                is TranslationState.MultipleVariants -> {
                    binding.btnRetranslate.isEnabled = true
                    binding.btnRetranslate.alpha = 1f
                    binding.progressTranslation.visibility = View.GONE
                    binding.cardSingleTranslation.visibility = View.GONE
                    binding.cardVariants.visibility = View.VISIBLE
                    binding.btnExamples.visibility = View.VISIBLE
                    binding.btnSaveCard.visibility = View.VISIBLE
                    binding.btnNewInput.visibility = View.VISIBLE
                    if (!isHebrewInput) binding.hebrewActionsBar.visibility = View.VISIBLE
                    populateVariants(state.variants)
                }

                is TranslationState.Error -> {
                    binding.btnRetranslate.isEnabled = true
                    binding.btnRetranslate.alpha = 1f
                    binding.progressTranslation.visibility = View.GONE
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    binding.btnNewInput.visibility = View.VISIBLE
                }
            }
        }

        viewModel.examplesState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ExamplesState.Idle -> {}
                is ExamplesState.Loading -> {
                    binding.progressExamples.visibility = View.VISIBLE
                    binding.examplesContainer.removeAllViews()
                }
                is ExamplesState.Done -> {
                    binding.progressExamples.visibility = View.GONE
                    showExamples(state.examples)
                }
                is ExamplesState.Error -> {
                    binding.progressExamples.visibility = View.GONE
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.analysisState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AnalysisState.Idle -> {}
                is AnalysisState.Loading -> {}
                is AnalysisState.Done -> {
                    val title = getString(
                        if (state.type == "conj") R.string.analysis_conjugation_title
                        else R.string.analysis_syntax_title
                    )
                    val sheet = childFragmentManager.findFragmentByTag("analysis") as? AnalysisBottomSheet
                    if (sheet != null && sheet.isAdded) {
                        sheet.setContent(title, state.text)
                    } else {
                        showAnalysisSheet(state.type, state.text)
                    }
                }
                is AnalysisState.Error -> {
                    val sheet = childFragmentManager.findFragmentByTag("analysis") as? AnalysisBottomSheet
                    if (sheet != null && sheet.isAdded) {
                        sheet.showError(state.message)
                    } else {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewModel.cardSaved.observe(viewLifecycleOwner) { saved ->
            if (saved) {
                viewModel.onCardSavedHandled()
                Toast.makeText(requireContext(), getString(R.string.card_saved), Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }

        viewModel.duplicateCard.observe(viewLifecycleOwner) { duplicate ->
            if (duplicate) {
                viewModel.onDuplicateHandled()
                Toast.makeText(requireContext(), getString(R.string.card_duplicate), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun speakToggle(key: String, text: String) {
        val isSlow = slowKeys.contains(key)
        tts?.setSpeechRate(if (isSlow) 0.5f else 1.0f)
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        if (result == TextToSpeech.SUCCESS) {
            if (isSlow) slowKeys.remove(key) else slowKeys.add(key)
        }
    }

    private fun transliterateToggle(tv: android.widget.TextView, hebrewText: String) {
        if (tv.visibility == View.VISIBLE) { tv.visibility = View.GONE; return }
        if (tv.text.isNotEmpty()) { tv.visibility = View.VISIBLE; return }
        val apiKey = requireContext()
            .getSharedPreferences("hebrew_prefs", Context.MODE_PRIVATE)
            .getString("openai_api_key", "") ?: ""
        if (apiKey.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { TransliterationHelper.transliterate(apiKey, hebrewText) }
                .onSuccess { tv.text = it; tv.visibility = View.VISIBLE }
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
            itemBinding.btnTransliterateExample.setOnClickListener {
                transliterateToggle(itemBinding.tvTransliterationExample, item.hebrew)
            }
            binding.examplesContainer.addView(itemBinding.root)
        }
    }

    private fun setupClickListeners() {
        binding.btnRetranslate.setOnClickListener {
            val text = binding.etInputText.text.toString().trim()
            if (text.isNotBlank()) viewModel.translate(text, isHebrewInput, gender)
        }
        binding.btnSpeakHebrew.setOnClickListener {
            val text = binding.etInputText.text.toString().trim()
            if (text.isNotBlank()) speakToggle("input", text)
        }
        binding.btnTransliterateHebrew.setOnClickListener {
            val text = binding.etInputText.text.toString().trim()
            if (text.isNotBlank()) transliterateToggle(binding.tvTransliterationInput, text)
        }
        binding.btnSpeakResult.setOnClickListener {
            val hebrew = viewModel.currentHebrew
            if (hebrew.isNotBlank()) speakToggle("result", hebrew)
        }
        binding.btnTransliterateResult.setOnClickListener {
            val hebrew = viewModel.currentHebrew
            if (hebrew.isNotBlank()) transliterateToggle(binding.tvTransliterationResult, hebrew)
        }
        binding.btnCopyHebrew.setOnClickListener {
            val hebrew = viewModel.currentHebrew
            if (hebrew.isNotBlank()) {
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Hebrew", hebrew))
                Toast.makeText(requireContext(), "Скопировано", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnShareHebrew.setOnClickListener {
            val hebrew = viewModel.currentHebrew
            if (hebrew.isNotBlank()) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, hebrew)
                }
                startActivity(Intent.createChooser(intent, "Поделиться"))
            }
        }
        binding.btnConjugate.setOnClickListener { onConjugateClick() }
        binding.btnConjugateResult.setOnClickListener { onConjugateClick() }
        binding.btnSyntax.setOnClickListener { onSyntaxClick() }
        binding.btnSyntaxResult.setOnClickListener { onSyntaxClick() }
        binding.btnExamples.setOnClickListener { viewModel.loadExamples() }
        binding.btnSaveCard.setOnClickListener {
            val checked = binding.radioGroupVariants.checkedRadioButtonId
            if (binding.cardVariants.visibility == View.VISIBLE && checked == -1) {
                Toast.makeText(requireContext(), "Выберите перевод", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (checked != -1) {
                val rb = binding.radioGroupVariants.findViewById<RadioButton>(checked)
                viewModel.selectVariant(rb.text.toString())
            }
            viewModel.saveCard()
        }
        binding.btnNewInput.setOnClickListener { findNavController().popBackStack() }
        listOf(binding.btnVoiceTranslation, binding.btnVoiceVariants).forEach { btn ->
            btn.setOnClickListener {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) startTranslationVoiceInput(btn as android.widget.ImageButton)
                else {
                    pendingVoiceButton = btn as android.widget.ImageButton
                    requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        binding.tvSingleTranslation.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (suppressTranslationSave) return
                val newText = s?.toString()?.trim() ?: return
                if (newText.isBlank()) return
                editJob?.cancel()
                editJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(800)
                    viewModel.updateHistoryRussian(newText)
                    viewModel.selectVariant(newText)
                }
            }
        })
    }

    private fun startTranslationVoiceInput(sourceBtn: android.widget.ImageButton) {
        voiceSpeechRecognizer?.destroy()
        voiceSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())

        val primaryColor = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
        val greyColor = ContextCompat.getColor(requireContext(), R.color.grey_medium)
        sourceBtn.imageTintList = android.content.res.ColorStateList.valueOf(primaryColor)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        voiceSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    suppressTranslationSave = true
                    binding.tvSingleTranslation.setText(text)
                    suppressTranslationSave = false
                    binding.cardSingleTranslation.visibility = View.VISIBLE
                    binding.cardVariants.visibility = View.GONE
                    viewModel.updateHistoryRussian(text)
                    viewModel.selectVariant(text)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.error_no_speech), Toast.LENGTH_SHORT).show()
                }
                if (_binding != null) sourceBtn.imageTintList = android.content.res.ColorStateList.valueOf(greyColor)
            }

            override fun onError(error: Int) {
                if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                    error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    Toast.makeText(requireContext(), getString(R.string.error_voice), Toast.LENGTH_SHORT).show()
                }
                if (_binding != null) sourceBtn.imageTintList = android.content.res.ColorStateList.valueOf(greyColor)
            }
        })

        voiceSpeechRecognizer?.startListening(intent)
    }

    private fun onConjugateClick() {
        val s = viewModel.analysisState.value
        if (s is AnalysisState.Done && s.type == "conj") {
            showAnalysisSheet(s.type, s.text)
            return
        }
        if (s !is AnalysisState.Loading) viewModel.loadConjugation()
        ensureAnalysisSheetShown()
    }

    private fun onSyntaxClick() {
        val s = viewModel.analysisState.value
        if (s is AnalysisState.Done && s.type == "syntax") {
            showAnalysisSheet(s.type, s.text)
            return
        }
        if (s !is AnalysisState.Loading) viewModel.loadSyntaxAnalysis()
        ensureAnalysisSheetShown()
    }

    private fun ensureAnalysisSheetShown() {
        val existing = childFragmentManager.findFragmentByTag("analysis")
        if (existing == null || !existing.isAdded) {
            AnalysisBottomSheet.newInstance().show(childFragmentManager, "analysis")
        }
    }

    private fun showAnalysisSheet(type: String, content: String) {
        val title = getString(
            if (type == "conj") R.string.analysis_conjugation_title
            else R.string.analysis_syntax_title
        )
        AnalysisBottomSheet.newInstance(title, content)
            .show(childFragmentManager, "analysis")
    }

    private fun populateVariants(variants: List<String>) {
        binding.radioGroupVariants.removeAllViews()
        variants.forEachIndexed { index, text ->
            val rb = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                this.text = text
                textSize = 16f
                setPadding(8, 8, 8, 8)
                if (!isHebrewInput) {
                    textDirection = View.TEXT_DIRECTION_RTL
                    gravity = Gravity.END
                }
                if (index == 0) isChecked = true
            }
            binding.radioGroupVariants.addView(rb)
        }
        if (variants.isNotEmpty()) viewModel.selectVariant(variants[0])

        binding.radioGroupVariants.setOnCheckedChangeListener { group, checkedId ->
            val rb = group.findViewById<RadioButton>(checkedId)
            viewModel.selectVariant(rb?.text?.toString() ?: "")
        }
    }

    override fun onDestroyView() {
        voiceSpeechRecognizer?.destroy()
        voiceSpeechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroyView()
        _binding = null
    }
}
