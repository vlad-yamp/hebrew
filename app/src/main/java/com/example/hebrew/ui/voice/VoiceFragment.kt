package com.example.hebrew.ui.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

    private var _binding: FragmentVoiceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VoiceViewModel by viewModels()
    private var speechRecognizer: SpeechRecognizer? = null
    private var isHebrewInput = true
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

        binding.btnMic.setOnClickListener { checkPermissionAndListen() }
        viewModel.state.observe(viewLifecycleOwner) { renderState(it) }
        viewModel.cardCount.observe(viewLifecycleOwner) { count ->
            binding.tvCardCount.text = getString(R.string.card_count, count)
        }
    }

    private fun updateHint() {
        binding.tvHint.text = getString(
            if (isHebrewInput) R.string.hint_voice else R.string.hint_voice_russian
        )
    }

    private fun renderState(state: VoiceState) = when (state) {
        is VoiceState.Idle -> {
            binding.tvStatus.text = getString(R.string.btn_start_voice)
            binding.progressBar.visibility = View.GONE
            binding.btnMic.isEnabled = true
        }
        is VoiceState.Listening -> {
            binding.tvStatus.text = getString(R.string.btn_listening)
            binding.progressBar.visibility = View.VISIBLE
            binding.btnMic.isEnabled = false
        }
        is VoiceState.Translating -> {
            binding.tvStatus.text = getString(R.string.btn_translating)
            binding.progressBar.visibility = View.VISIBLE
            binding.btnMic.isEnabled = false
        }
        is VoiceState.Error -> {
            binding.tvStatus.text = state.message
            binding.progressBar.visibility = View.GONE
            binding.btnMic.isEnabled = true
            viewModel.onErrorHandled()
        }
    }

    private fun checkPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) startListening()
        else requestPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())

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
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    viewModel.onTranslating()
                    val bundle = Bundle().apply {
                        putString("inputText", text)
                        putBoolean("isHebrewInput", isHebrewInput)
                    }
                    findNavController().navigate(R.id.action_voice_to_translation, bundle)
                } else {
                    viewModel.onIdle()
                    Toast.makeText(requireContext(), getString(R.string.error_no_speech), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: Int) {
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
        super.onDestroyView()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _binding = null
    }
}
