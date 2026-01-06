package com.example.familyone.ui

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
 * Показывает три статуса:
 * - Нет фото (красный)
 * - Есть фото, не зарегистрирован на сервере (оранжевый)
 * - Зарегистрирован на сервере (зелёный)
 */
class MemberSelectionAdapter(
    private val onMemberClick: (FamilyMember) -> Unit
) : ListAdapter<FamilyMember, MemberSelectionAdapter.MemberViewHolder>(DiffCallback()) {
    
    // Множество ID членов, зарегистрированных на сервере
    private val registeredMemberIds = mutableSetOf<String>()
    
    /**
     * Обновляет список зарегистрированных на сервере членов
     */
    fun updateRegisteredMembers(memberIds: Set<String>) {
        registeredMemberIds.clear()
        registeredMemberIds.addAll(memberIds)
        notifyDataSetChanged()
    }
    
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
            val context = binding.root.context
            
            binding.tvName.text = "${member.firstName} ${member.lastName}"
            binding.tvRole.text = member.role.toLocalizedString(context)
            
            val hasPhoto = !member.photoUri.isNullOrEmpty()
            val isRegistered = registeredMemberIds.contains(member.id.toString())
            
            // Загружаем фото
            if (hasPhoto) {
                val photoPath = member.photoUri!!.replace("file://", "")
                Glide.with(context)
                    .load(File(photoPath))
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .circleCrop()
                    .into(binding.ivPhoto)
            } else {
                binding.ivPhoto.setImageResource(R.mipmap.ic_launcher)
            }
            
            // Устанавливаем статус
            when {
                !hasPhoto -> {
                    // Нет фото - красный
                    binding.tvStatus.text = "Нет фото"
                    setStatusColors(
                        ContextCompat.getColor(context, R.color.red_button),
                        0xFFFFEBEE.toInt() // светло-красный фон
                    )
                    binding.ivPhotoStatus.setImageResource(R.drawable.ic_warning)
                    binding.ivPhotoStatus.setColorFilter(ContextCompat.getColor(context, R.color.red_button))
                }
                !isRegistered -> {
                    // Есть фото, не зарегистрирован - оранжевый
                    binding.tvStatus.text = "Не зарегистрирован"
                    setStatusColors(
                        0xFFFF9800.toInt(), // оранжевый
                        0xFFFFF3E0.toInt()  // светло-оранжевый фон
                    )
                    binding.ivPhotoStatus.setImageResource(R.drawable.ic_check_circle)
                    binding.ivPhotoStatus.setColorFilter(0xFFFF9800.toInt())
                }
                else -> {
                    // Зарегистрирован - зелёный
                    binding.tvStatus.text = "Зарегистрирован"
                    setStatusColors(
                        ContextCompat.getColor(context, R.color.green_accent),
                        0xFFE8F5E9.toInt() // светло-зелёный фон
                    )
                    binding.ivPhotoStatus.setImageResource(R.drawable.ic_check_circle)
                    binding.ivPhotoStatus.setColorFilter(ContextCompat.getColor(context, R.color.green_accent))
                }
            }
            
            binding.root.setOnClickListener {
                onMemberClick(member)
            }
        }
        
        private fun setStatusColors(textColor: Int, backgroundColor: Int) {
            binding.tvStatus.setTextColor(textColor)
            val background = binding.tvStatus.background
            if (background is GradientDrawable) {
                background.setColor(backgroundColor)
            } else {
                val drawable = GradientDrawable().apply {
                    cornerRadius = 8f * binding.root.context.resources.displayMetrics.density
                    setColor(backgroundColor)
                }
                binding.tvStatus.background = drawable
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
