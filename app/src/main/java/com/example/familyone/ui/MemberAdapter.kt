package com.example.familyone.ui

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.familyone.R
import com.example.familyone.data.FamilyMember
import com.example.familyone.databinding.ItemFamilyMemberBinding

class MemberAdapter(
    private val onEditClick: (FamilyMember) -> Unit,
    private val onDeleteClick: (FamilyMember) -> Unit,
    private val onContactClick: (FamilyMember) -> Unit,
    private val allMembers: () -> List<FamilyMember>,
    private val onMemberClick: ((FamilyMember) -> Unit)? = null
) : ListAdapter<FamilyMember, MemberAdapter.MemberViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemFamilyMemberBinding.inflate(
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
        private val binding: ItemFamilyMemberBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(member: FamilyMember) {
            binding.tvMemberName.text = member.lastName
            binding.tvMemberDetails.text = "${member.firstName}\n${member.patronymic}"
            binding.tvBirthDate.text = binding.root.context.getString(
                R.string.birth_date_label,
                member.birthDate
            )
            
            // Show parents if they exist
            val parentsText = buildParentsText(member)
            if (parentsText.isNotEmpty()) {
                binding.tvParents.visibility = View.VISIBLE
                binding.tvParents.text = binding.root.context.getString(
                    R.string.parents_label,
                    parentsText
                )
            } else {
                binding.tvParents.visibility = View.GONE
            }
            
            // Show maiden name if exists
            if (!member.maidenName.isNullOrEmpty()) {
                binding.tvMaidenName.visibility = View.VISIBLE
                binding.tvMaidenName.text = binding.root.context.getString(
                    R.string.maiden_name,
                    member.maidenName
                )
            } else {
                binding.tvMaidenName.visibility = View.GONE
            }
            
            // Show wedding date if exists
            if (!member.weddingDate.isNullOrEmpty()) {
                binding.layoutWeddingDate.visibility = View.VISIBLE
                binding.tvWeddingDate.text = "Свадьба: ${member.weddingDate}"
            } else {
                binding.layoutWeddingDate.visibility = View.GONE
            }
            
            // Load photo with optimized Glide settings
            if (!member.photoUri.isNullOrEmpty()) {
                val photoPath = member.photoUri!!.replace("file://", "")
                Glide.with(binding.root.context)
                    .load(java.io.File(photoPath))
                    .override(150, 150) // Thumbnail size for list
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .centerCrop()
                    .into(binding.ivMemberPhoto)
            } else {
                binding.ivMemberPhoto.setImageResource(R.mipmap.ic_launcher)
            }
            
            // Show contact button only if phone number exists
            if (!member.phoneNumber.isNullOrEmpty()) {
                binding.btnContact.visibility = View.VISIBLE
                binding.btnContact.setOnClickListener {
                    onContactClick(member)
                }
            } else {
                binding.btnContact.visibility = View.GONE
            }
            
            binding.btnEdit.setOnClickListener {
                onEditClick(member)
            }
            
            binding.btnDelete.setOnClickListener {
                onDeleteClick(member)
            }
            
            // Клик по карточке открывает профиль
            binding.root.setOnClickListener {
                onMemberClick?.invoke(member)
            }
        }
        
        private fun buildParentsText(member: FamilyMember): String {
            val parents = mutableListOf<String>()
            val members = allMembers()
            
            member.fatherId?.let { fatherId ->
                members.find { it.id == fatherId }?.let { father ->
                    parents.add("${father.firstName} ${father.lastName}")
                }
            }
            
            member.motherId?.let { motherId ->
                members.find { it.id == motherId }?.let { mother ->
                    parents.add("${mother.firstName} ${mother.lastName}")
                }
            }
            
            return parents.joinToString(", ")
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

