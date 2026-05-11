package com.example.hebrew.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.hebrew.R
import com.example.hebrew.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("hebrew_prefs", Context.MODE_PRIVATE)

        binding.etApiKey.setText(prefs.getString("openai_api_key", ""))

        val savedReps = prefs.getInt("repetitions_count", 4)
        binding.sliderRepetitions.value = savedReps.toFloat()
        binding.tvRepetitionsLabel.text = getString(R.string.label_repetitions, savedReps)

        binding.sliderRepetitions.addOnChangeListener { _, value, _ ->
            binding.tvRepetitionsLabel.text = getString(R.string.label_repetitions, value.toInt())
        }

        val savedExamples = prefs.getInt("examples_count", 5)
        binding.sliderExamplesCount.value = savedExamples.toFloat()
        binding.tvExamplesCountLabel.text = getString(R.string.label_examples_count, savedExamples)

        binding.sliderExamplesCount.addOnChangeListener { _, value, _ ->
            binding.tvExamplesCountLabel.text = getString(R.string.label_examples_count, value.toInt())
        }

        val savedHistory = prefs.getInt("history_count", 30)
        binding.sliderHistoryCount.value = savedHistory.toFloat()
        binding.tvHistoryCountLabel.text = getString(R.string.label_history_count, savedHistory)

        binding.sliderHistoryCount.addOnChangeListener { _, value, _ ->
            binding.tvHistoryCountLabel.text = getString(R.string.label_history_count, value.toInt())
        }

        binding.btnSaveSettings.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            val reps = binding.sliderRepetitions.value.toInt()
            val examples = binding.sliderExamplesCount.value.toInt()
            val history = binding.sliderHistoryCount.value.toInt()
            prefs.edit()
                .putString("openai_api_key", key)
                .putInt("repetitions_count", reps)
                .putInt("examples_count", examples)
                .putInt("history_count", history)
                .apply()
            Toast.makeText(requireContext(), getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
