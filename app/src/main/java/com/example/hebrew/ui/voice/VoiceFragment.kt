package com.example.hebrew.ui.voice

import android.Manifest
import android.animation.ValueAnimator
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var audioManager: AudioManager? = null
    private var isHebrewInput = true
    private var inputMode = InputMode.MIC
    private var gender = "male"
    private val rippleAnimators = mutableListOf<ValueAnimator>()
    private var rippleMaxScale = 0.3f
    private val silenceHandler = Handler(Looper.getMainLooper())
    private val stopOnSilence = Runnable { speechRecognizer?.stopListening() }

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

        gender = prefs.getString("gender_preference", "male") ?: "male"
        binding.genderToggle.check(if (gender == "female") R.id.btnGenderFemale else R.id.btnGenderMale)

        updateHint()
        updateGenderVisibility()

        binding.langToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isHebrewInput = (checkedId == R.id.btnLangHebrew)
                prefs.edit().putBoolean("default_lang_hebrew", isHebrewInput).apply()
                updateHint()
                updateGenderVisibility()
            }
        }

        binding.genderToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                gender = if (checkedId == R.id.btnGenderFemale) "female" else "male"
                prefs.edit().putString("gender_preference", gender).apply()
            }
        }

        binding.btnMic.setOnClickListener {
            if (viewModel.state.value is VoiceState.Listening) {
                silenceHandler.removeCallbacks(stopOnSilence)
                speechRecognizer?.stopListening()
                viewModel.onTranslating()
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

    private fun updateGenderVisibility() {
        binding.genderToggle.visibility = if (isHebrewInput) View.GONE else View.VISIBLE
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
            if (!isHebrewInput) putString("gender", gender)
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
            rippleMaxScale = 0.3f
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
        rippleMaxScale = 0.3f
        val rippleViews = listOf(binding.ripple1, binding.ripple2, binding.ripple3)
        rippleViews.forEach { v ->
            v.visibility = View.VISIBLE
            v.alpha = 0f
            v.scaleX = 0f
            v.scaleY = 0f
        }
        rippleViews.forEachIndexed { index, view ->
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1500
                startDelay = (index * 500).toLong()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                addUpdateListener { anim ->
                    val f = anim.animatedFraction
                    val s = f * rippleMaxScale
                    view.scaleX = s
                    view.scaleY = s
                    view.alpha = 0.45f * rippleMaxScale * (1f - f)
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

    private val beepStreams = intArrayOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_SYSTEM
    )
    private val savedVolumes = mutableMapOf<Int, Int>()
    private val unmuteRunnable = Runnable { unmuteBeep() }

    private fun muteBeep() {
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val am = audioManager ?: return
        silenceHandler.removeCallbacks(unmuteRunnable)
        beepStreams.forEach { stream ->
            try {
                savedVolumes[stream] = am.getStreamVolume(stream)
                am.setStreamVolume(stream, 0, 0)
            } catch (_: Exception) {}
        }
    }

    private fun unmuteBeep() {
        val am = audioManager ?: return
        beepStreams.forEach { stream ->
            try {
                am.setStreamVolume(stream, savedVolumes[stream] ?: am.getStreamMaxVolume(stream), 0)
            } catch (_: Exception) {}
        }
        savedVolumes.clear()
    }

    private fun unmuteBeepDelayed(delayMs: Long = 400) {
        silenceHandler.removeCallbacks(unmuteRunnable)
        silenceHandler.postDelayed(unmuteRunnable, delayMs)
    }

    private fun shouldMuteBeep() =
        prefs.getBoolean("mute_recognition_sounds", false)

    private fun startListening() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        acquireWakeLock()
        if (shouldMuteBeep()) muteBeep()

        val locale = if (isHebrewInput) "iw-IL" else "ru-RU"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (shouldMuteBeep()) unmuteBeepDelayed(300)
                viewModel.onListening()
            }
            override fun onBeginningOfSpeech() {
                silenceHandler.removeCallbacks(stopOnSilence)
            }
            override fun onRmsChanged(rmsdB: Float) {
                val rms = rmsdB.coerceIn(0f, 10f) / 10f
                val target = 0.3f + 0.7f * rms
                rippleMaxScale = rippleMaxScale * 0.5f + target * 0.5f
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                silenceHandler.postDelayed(stopOnSilence, 1000)
            }

            override fun onResults(results: Bundle?) {
                silenceHandler.removeCallbacks(stopOnSilence)
                releaseWakeLock()
                wakeScreen()
                if (shouldMuteBeep()) unmuteBeepDelayed(300)
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
                silenceHandler.removeCallbacks(stopOnSilence)
                releaseWakeLock()
                if (shouldMuteBeep()) unmuteBeepDelayed(600)
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
        silenceHandler.removeCallbacksAndMessages(null)
        unmuteBeep()
        releaseWakeLock()
        stopRipple()
        super.onDestroyView()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _binding = null
    }
}
