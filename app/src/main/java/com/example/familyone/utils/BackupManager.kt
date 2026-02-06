package com.example.familyone.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.familyone.data.FamilyDatabase
import com.example.familyone.data.FamilyMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager for local backup operations
 */
object BackupManager {
    
    private const val BACKUP_FOLDER = "FamilyOne_Backups"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    
    /**
     * Export family data to JSON file in Downloads folder
     * @return Result with file path on success
     */
    suspend fun exportToLocalFile(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Get all members from database
            val database = FamilyDatabase.getDatabase(context)
            val members = database.familyMemberDao().getAllMembersSync()
            
            // Convert to JSON
            val jsonData = DataImportExport.exportToJson(members)
            
            // Create backup folder in Downloads
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupDir = File(downloadsDir, BACKUP_FOLDER)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            
            // Create backup file with timestamp
            val timestamp = dateFormat.format(Date())
            val fileName = "familyone_backup_$timestamp.json"
            val backupFile = File(backupDir, fileName)
            
            // Write JSON to file
            FileOutputStream(backupFile).use { output ->
                output.write(jsonData.toByteArray(Charsets.UTF_8))
            }
            
            Result.success(backupFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Import family data from a JSON file
     * @param uri URI of the selected file
     * @param clearExisting If true, clears existing data before import
     * @return Result with number of imported members
     */
    suspend fun importFromLocalFile(
        context: Context,
        uri: Uri,
        clearExisting: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val database = FamilyDatabase.getDatabase(context)
            val dao = database.familyMemberDao()
            
            // Clear existing data if requested
            if (clearExisting) {
                dao.deleteAllMembers()
            }
            
            // Import from JSON
            val result = DataImportExport.importFromJson(context, uri)
            
            result.fold(
                onSuccess = { members ->
                    // Create mapping from old IDs to new IDs
                    val idMapping = mutableMapOf<Long, Long>()
                    
                    // First pass: insert all members without parent links
                    members.forEach { member ->
                        val memberWithoutParents = member.copy(
                            id = 0, // Let Room generate new ID
                            fatherId = null,
                            motherId = null
                        )
                        val newId = dao.insertMember(memberWithoutParents)
                        idMapping[member.id] = newId
                    }
                    
                    // Second pass: update parent links with new IDs
                    members.forEach { member ->
                        val newId = idMapping[member.id] ?: return@forEach
                        val newFatherId = member.fatherId?.let { idMapping[it] }
                        val newMotherId = member.motherId?.let { idMapping[it] }
                        
                        if (newFatherId != null || newMotherId != null) {
                            val updatedMember = dao.getMemberById(newId)
                            updatedMember?.let {
                                dao.updateMember(it.copy(
                                    fatherId = newFatherId,
                                    motherId = newMotherId
                                ))
                            }
                        }
                    }
                    
                    Result.success(members.size)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get list of local backup files
     */
    fun getLocalBackups(): List<BackupFileInfo> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val backupDir = File(downloadsDir, BACKUP_FOLDER)
        
        if (!backupDir.exists()) return emptyList()
        
        return backupDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.map { file ->
                BackupFileInfo(
                    name = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    lastModified = Date(file.lastModified())
                )
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }
    
    /**
     * Delete a local backup file
     */
    fun deleteLocalBackup(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get raw JSON data for Google Drive upload
     */
    suspend fun getBackupData(context: Context): String = withContext(Dispatchers.IO) {
        val database = FamilyDatabase.getDatabase(context)
        val members = database.familyMemberDao().getAllMembersSync()
        DataImportExport.exportToJson(members)
    }
    
    /**
     * Restore from raw JSON data (for Google Drive download)
     */
    suspend fun restoreFromData(
        context: Context,
        jsonData: String,
        clearExisting: Boolean = true
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val database = FamilyDatabase.getDatabase(context)
            val dao = database.familyMemberDao()
            
            if (clearExisting) {
                dao.deleteAllMembers()
            }
            
            val jsonArray = org.json.JSONArray(jsonData)
            val members = mutableListOf<FamilyMember>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val member = FamilyMember(
                    id = jsonObject.getLong("id"),
                    firstName = jsonObject.getString("firstName"),
                    lastName = jsonObject.getString("lastName"),
                    patronymic = jsonObject.optString("patronymic").takeIf { it.isNotEmpty() },
                    gender = com.example.familyone.data.Gender.valueOf(jsonObject.getString("gender")),
                    birthDate = jsonObject.getString("birthDate"),
                    role = com.example.familyone.data.FamilyRole.valueOf(jsonObject.getString("role")),
                    phoneNumber = jsonObject.optString("phoneNumber").takeIf { it.isNotEmpty() },
                    fatherId = if (jsonObject.isNull("fatherId")) null else jsonObject.getLong("fatherId"),
                    motherId = if (jsonObject.isNull("motherId")) null else jsonObject.getLong("motherId"),
                    weddingDate = jsonObject.optString("weddingDate").takeIf { it.isNotEmpty() },
                    maidenName = jsonObject.optString("maidenName").takeIf { it.isNotEmpty() },
                    photoUri = null
                )
                members.add(member)
            }
            
            // Same two-pass import as importFromLocalFile
            val idMapping = mutableMapOf<Long, Long>()
            
            members.forEach { member ->
                val memberWithoutParents = member.copy(id = 0, fatherId = null, motherId = null)
                val newId = dao.insertMember(memberWithoutParents)
                idMapping[member.id] = newId
            }
            
            members.forEach { member ->
                val newId = idMapping[member.id] ?: return@forEach
                val newFatherId = member.fatherId?.let { idMapping[it] }
                val newMotherId = member.motherId?.let { idMapping[it] }
                
                if (newFatherId != null || newMotherId != null) {
                    val updatedMember = dao.getMemberById(newId)
                    updatedMember?.let {
                        dao.updateMember(it.copy(fatherId = newFatherId, motherId = newMotherId))
                    }
                }
            }
            
            Result.success(members.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class BackupFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Date
)
