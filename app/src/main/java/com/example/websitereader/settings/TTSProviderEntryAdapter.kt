package com.example.websitereader.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.websitereader.R

class TTSProviderEntryAdapter(
    private val entries: MutableList<TTSProviderEntry>,
    private val onClick: (position: Int, entry: TTSProviderEntry) -> Unit
) : RecyclerView.Adapter<TTSProviderEntryAdapter.EntryViewHolder>() {

    class EntryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.entryName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tts_provider_entry, parent, false)
        return EntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        holder.name.text = entries[position].name
        holder.itemView.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onClick(adapterPosition, entries[adapterPosition])
            }
        }
        holder.itemView.findViewById<View>(R.id.btnDeleteEntry).setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                entries.removeAt(adapterPosition)
                notifyItemRemoved(adapterPosition)
                TTSProviderEntryStorage.save(holder.itemView.context, entries)
            }
        }
    }

    override fun getItemCount() = entries.size
}