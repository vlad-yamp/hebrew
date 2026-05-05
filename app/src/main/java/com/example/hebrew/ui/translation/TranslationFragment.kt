package com.example.hebrew.ui.translation

import android.os.Bundle
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

class TranslationFragment : Fragment() {

    private var _binding: FragmentTranslationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TranslationViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTranslationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val hebrewText = arguments?.getString("hebrewText") ?: ""
        binding.tvHebrewPhrase.text = hebrewText

        setupObservers()
        setupClickListeners()

        if (viewModel.translationState.value is TranslationState.Idle) {
            viewModel.translate(hebrewText)
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
                }

                is TranslationState.MultipleVariants -> {
                    binding.progressTranslation.visibility = View.GONE
                    binding.cardSingleTranslation.visibility = View.GONE
                    binding.cardVariants.visibility = View.VISIBLE
                    binding.btnExamples.visibility = View.VISIBLE
                    binding.btnSaveCard.visibility = View.VISIBLE
                    binding.btnNewInput.visibility = View.VISIBLE
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
                    binding.cardExamples.visibility = View.GONE
                }
                is ExamplesState.Done -> {
                    binding.progressExamples.visibility = View.GONE
                    binding.cardExamples.visibility = View.VISIBLE
                    binding.tvExamples.text = state.text
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

    private fun setupClickListeners() {
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
        super.onDestroyView()
        _binding = null
    }
}
