package com.example.hebrew.ui.history

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hebrew.R
import com.example.hebrew.api.TransliterationHelper
import com.example.hebrew.data.Card
import com.example.hebrew.databinding.FragmentHistoryBinding
import com.example.hebrew.ui.cards.CardPreviewBottomSheet
import kotlinx.coroutines.launch
import java.util.Locale

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
    private var tts: TextToSpeech? = null
    private val slowKeys = mutableSetOf<String>()

    private fun speakToggle(key: String, text: String) {
        val isSlow = slowKeys.contains(key)
        tts?.setSpeechRate(if (isSlow) 0.5f else 1.0f)
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        if (result == TextToSpeech.SUCCESS) {
            if (isSlow) slowKeys.remove(key) else slowKeys.add(key)
        }
    }

    private val adapter = HistoryAdapter(
        onEntryClick = { entry ->
            CardPreviewBottomSheet.newInstance(Card(hebrew = entry.hebrew, russian = entry.russian))
                .show(childFragmentManager, "preview")
        },
        onSpeakClick = { entry ->
            speakToggle(entry.id.toString(), entry.hebrew)
        },
        onTransliterateClick = { entry, onResult ->
            val apiKey = requireContext()
                .getSharedPreferences("hebrew_prefs", Context.MODE_PRIVATE)
                .getString("openai_api_key", "") ?: ""
            if (apiKey.isBlank()) return@HistoryAdapter
            lifecycleScope.launch {
                runCatching { TransliterationHelper.transliterate(apiKey, entry.hebrew) }
                    .onSuccess { onResult(it) }
            }
        },
        onDeleteClick = { entry -> viewModel.delete(entry) }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                var result = tts?.setLanguage(Locale("iw"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("he"))
                }
            }
        }

        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = adapter

        viewModel.allEntries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
            binding.tvHistoryCount.text = getString(R.string.history_count, entries.size)
            binding.tvEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerHistory.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        }

        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.confirm_clear_history))
                .setPositiveButton(getString(R.string.confirm_clear_yes)) { _, _ ->
                    viewModel.deleteAll()
                }
                .setNegativeButton(getString(R.string.confirm_clear_no), null)
                .show()
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
