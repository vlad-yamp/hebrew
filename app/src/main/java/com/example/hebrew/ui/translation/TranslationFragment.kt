package com.example.hebrew.ui.translation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.hebrew.R
import com.example.hebrew.databinding.FragmentTranslationBinding
import com.example.hebrew.databinding.ItemExampleBinding
import com.example.hebrew.ui.learning.ExampleItem
import java.util.Locale

class TranslationFragment : Fragment() {

    private var _binding: FragmentTranslationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TranslationViewModel by viewModels()
    private var tts: TextToSpeech? = null

    private var isHebrewInput = true
    private var inputText = ""

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

        applyInputLanguage()
        initTts()
        setupObservers()
        setupClickListeners()

        if (viewModel.translationState.value is TranslationState.Idle) {
            viewModel.translate(inputText, isHebrewInput)
        }
    }

    private fun applyInputLanguage() {
        binding.tvHebrewPhrase.text = inputText

        if (isHebrewInput) {
            binding.tvInputLabel.text = "Иврит"
            binding.tvHebrewPhrase.textDirection = View.TEXT_DIRECTION_RTL
            binding.tvHebrewPhrase.gravity = Gravity.END
            binding.tvTranslationLabel.text = "Перевод"
            binding.tvSingleTranslation.textDirection = View.TEXT_DIRECTION_LOCALE
            binding.tvSingleTranslation.gravity = Gravity.START
            binding.btnSpeakHebrew.visibility = View.VISIBLE
            binding.hebrewActionsBar.visibility = View.GONE
        } else {
            binding.tvInputLabel.text = "Русский"
            binding.tvHebrewPhrase.textDirection = View.TEXT_DIRECTION_LOCALE
            binding.tvHebrewPhrase.gravity = Gravity.START
            binding.tvTranslationLabel.text = "Иврит"
            binding.tvSingleTranslation.textDirection = View.TEXT_DIRECTION_RTL
            binding.tvSingleTranslation.gravity = Gravity.END
            binding.btnSpeakHebrew.visibility = View.GONE
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
                    binding.progressTranslation.visibility = View.VISIBLE
                    binding.cardSingleTranslation.visibility = View.GONE
                    binding.cardVariants.visibility = View.GONE
                    binding.hebrewActionsBar.visibility = View.GONE
                    binding.btnExamples.visibility = View.GONE
                    binding.btnSaveCard.visibility = View.GONE
                    binding.btnNewInput.visibility = View.GONE
                }

                is TranslationState.SingleTranslation -> {
                    binding.progressTranslation.visibility = View.GONE
                    binding.cardSingleTranslation.visibility = View.VISIBLE
                    binding.tvSingleTranslation.text = state.text
                    binding.cardVariants.visibility = View.GONE
                    binding.btnExamples.visibility = View.VISIBLE
                    binding.btnSaveCard.visibility = View.VISIBLE
                    binding.btnNewInput.visibility = View.VISIBLE
                    if (!isHebrewInput) binding.hebrewActionsBar.visibility = View.VISIBLE
                }

                is TranslationState.MultipleVariants -> {
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

        viewModel.cardSaved.observe(viewLifecycleOwner) { saved ->
            if (saved) {
                viewModel.onCardSavedHandled()
                Toast.makeText(requireContext(), getString(R.string.card_saved), Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }
    }

    private fun showExamples(examples: List<ExampleItem>) {
        binding.examplesContainer.removeAllViews()
        examples.forEach { item ->
            val itemBinding = ItemExampleBinding.inflate(
                layoutInflater, binding.examplesContainer, false
            )
            itemBinding.tvExampleHebrew.text = item.hebrew
            itemBinding.tvExampleRussian.text = item.russian
            itemBinding.btnSpeakExample.setOnClickListener {
                tts?.speak(item.hebrew, TextToSpeech.QUEUE_FLUSH, null, null)
            }
            binding.examplesContainer.addView(itemBinding.root)
        }
    }

    private fun setupClickListeners() {
        binding.btnSpeakHebrew.setOnClickListener {
            val hebrew = viewModel.currentHebrew
            if (hebrew.isNotBlank()) tts?.speak(hebrew, TextToSpeech.QUEUE_FLUSH, null, null)
        }
        binding.btnSpeakResult.setOnClickListener {
            val hebrew = viewModel.currentHebrew
            if (hebrew.isNotBlank()) tts?.speak(hebrew, TextToSpeech.QUEUE_FLUSH, null, null)
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
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroyView()
        _binding = null
    }
}
