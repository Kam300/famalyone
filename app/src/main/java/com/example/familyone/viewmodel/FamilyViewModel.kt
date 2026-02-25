package com.example.familyone.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.familyone.api.FaceRecognitionApi
import com.example.familyone.data.FamilyDatabase
import com.example.familyone.data.FamilyMember
import com.example.familyone.data.FamilyRepository
import com.example.familyone.data.FamilyRole
import com.example.familyone.utils.UniqueIdHelper
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
            // Удаляем лицо с сервера распознавания
            try {
                FaceRecognitionApi.deleteFace(member.id)
                Log.d("FamilyViewModel", "Лицо удалено с сервера для ID: ${member.id}")
            } catch (e: Exception) {
                Log.w("FamilyViewModel", "Не удалось удалить лицо с сервера: ${e.message}")
            }
            // Удаляем из локальной базы
            repository.deleteMember(member)
            onComplete()
        }
    }
    
    fun deleteAllMembers(onComplete: () -> Unit = {}) {
        deleteAllMembersWithStatus { _, _ ->
            onComplete()
        }
    }

    fun deleteAllMembersWithStatus(onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            // Очищаем лица на сервере для текущего устройства
            val deviceId = UniqueIdHelper.getDeviceId(getApplication())
            val clearServerResult = FaceRecognitionApi.clearAll(deviceId)
            val serverCleared = clearServerResult.isSuccess
            val serverError = clearServerResult.exceptionOrNull()?.message
            if (clearServerResult.isSuccess) {
                Log.d("FamilyViewModel", "Все лица удалены с сервера для device_id=$deviceId")
            } else {
                val errorMessage = serverError ?: "unknown error"
                Log.w("FamilyViewModel", "Не удалось очистить сервер для device_id=$deviceId: $errorMessage")
            }
            // Удаляем из локальной базы
            repository.deleteAllMembers()
            onComplete(serverCleared, serverError)
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
