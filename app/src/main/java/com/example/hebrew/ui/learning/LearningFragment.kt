package com.example.hebrew.ui.learning

import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.hebrew.R
import com.example.hebrew.api.TransliterationHelper
import com.example.hebrew.ui.learning.LearningMode
import com.example.hebrew.databinding.FragmentLearningBinding
import com.example.hebrew.databinding.ItemExampleBinding
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class LearningFragment : Fragment() {

    private var _binding: FragmentLearningBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LearningViewModel by viewModels()
    private var tts: TextToSpeech? = null

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
            animateSwipe(toRight = true) { viewModel.markKnown() }
        }
        binding.btnUnknown.setOnClickListener {
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
                    slowKeys.clear()
                    binding.layoutAllLearned.visibility = View.GONE
                    binding.scrollContent.visibility = View.VISIBLE
                    binding.buttonBar.visibility = View.VISIBLE
                    binding.tvSwipeHint.visibility = View.VISIBLE
                    binding.tvProgress.text =
                        getString(R.string.learning_progress, state.current, state.total)
                    binding.tvHebrewWord.text = state.card.hebrew
                    binding.tvRussianWord.text = state.card.russian
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
        // Scroll to show examples
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
                viewModel.toggleFlip()           // sets isFlipped = true → observer shows back
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
                viewModel.toggleFlip()           // sets isFlipped = false → observer shows front
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
                        if (diffX > 0) animateSwipe(true)  { viewModel.markKnown() }
                        else           animateSwipe(false) { viewModel.markUnknown() }
                        return true
                    }
                    return false
                }
            })

        // Return true always so Android delivers the full gesture sequence (MOVE, UP)
        val touchListener = View.OnTouchListener { _, event ->
            detector.onTouchEvent(event)
            true
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
        super.onDestroyView()
        _binding = null
    }
}
