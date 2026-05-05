package com.example.hebrew.ui.cards

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hebrew.data.Card
import com.example.hebrew.databinding.ItemCardBinding

class CardAdapter(
    private val onCardClick: (Card) -> Unit,
    private val onDeleteClick: (Card) -> Unit
) : ListAdapter<Card, CardAdapter.CardViewHolder>(DiffCallback) {

    var threshold: Int = 4

    object DiffCallback : DiffUtil.ItemCallback<Card>() {
        override fun areItemsTheSame(a: Card, b: Card) = a.id == b.id
        override fun areContentsTheSame(a: Card, b: Card) = a == b
    }

    inner class CardViewHolder(private val binding: ItemCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: Card) {
            binding.tvHebrewCard.text = card.hebrew
            binding.tvRussianCard.text = card.russian
            binding.tvKnownCount.text = "${card.knownCount}/$threshold"
            binding.root.setOnClickListener { onCardClick(card) }
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
