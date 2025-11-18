package com.example.familyone.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.familyone.R
import com.example.familyone.data.MemberPhoto
import com.example.familyone.databinding.ItemPhotoGalleryBinding

class PhotoGalleryAdapter(
    private val onPhotoClick: (MemberPhoto) -> Unit,
    private val onPhotoLongClick: (MemberPhoto) -> Unit
) : ListAdapter<MemberPhoto, PhotoGalleryAdapter.PhotoViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoGalleryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class PhotoViewHolder(
        private val binding: ItemPhotoGalleryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(photo: MemberPhoto) {
            val photoPath = photo.photoUri.replace("file://", "")
            
            Glide.with(binding.root.context)
                .load(java.io.File(photoPath))
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .centerCrop()
                .into(binding.ivPhoto)
            
            binding.root.setOnClickListener {
                onPhotoClick(photo)
            }
            
            binding.root.setOnLongClickListener {
                onPhotoLongClick(photo)
                true
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<MemberPhoto>() {
        override fun areItemsTheSame(oldItem: MemberPhoto, newItem: MemberPhoto): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: MemberPhoto, newItem: MemberPhoto): Boolean {
            return oldItem == newItem
        }
    }
}
