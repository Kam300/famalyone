package com.example.familyone.data

import androidx.lifecycle.LiveData

class FamilyRepository(private val familyMemberDao: FamilyMemberDao) {
    
    val allMembers: LiveData<List<FamilyMember>> = familyMemberDao.getAllMembers()
    
    suspend fun getAllMembersSync(): List<FamilyMember> {
        return familyMemberDao.getAllMembersSync()
    }
    
    suspend fun getMemberById(id: Long): FamilyMember? {
        return familyMemberDao.getMemberById(id)
    }
    
    suspend fun insertMember(member: FamilyMember): Long {
        return familyMemberDao.insertMember(member)
    }
    
    suspend fun updateMember(member: FamilyMember) {
        familyMemberDao.updateMember(member)
    }
    
    suspend fun deleteMember(member: FamilyMember) {
        familyMemberDao.deleteMember(member)
    }
    
    suspend fun deleteAllMembers() {
        familyMemberDao.deleteAllMembers()
    }
    
    suspend fun getMembersByRole(role: FamilyRole): List<FamilyMember> {
        return familyMemberDao.getMembersByRole(role)
    }
}

