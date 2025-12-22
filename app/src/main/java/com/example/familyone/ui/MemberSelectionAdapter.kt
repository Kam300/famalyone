package com.example.familyone.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.familyone.R
import com.example.familyone.data.FamilyMember
import com.example.familyone.databinding.ItemMemberSelectionBinding
import com.example.familyone.utils.toLocalizedString
import java.io.File

/**
 * Адаптер для выбора члена семьи при привязке фото
 */
class MemberSelectionAdapter(
    private val onMemberClick: (FamilyMember) -> Unit
) : ListAdapter<FamilyMember, MemberSelectionAdapter.MemberViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemMemberSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MemberViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class MemberViewHolder(
        private val binding: ItemMemberSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(member: FamilyMember) {
            binding.tvName.text = "${member.firstName} ${member.lastName}"
            binding.tvRole.text = member.role.toLocalizedString(binding.root.context)
            
            // Загружаем фото
            if (!member.photoUri.isNullOrEmpty()) {
                val photoPath = member.photoUri!!.replace("file://", "")
                Glide.with(binding.root.context)
                    .load(File(photoPath))
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .circleCrop()
                    .into(binding.ivPhoto)
                
                binding.ivPhotoStatus.setImageResource(R.drawable.ic_check_circle)
                binding.ivPhotoStatus.setColorFilter(binding.root.context.getColor(R.color.green_accent))
            } else {
                binding.ivPhoto.setImageResource(R.mipmap.ic_launcher)
                binding.ivPhotoStatus.setImageResource(R.drawable.ic_warning)
                binding.ivPhotoStatus.setColorFilter(binding.root.context.getColor(R.color.red_button))
            }
            
            binding.root.setOnClickListener {
                onMemberClick(member)
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<FamilyMember>() {
        override fun areItemsTheSame(oldItem: FamilyMember, newItem: FamilyMember): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: FamilyMember, newItem: FamilyMember): Boolean {
            return oldItem == newItem
        }
    }
}
