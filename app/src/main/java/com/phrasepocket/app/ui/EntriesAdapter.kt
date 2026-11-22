package com.phrasepocket.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.phrasepocket.app.data.Entry
import com.phrasepocket.app.databinding.ItemEntryBinding

class EntriesAdapter(
    private val onClick: (Entry) -> Unit,
    private val onLongClick: (Entry) -> Unit,
    private val onSpeak: (Entry) -> Unit = {}
) : ListAdapter<Entry, EntriesAdapter.VH>(DIFF) {

    class VH(val binding: ItemEntryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        holder.binding.entryText.text = entry.text
        holder.binding.entryMeaning.text = entry.meaning
        holder.binding.entryMeaning.visibility =
            if (entry.meaning.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        holder.binding.root.setOnClickListener { onClick(entry) }
        holder.binding.root.setOnLongClickListener {
            onLongClick(entry)
            true
        }
        holder.binding.entrySpeak.setOnClickListener { onSpeak(entry) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Entry>() {
            override fun areItemsTheSame(oldItem: Entry, newItem: Entry) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Entry, newItem: Entry) = oldItem == newItem
        }
    }
}
