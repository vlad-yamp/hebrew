package com.example.hebrew.ui.cards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hebrew.R
import com.example.hebrew.databinding.FragmentCardListBinding

class CardListFragment : Fragment() {

    private var _binding: FragmentCardListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CardListViewModel by viewModels()
    private val adapter = CardAdapter(
        onCardClick = { card ->
            CardPreviewBottomSheet.newInstance(card)
                .show(childFragmentManager, "card_preview")
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

        binding.recyclerCards.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCards.adapter = adapter

        viewModel.allCards.observe(viewLifecycleOwner) { cards ->
            adapter.threshold = viewModel.getThreshold()
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
        super.onDestroyView()
        _binding = null
    }
}
