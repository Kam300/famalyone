package com.example.familyone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.familyone.data.FamilyDatabase
import com.example.familyone.data.FamilyMember
import com.example.familyone.data.FamilyRepository
import com.example.familyone.data.FamilyRole
import kotlinx.coroutines.launch

class FamilyViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: FamilyRepository
    val allMembers: LiveData<List<FamilyMember>>
    
    init {
        val familyMemberDao = FamilyDatabase.getDatabase(application).familyMemberDao()
        repository = FamilyRepository(familyMemberDao)
        allMembers = repository.allMembers
    }
    
    fun insertMember(member: FamilyMember, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.insertMember(member)
            onComplete(id)
        }
    }
    
    fun updateMember(member: FamilyMember, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateMember(member)
            onComplete()
        }
    }
    
    fun deleteMember(member: FamilyMember, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteMember(member)
            onComplete()
        }
    }
    
    fun deleteAllMembers(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAllMembers()
            onComplete()
        }
    }
    
    fun getMemberById(id: Long, onComplete: (FamilyMember?) -> Unit) {
        viewModelScope.launch {
            val member = repository.getMemberById(id)
            onComplete(member)
        }
    }
    
    fun getAllMembersSync(onComplete: (List<FamilyMember>) -> Unit) {
        viewModelScope.launch {
            val members = repository.getAllMembersSync()
            onComplete(members)
        }
    }
    
    // Синхронные методы для импорта
    suspend fun getAllMembersSync(): List<FamilyMember> {
        return repository.getAllMembersSync()
    }
    
    suspend fun insertMemberSync(member: FamilyMember): Long {
        return repository.insertMember(member)
    }
    
    suspend fun updateMemberParents(memberId: Long, fatherId: Long?, motherId: Long?) {
        val member = repository.getMemberById(memberId)
        member?.let {
            val updated = it.copy(fatherId = fatherId, motherId = motherId)
            repository.updateMember(updated)
        }
    }
    
    fun getMembersByRole(role: FamilyRole, onComplete: (List<FamilyMember>) -> Unit) {
        viewModelScope.launch {
            val members = repository.getMembersByRole(role)
            onComplete(members)
        }
    }
    
    // Photo methods
    private val photoDao = FamilyDatabase.getDatabase(application).memberPhotoDao()
    
    fun getPhotosForMember(memberId: Long) = photoDao.getPhotosForMember(memberId)
    
    fun insertPhoto(photo: com.example.familyone.data.MemberPhoto, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            photoDao.insertPhoto(photo)
            onComplete()
        }
    }
    
    fun deletePhoto(photo: com.example.familyone.data.MemberPhoto, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            photoDao.deletePhoto(photo)
            onComplete()
        }
    }
}
