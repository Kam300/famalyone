package com.example.familyone.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FamilyMemberDao {
    @Query("SELECT * FROM family_members ORDER BY role, lastName")
    fun getAllMembers(): LiveData<List<FamilyMember>>
    
    @Query("SELECT * FROM family_members ORDER BY role, lastName")
    suspend fun getAllMembersSync(): List<FamilyMember>
    
    @Query("SELECT * FROM family_members WHERE id = :id")
    suspend fun getMemberById(id: Long): FamilyMember?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: FamilyMember): Long
    
    @Update
    suspend fun updateMember(member: FamilyMember)
    
    @Delete
    suspend fun deleteMember(member: FamilyMember)
    
    @Query("DELETE FROM family_members")
    suspend fun deleteAllMembers()
    
    @Query("SELECT * FROM family_members WHERE role = :role")
    suspend fun getMembersByRole(role: FamilyRole): List<FamilyMember>
}

