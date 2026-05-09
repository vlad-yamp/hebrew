package com.example.hebrew.ui.voice

import android.Manifest
import android.animation.ValueAnimator
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.hebrew.R
import com.example.hebrew.databinding.FragmentVoiceBinding

class VoiceFragment : Fragment() {

    private enum class InputMode { MIC, TEXT }

    private var _binding: FragmentVoiceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VoiceViewModel by viewModels()
    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isHebrewInput = true
    private var inputMode = InputMode.MIC
    private val rippleAnimators = mutableListOf<ValueAnimator>()

    private val prefs by lazy {
        requireContext().getSharedPreferences("hebrew_prefs", Context.MODE_PRIVATE)
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
        else Toast.makeText(requireContext(), "Нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isHebrewInput = prefs.getBoolean("default_lang_hebrew", true)
        binding.langToggle.check(if (isHebrewInput) R.id.btnLangHebrew else R.id.btnLangRussian)
        updateHint()

        binding.langToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isHebrewInput = (checkedId == R.id.btnLangHebrew)
                prefs.edit().putBoolean("default_lang_hebrew", isHebrewInput).apply()
                updateHint()
            }
        }

        binding.btnMic.setOnClickListener {
            if (viewModel.state.value is VoiceState.Listening) {
                speechRecognizer?.cancel()
                releaseWakeLock()
                viewModel.onIdle()
            } else {
                checkPermissionAndListen()
            }
        }

        binding.btnInputMic.setOnClickListener { setInputMode(InputMode.MIC) }
        binding.btnInputKeyboard.setOnClickListener {
            stopRipple()
            setInputMode(InputMode.TEXT)
        }

        binding.btnInputClipboard.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
            if (text.isBlank()) {
                Toast.makeText(requireContext(), getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
            } else {
                navigateToTranslation(text)
            }
        }

        binding.btnSendText.setOnClickListener {
            val text = binding.etManualInput.text.toString().trim()
            if (text.isNotBlank()) navigateToTranslation(text)
        }

        viewModel.state.observe(viewLifecycleOwner) { renderState(it) }
        viewModel.cardCount.observe(viewLifecycleOwner) { count ->
            binding.tvCardCount.text = getString(R.string.card_count, count)
        }

        setInputMode(InputMode.MIC)
    }

    private fun setInputMode(mode: InputMode) {
        inputMode = mode
        val primary = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
        val grey = ContextCompat.getColor(requireContext(), R.color.grey_medium)

        when (mode) {
            InputMode.MIC -> {
                binding.btnMic.visibility = View.VISIBLE
                binding.layoutTextInput.visibility = View.GONE
                binding.btnInputMic.imageTintList = ColorStateList.valueOf(primary)
                binding.btnInputKeyboard.imageTintList = ColorStateList.valueOf(grey)
            }
            InputMode.TEXT -> {
                binding.btnMic.visibility = View.GONE
                binding.layoutTextInput.visibility = View.VISIBLE
                binding.tvStatus.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
                binding.btnInputMic.imageTintList = ColorStateList.valueOf(grey)
                binding.btnInputKeyboard.imageTintList = ColorStateList.valueOf(primary)
                binding.etManualInput.requestFocus()
            }
        }
    }

    private fun updateHint() {
        binding.tvHint.text = getString(
            if (isHebrewInput) R.string.hint_voice else R.string.hint_voice_russian
        )
        if (isHebrewInput) {
            binding.etManualInput.textDirection = View.TEXT_DIRECTION_RTL
            binding.etManualInput.hint = "הכנס טקסט..."
        } else {
            binding.etManualInput.textDirection = View.TEXT_DIRECTION_LOCALE
            binding.etManualInput.hint = "Введите текст..."
        }
    }

    private fun navigateToTranslation(text: String) {
        val bundle = Bundle().apply {
            putString("inputText", text)
            putBoolean("isHebrewInput", isHebrewInput)
        }
        findNavController().navigate(R.id.action_voice_to_translation, bundle)
    }

    private fun renderState(state: VoiceState) { when (state) {
        is VoiceState.Idle -> {
            binding.tvStatus.visibility = View.GONE
            binding.progressBar.visibility = View.GONE
            binding.btnMic.isEnabled = true
            stopRipple()
        }
        is VoiceState.Listening -> {
            binding.tvStatus.text = getString(R.string.btn_listening)
            binding.tvStatus.visibility = View.VISIBLE
            binding.progressBar.visibility = View.VISIBLE
            binding.btnMic.isEnabled = true
            if (inputMode == InputMode.MIC) startRipple()
        }
        is VoiceState.Translating -> {
            binding.tvStatus.text = getString(R.string.btn_translating)
            binding.tvStatus.visibility = View.VISIBLE
            binding.progressBar.visibility = View.VISIBLE
            binding.btnMic.isEnabled = false
            if (inputMode == InputMode.MIC) startRipple()
        }
        is VoiceState.Error -> {
            binding.tvStatus.text = state.message
            binding.tvStatus.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            binding.btnMic.isEnabled = true
            stopRipple()
            viewModel.onErrorHandled()
        }
    } }

    private fun startRipple() {
        if (rippleAnimators.isNotEmpty()) return
        val rippleViews = listOf(binding.ripple1, binding.ripple2, binding.ripple3)
        rippleViews.forEach { v ->
            v.visibility = View.VISIBLE
            v.alpha = 0f
            v.scaleX = 1f
            v.scaleY = 1f
        }
        rippleViews.forEachIndexed { index, view ->
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1500
                startDelay = (index * 500).toLong()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                addUpdateListener { anim ->
                    val f = anim.animatedFraction
                    view.scaleX = f
                    view.scaleY = f
                    view.alpha = 0.35f * (1f - f)
                }
            }
            rippleAnimators.add(animator)
            animator.start()
        }
    }

    private fun stopRipple() {
        rippleAnimators.forEach { it.cancel() }
        rippleAnimators.clear()
        if (_binding != null) {
            listOf(binding.ripple1, binding.ripple2, binding.ripple3).forEach { v ->
                v.animate().cancel()
                v.visibility = View.INVISIBLE
                v.alpha = 0f
                v.scaleX = 1f
                v.scaleY = 1f
            }
        }
    }

    // ── Permission & voice recognition ───────────────────────────────────────

    private fun checkPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) startListening()
        else requestPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun acquireWakeLock() {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hebrew:SpeechRecognition")
        wakeLock?.acquire(60_000L)
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Hebrew:WakeScreen"
        )
        wl.acquire(3000L)
        wl.release()
    }

    private fun startListening() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        acquireWakeLock()

        val locale = if (isHebrewInput) "iw-IL" else "ru-RU"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = viewModel.onListening()
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onResults(results: Bundle?) {
                releaseWakeLock()
                wakeScreen()
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    viewModel.onTranslating()
                    navigateToTranslation(text)
                } else {
                    viewModel.onIdle()
                    Toast.makeText(requireContext(), getString(R.string.error_no_speech), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: Int) {
                releaseWakeLock()
                viewModel.onIdle()
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> getString(R.string.error_no_speech)
                    else -> getString(R.string.error_voice)
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onIdle()
    }

    override fun onDestroyView() {
        releaseWakeLock()
        stopRipple()
        super.onDestroyView()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _binding = null
    }
}
