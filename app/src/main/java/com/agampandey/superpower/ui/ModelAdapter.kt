package com.agampandey.superpower.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.agampandey.superpower.R
import com.agampandey.superpower.data.LanguageModelItem

class ModelAdapter(
    private val onActionClick: (LanguageModelItem) -> Unit
) : RecyclerView.Adapter<ModelAdapter.ViewHolder>() {

    private var items: List<LanguageModelItem> = emptyList()

    fun updateData(newItems: List<LanguageModelItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_language_name)
        val tvStatus: TextView = view.findViewById(R.id.tv_status)
        val btnAction: ImageView = view.findViewById(R.id.btn_action)
        val pbDownload: ProgressBar = view.findViewById(R.id.pb_download)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.languageName
        
        if (item.isDownloading) {
            holder.tvStatus.text = "Downloading..."
            holder.btnAction.visibility = View.GONE
            holder.pbDownload.visibility = View.VISIBLE
        } else if (item.isDownloaded) {
            holder.tvStatus.text = "Downloaded"
            holder.btnAction.visibility = View.VISIBLE
            holder.pbDownload.visibility = View.GONE
            holder.btnAction.setImageResource(R.drawable.ic_delete) // Reuse delete icon
            holder.btnAction.setColorFilter(holder.itemView.context.getColor(R.color.text_secondary))
        } else {
            holder.tvStatus.text = "Tap to download"
            holder.btnAction.visibility = View.VISIBLE
            holder.pbDownload.visibility = View.GONE
            holder.btnAction.setImageResource(R.drawable.ic_download)
            holder.btnAction.setColorFilter(holder.itemView.context.getColor(R.color.purple_500))
        }

        holder.btnAction.setOnClickListener {
            onActionClick(item)
        }
    }

    override fun getItemCount() = items.size
}
