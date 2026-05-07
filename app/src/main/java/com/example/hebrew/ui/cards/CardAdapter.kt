package com.example.hebrew.ui.cards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hebrew.data.Card
import com.example.hebrew.databinding.ItemCardBinding

class CardAdapter(
    private val onCardClick: (Card) -> Unit,
    private val onSpeakClick: (Card) -> Unit,
    private val onTransliterateClick: (Card, (String) -> Unit) -> Unit,
    private val onDeleteClick: (Card) -> Unit
) : ListAdapter<Card, CardAdapter.CardViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<Card>() {
        override fun areItemsTheSame(a: Card, b: Card) = a.id == b.id
        override fun areContentsTheSame(a: Card, b: Card) = a == b
    }

    inner class CardViewHolder(private val binding: ItemCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: Card) {
            binding.tvHebrewCard.text = card.hebrew
            binding.tvRussianCard.text = card.russian
            binding.tvTransliteration.visibility = View.GONE
            binding.tvTransliteration.text = ""
            binding.root.setOnClickListener { onCardClick(card) }
            binding.btnSpeak.setOnClickListener { onSpeakClick(card) }
            binding.btnTransliterate.setOnClickListener {
                if (binding.tvTransliteration.visibility == View.VISIBLE) {
                    binding.tvTransliteration.visibility = View.GONE
                    return@setOnClickListener
                }
                if (binding.tvTransliteration.text.isNotEmpty()) {
                    binding.tvTransliteration.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                onTransliterateClick(card) { result ->
                    binding.tvTransliteration.text = result
                    binding.tvTransliteration.visibility = View.VISIBLE
                }
            }
            binding.btnDelete.setOnClickListener { onDeleteClick(card) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
