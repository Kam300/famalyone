package com.example.familyone.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface MemberPhotoDao {
    
    @Query("SELECT * FROM member_photos WHERE memberId = :memberId ORDER BY dateAdded DESC")
    fun getPhotosForMember(memberId: Long): LiveData<List<MemberPhoto>>
    
    @Query("SELECT * FROM member_photos WHERE memberId = :memberId ORDER BY dateAdded DESC")
    suspend fun getPhotosForMemberSync(memberId: Long): List<MemberPhoto>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: MemberPhoto): Long
    
    @Delete
    suspend fun deletePhoto(photo: MemberPhoto)
    
    @Query("DELETE FROM member_photos WHERE memberId = :memberId")
    suspend fun deleteAllPhotosForMember(memberId: Long)
    
    @Query("SELECT COUNT(*) FROM member_photos WHERE memberId = :memberId")
    suspend fun getPhotoCount(memberId: Long): Int
}
