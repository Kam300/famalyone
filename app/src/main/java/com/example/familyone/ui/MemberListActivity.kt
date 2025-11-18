package com.example.familyone.ui

import android.content .Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.familyone.R
import com.example.familyone.databinding.ActivityMemberListBinding
import com.example.familyone.utils.toast
import com.example.familyone.utils.toLocalizedString
import com.example.familyone.viewmodel.FamilyViewModel

class MemberListActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMemberListBinding
    private lateinit var viewModel: FamilyViewModel
    private lateinit var adapter: MemberAdapter
    private var allMembers: List<com.example.familyone.data.FamilyMember> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemberListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = ViewModelProvider(this)[FamilyViewModel::class.java]
        
        setupRecyclerView()
        setupClickListeners()
        observeMembers()
    }
    
    private fun setupRecyclerView() {
        adapter = MemberAdapter(
            onEditClick = { member ->
                val intent = Intent(this, AddMemberActivity::class.java)
                intent.putExtra("MEMBER_ID", member.id)
                startActivity(intent)
            },
            onDeleteClick = { member ->
                showDeleteConfirmation(member)
            },
            onContactClick = { member ->
                showContactOptions(member)
            },
            allMembers = { viewModel.allMembers.value ?: emptyList() },
            onMemberClick = { member ->
                val intent = Intent(this, MemberProfileActivity::class.java)
                intent.putExtra("MEMBER_ID", member.id)
                startActivity(intent)
            }
        )
        
        binding.rvMembers.layoutManager = LinearLayoutManager(this)
        binding.rvMembers.adapter = adapter
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        
        binding.btnDeleteAll.setOnClickListener {
            showDeleteAllConfirmation()
        }
        
        // Search functionality
        binding.etSearch.addTextChangedListener { text ->
            filterMembers(text.toString())
        }
    }
    
    private fun filterMembers(query: String) {
        if (query.isEmpty()) {
            adapter.submitList(allMembers)
            updateEmptyState(allMembers.isEmpty())
            return
        }
        
        val filtered = allMembers.filter { member ->
            member.firstName.contains(query, ignoreCase = true) ||
            member.lastName.contains(query, ignoreCase = true) ||
                    member.patronymic?.contains(query, ignoreCase = true) ?:
            member.role.toLocalizedString(this).contains(query, ignoreCase = true)
        }
        
        adapter.submitList(filtered)
        updateEmptyState(filtered.isEmpty())
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvMembers.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvMembers.visibility = View.VISIBLE
        }
    }
    
    private fun observeMembers() {
        viewModel.allMembers.observe(this) { members ->
            allMembers = members
            filterMembers(binding.etSearch.text.toString())
        }
    }
    
    private fun showDeleteConfirmation(member: com.example.familyone.data.FamilyMember) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_confirmation))
            .setMessage("${member.firstName} ${member.lastName}")
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                viewModel.deleteMember(member) {
                    runOnUiThread {
                        toast(getString(R.string.ok))
                    }
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }
    
    private fun showDeleteAllConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_confirmation))
            .setMessage(getString(R.string.delete_all_confirmation))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                viewModel.deleteAllMembers {
                    runOnUiThread {
                        toast(getString(R.string.ok))
                    }
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }
    
    private fun showContactOptions(member: com.example.familyone.data.FamilyMember) {
        val options = listOf(
            ContactOption(getString(R.string.telegram_app), R.drawable.ic_telegram),
            ContactOption(getString(R.string.whatsapp_app), R.drawable.ic_whatsapp),
            ContactOption(getString(R.string.call), R.drawable.ic_phone)
        )
        
        val adapter = ContactDialogAdapter(this, options)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.contact_via))
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> openTelegram(member.phoneNumber ?: "")
                    1 -> openWhatsApp(member.phoneNumber ?: "")
                    2 -> makeCall(member.phoneNumber ?: "")
                }
            }
            .show()
    }
    
    private fun openTelegram(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse("https://t.me/$phoneNumber")
            startActivity(intent)
        } catch (e: Exception) {
            toast("Telegram не установлен")
        }
    }
    
    private fun openWhatsApp(phoneNumber: String) {
        try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse("https://wa.me/$cleanNumber")
            startActivity(intent)
        } catch (e: Exception) {
            toast("WhatsApp не установлен")
        }
    }
    
    private fun makeCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = android.net.Uri.parse("tel:$phoneNumber")
            startActivity(intent)
        } catch (e: Exception) {
            toast("Невозможно совершить звонок")
        }
    }
}

