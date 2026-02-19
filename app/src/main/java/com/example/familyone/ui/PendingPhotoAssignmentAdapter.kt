package com.example.familyone.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.familyone.R
import com.example.familyone.databinding.ItemPendingPhotoAssignmentBinding

class PendingPhotoAssignmentAdapter(
    private val onPendingPhotoClick: (Int) -> Unit
) : ListAdapter<PendingPhotoAssignmentAdapter.PendingPhotoUiItem, PendingPhotoAssignmentAdapter.PendingPhotoViewHolder>(DiffCallback()) {

    data class PendingPhotoUiItem(
        val uri: android.net.Uri,
        val reasonText: String,
        val errorText: String?,
        val isSelected: Boolean
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingPhotoViewHolder {
        val binding = ItemPendingPhotoAssignmentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PendingPhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PendingPhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PendingPhotoViewHolder(
        private val binding: ItemPendingPhotoAssignmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PendingPhotoUiItem) {
            val context = binding.root.context

            Glide.with(context)
                .load(item.uri)
                .centerCrop()
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .into(binding.ivPendingPhoto)

            binding.tvPendingReason.text = item.reasonText
            if (item.errorText.isNullOrBlank()) {
                binding.tvPendingError.text = ""
                binding.tvPendingError.visibility = android.view.View.GONE
            } else {
                binding.tvPendingError.text = item.errorText
                binding.tvPendingError.visibility = android.view.View.VISIBLE
            }

            val strokeColor = if (item.isSelected) {
                ContextCompat.getColor(context, R.color.purple_button)
            } else {
                ContextCompat.getColor(context, R.color.text_tertiary_light)
            }

            binding.cardPendingItem.strokeWidth = if (item.isSelected) 3 else 1
            binding.cardPendingItem.strokeColor = strokeColor

            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onPendingPhotoClick(pos)
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<PendingPhotoUiItem>() {
        override fun areItemsTheSame(oldItem: PendingPhotoUiItem, newItem: PendingPhotoUiItem): Boolean {
            return oldItem.uri.toString() == newItem.uri.toString()
        }

        override fun areContentsTheSame(oldItem: PendingPhotoUiItem, newItem: PendingPhotoUiItem): Boolean {
            return oldItem == newItem
        }
    }
}
