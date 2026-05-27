package com.example.hebrew.ui.learning

import android.Manifest
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.hebrew.R
import com.example.hebrew.api.TransliterationHelper
import com.example.hebrew.databinding.FragmentLearningBinding
import com.example.hebrew.databinding.ItemExampleBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class LearningFragment : Fragment() {

    private var _binding: FragmentLearningBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LearningViewModel by viewModels()
    private var tts: TextToSpeech? = null
    private var isEditMode = false
    private var editSpeechRecognizer: SpeechRecognizer? = null
    private var saveJob: Job? = null

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCardVoiceRecognition()
        else Toast.makeText(requireContext(), "Нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initTts()
        setupSwipeAndTap()
        setupButtons()
        setupEditButtons()
        observeState()
    }

    // ── TTS ──────────────────────────────────────────────────────────────────

    private fun initTts() {
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                var result = tts?.setLanguage(Locale("iw"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    result = tts?.setLanguage(Locale("he"))
                }
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    tts?.setLanguage(Locale.getDefault())
                }
            }
        }
    }

    private val slowKeys = mutableSetOf<String>()

    private fun speakToggle(key: String, text: String) {
        val isSlow = slowKeys.contains(key)
        tts?.setSpeechRate(if (isSlow) 0.5f else 1.0f)
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        if (result == TextToSpeech.SUCCESS) {
            if (isSlow) slowKeys.remove(key) else slowKeys.add(key)
        }
    }

    // ── Buttons ──────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnKnown.setOnClickListener {
            saveEditIfNeeded()
            animateSwipe(toRight = true) { viewModel.markKnown() }
        }
        binding.btnUnknown.setOnClickListener {
            saveEditIfNeeded()
            animateSwipe(toRight = false) { viewModel.markUnknown() }
        }
        binding.btnRestartLearning.setOnClickListener { viewModel.restartLearning() }
        binding.btnSpeak.setOnClickListener {
            val card = (viewModel.state.value as? LearningState.ShowCard)?.card ?: return@setOnClickListener
            speakToggle("main", card.hebrew)
        }
        binding.btnTransliterate.setOnClickListener {
            val card = (viewModel.state.value as? LearningState.ShowCard)?.card ?: return@setOnClickListener
            transliterateToggle(binding.tvTransliteration, card.hebrew)
        }
        binding.btnExamples.setOnClickListener { viewModel.loadExamples() }

        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = if (checkedId == R.id.btnModeMemorize) LearningMode.MEMORIZE else LearningMode.REVIEW
            viewModel.setMode(mode)
        }

        viewModel.mode.observe(viewLifecycleOwner) { mode ->
            val id = if (mode == LearningMode.MEMORIZE) R.id.btnModeMemorize else R.id.btnModeReview
            if (binding.toggleMode.checkedButtonId != id) binding.toggleMode.check(id)
        }

        viewModel.memorizedCount.observe(viewLifecycleOwner) { count ->
            binding.tvMemorized.text = if (count > 0) getString(R.string.memorized_count, count) else ""
        }
    }

    // ── Edit card Russian text ────────────────────────────────────────────────

    private fun setupEditButtons() {
        binding.btnEditRussian.setOnClickListener {
            if (isEditMode) exitEditMode() else enterEditMode()
        }

        binding.btnVoiceEditRussian.setOnClickListener {
            startCardVoiceInput()
        }

        binding.etRussianWord.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isEditMode) return
                saveJob?.cancel()
                saveJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(500)
                    val b = _binding ?: return@launch
                    val text = b.etRussianWord.text.toString().trim()
                    if (text.isNotBlank()) viewModel.updateRussian(text)
                }
            }
        })

        binding.etRussianWord.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && isEditMode) exitEditMode()
        }
    }

    private fun enterEditMode() {
        isEditMode = true
        binding.etRussianWord.apply {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            setSelection(text.length)
        }
        binding.tvFlipBackHint.visibility = View.GONE
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etRussianWord, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun exitEditMode() {
        saveJob?.cancel()
        val text = binding.etRussianWord.text.toString().trim()
        if (text.isNotBlank()) viewModel.updateRussian(text)
        resetEditModeNoSave()
    }

    private fun resetEditModeNoSave() {
        isEditMode = false
        saveJob?.cancel()
        if (_binding == null) return
        binding.etRussianWord.apply {
            clearFocus()
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        binding.tvFlipBackHint.visibility = View.VISIBLE
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etRussianWord.windowToken, 0)
    }

    private fun saveEditIfNeeded() {
        if (!isEditMode) return
        exitEditMode()
    }

    // ── Voice input for card edit ─────────────────────────────────────────────

    private fun startCardVoiceInput() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCardVoiceRecognition()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startCardVoiceRecognition() {
        editSpeechRecognizer?.destroy()
        editSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        editSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    binding.etRussianWord.setText(text)
                    viewModel.updateRussian(text)
                }
            }
            override fun onError(error: Int) {
                Toast.makeText(requireContext(), getString(R.string.error_voice), Toast.LENGTH_SHORT).show()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        editSpeechRecognizer?.startListening(intent)
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeState() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LearningState.Loading -> {
                    binding.scrollContent.visibility = View.GONE
                    binding.buttonBar.visibility = View.GONE
                    binding.layoutAllLearned.visibility = View.GONE
                    binding.tvProgress.text = ""
                    binding.tvSwipeHint.visibility = View.GONE
                }
                is LearningState.AllLearned -> {
                    binding.scrollContent.visibility = View.GONE
                    binding.buttonBar.visibility = View.GONE
                    binding.layoutAllLearned.visibility = View.VISIBLE
                    binding.tvSwipeHint.visibility = View.GONE
                }
                is LearningState.ShowCard -> {
                    if (isEditMode) resetEditModeNoSave()
                    slowKeys.clear()
                    binding.layoutAllLearned.visibility = View.GONE
                    binding.scrollContent.visibility = View.VISIBLE
                    binding.buttonBar.visibility = View.VISIBLE
                    binding.tvSwipeHint.visibility = View.VISIBLE
                    binding.tvProgress.text =
                        getString(R.string.learning_progress, state.current, state.total)
                    binding.tvHebrewWord.text = state.card.hebrew
                    binding.etRussianWord.setText(state.card.russian)
                    binding.tvTransliteration.visibility = View.GONE
                    binding.tvTransliteration.text = ""
                    if (state.isFlipped) showBack() else showFront()
                }
            }
        }

        viewModel.examplesState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ExamplesState.Idle -> {
                    binding.progressExamples.visibility = View.GONE
                    binding.examplesContainer.removeAllViews()
                }
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
        binding.scrollContent.post {
            binding.scrollContent.smoothScrollTo(0, binding.examplesContainer.top)
        }
    }

    // ── Card display ──────────────────────────────────────────────────────────

    private fun showFront() {
        binding.cardFront.visibility = View.VISIBLE
        binding.cardBack.visibility = View.GONE
        binding.cardFront.rotationY = 0f
    }

    private fun showBack() {
        binding.cardFront.visibility = View.GONE
        binding.cardBack.visibility = View.VISIBLE
        binding.cardBack.rotationY = 0f
    }

    // ── Card flip animations ──────────────────────────────────────────────────

    private fun handleCardTap() {
        val state = viewModel.state.value as? LearningState.ShowCard ?: return
        if (!state.isFlipped) animateFlipToBack() else animateFlipToFront()
    }

    private fun animateFlipToBack() {
        val density = resources.displayMetrics.density
        binding.cardFront.cameraDistance = 8000 * density
        binding.cardBack.cameraDistance = 8000 * density

        val flipOut = ObjectAnimator.ofFloat(binding.cardFront, "rotationY", 0f, -90f).apply { duration = 180 }
        val flipIn  = ObjectAnimator.ofFloat(binding.cardBack,  "rotationY", 90f, 0f).apply { duration = 180 }

        flipOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                viewModel.toggleFlip()
                binding.cardFront.visibility = View.GONE
                binding.cardBack.visibility  = View.VISIBLE
                flipIn.start()
            }
        })
        flipOut.start()
    }

    private fun animateFlipToFront() {
        val density = resources.displayMetrics.density
        binding.cardFront.cameraDistance = 8000 * density
        binding.cardBack.cameraDistance = 8000 * density

        val flipOut = ObjectAnimator.ofFloat(binding.cardBack,  "rotationY", 0f, 90f).apply { duration = 180 }
        val flipIn  = ObjectAnimator.ofFloat(binding.cardFront, "rotationY", -90f, 0f).apply { duration = 180 }

        flipOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                viewModel.toggleFlip()
                binding.cardBack.visibility  = View.GONE
                binding.cardFront.visibility = View.VISIBLE
                flipIn.start()
            }
        })
        flipOut.start()
    }

    // ── Gesture (swipe + tap) ─────────────────────────────────────────────────

    private fun setupSwipeAndTap() {
        val detector = GestureDetector(requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                private val SWIPE_THRESHOLD = 80
                private val SWIPE_VELOCITY  = 80

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    handleCardTap()
                    return true
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val startX = e1?.x ?: return false
                    val diffX  = e2.x - startX
                    val diffY  = e2.y - (e1.y)
                    if (abs(diffX) > abs(diffY) &&
                        abs(diffX) > SWIPE_THRESHOLD &&
                        abs(velocityX) > SWIPE_VELOCITY
                    ) {
                        if (diffX > 0) {
                            saveEditIfNeeded()
                            animateSwipe(true)  { viewModel.markKnown() }
                        } else {
                            saveEditIfNeeded()
                            animateSwipe(false) { viewModel.markUnknown() }
                        }
                        return true
                    }
                    return false
                }
            })

        val touchListener = View.OnTouchListener { _, event ->
            if (isEditMode) false
            else { detector.onTouchEvent(event); true }
        }
        binding.cardContainer.setOnTouchListener(touchListener)
        binding.cardFront.setOnTouchListener(touchListener)
        binding.cardBack.setOnTouchListener(touchListener)
    }

    // ── Swipe animation ───────────────────────────────────────────────────────

    private fun animateSwipe(toRight: Boolean, onEnd: () -> Unit) {
        val direction   = if (toRight) 1f else -1f
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()

        val slide = ObjectAnimator.ofFloat(
            binding.cardContainer, "translationX", 0f, direction * screenWidth
        ).apply { duration = 250 }
        val fade = ObjectAnimator.ofFloat(binding.cardContainer, "alpha", 1f, 0f).apply { duration = 250 }

        AnimatorSet().apply {
            playTogether(slide, fade)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    binding.cardContainer.translationX = 0f
                    binding.cardContainer.alpha = 1f
                    onEnd()
                }
            })
            start()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onDestroyView() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        saveJob?.cancel()
        editSpeechRecognizer?.destroy()
        editSpeechRecognizer = null
        super.onDestroyView()
        _binding = null
    }
}
