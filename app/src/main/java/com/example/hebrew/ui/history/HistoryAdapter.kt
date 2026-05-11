package com.example.hebrew.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hebrew.data.HistoryEntry
import com.example.hebrew.databinding.ItemCardBinding

class HistoryAdapter(
    private val onEntryClick: (HistoryEntry) -> Unit,
    private val onSpeakClick: (HistoryEntry) -> Unit,
    private val onTransliterateClick: (HistoryEntry, (String) -> Unit) -> Unit,
    private val onDeleteClick: (HistoryEntry) -> Unit
) : ListAdapter<HistoryEntry, HistoryAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<HistoryEntry>() {
        override fun areItemsTheSame(a: HistoryEntry, b: HistoryEntry) = a.id == b.id
        override fun areContentsTheSame(a: HistoryEntry, b: HistoryEntry) = a == b
    }

    inner class ViewHolder(private val binding: ItemCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: HistoryEntry) {
            binding.tvHebrewCard.text = entry.hebrew
            binding.tvRussianCard.text = entry.russian
            binding.tvTransliteration.visibility = View.GONE
            binding.tvTransliteration.text = ""
            binding.root.setOnClickListener { onEntryClick(entry) }
            binding.btnSpeak.setOnClickListener { onSpeakClick(entry) }
            binding.btnTransliterate.setOnClickListener {
                if (binding.tvTransliteration.visibility == View.VISIBLE) {
                    binding.tvTransliteration.visibility = View.GONE
                    return@setOnClickListener
                }
                if (binding.tvTransliteration.text.isNotEmpty()) {
                    binding.tvTransliteration.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                onTransliterateClick(entry) { result ->
                    binding.tvTransliteration.text = result
                    binding.tvTransliteration.visibility = View.VISIBLE
                }
            }
            binding.btnDelete.setOnClickListener { onDeleteClick(entry) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
