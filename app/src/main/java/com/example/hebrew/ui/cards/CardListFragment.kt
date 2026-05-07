package com.example.hebrew.ui.cards

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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hebrew.R
import com.example.hebrew.api.TransliterationHelper
import com.example.hebrew.databinding.FragmentCardListBinding
import kotlinx.coroutines.launch
import java.util.Locale

class CardListFragment : Fragment() {

    private var _binding: FragmentCardListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CardListViewModel by viewModels()
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

    private val adapter = CardAdapter(
        onCardClick = { card ->
            CardPreviewBottomSheet.newInstance(card)
                .show(childFragmentManager, "card_preview")
        },
        onSpeakClick = { card ->
            speakToggle(card.id.toString(), card.hebrew)
        },
        onTransliterateClick = { card, onResult ->
            val apiKey = requireContext()
                .getSharedPreferences("hebrew_prefs", Context.MODE_PRIVATE)
                .getString("openai_api_key", "") ?: ""
            if (apiKey.isBlank()) return@CardAdapter
            lifecycleScope.launch {
                runCatching { TransliterationHelper.transliterate(apiKey, card.hebrew) }
                    .onSuccess { onResult(it) }
            }
        },
        onDeleteClick = { card ->
            viewModel.deleteCard(card)
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCardListBinding.inflate(inflater, container, false)
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

        binding.recyclerCards.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCards.adapter = adapter

        viewModel.allCards.observe(viewLifecycleOwner) { cards ->
            adapter.submitList(cards)
            val count = cards.size
            binding.tvCardCount.text = getString(R.string.card_count, count)
            binding.tvEmpty.visibility = if (count == 0) View.VISIBLE else View.GONE
            binding.recyclerCards.visibility = if (count == 0) View.GONE else View.VISIBLE
            binding.btnLearn.isEnabled = count > 0
        }

        binding.btnLearn.setOnClickListener {
            findNavController().navigate(R.id.action_cardList_to_learning)
        }

        binding.btnResetAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.confirm_restart_all))
                .setPositiveButton(getString(R.string.confirm_clear_yes)) { _, _ ->
                    viewModel.resetAll()
                }
                .setNegativeButton(getString(R.string.confirm_clear_no), null)
                .show()
        }

        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.confirm_clear))
                .setPositiveButton(getString(R.string.confirm_clear_yes)) { _, _ ->
                    viewModel.clearAll()
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
