package com.example.familyone.utils

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class for Google Drive backup operations
 */
class GoogleDriveHelper(private val context: Context) {
    
    companion object {
        private const val APP_NAME = "FamilyOne"
        private const val BACKUP_FOLDER_NAME = "FamilyOne_Backups"
        private const val BACKUP_MIME_TYPE = "application/json"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    }
    
    private var driveService: Drive? = null
    
    /**
     * Get Google Sign-In client configured for Drive access
     */
    fun getSignInClient(): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA), Scope(DriveScopes.DRIVE_FILE))
            .build()
        
        return GoogleSignIn.getClient(context, signInOptions)
    }
    
    /**
     * Get sign-in intent to launch
     */
    fun getSignInIntent(): Intent {
        return getSignInClient().signInIntent
    }
    
    /**
     * Handle sign-in result from activity result
     */
    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        return try {
            GoogleSignIn.getSignedInAccountFromIntent(data).result
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get currently signed-in account
     */
    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
    
    /**
     * Check if user is signed in to Google
     */
    fun isSignedIn(): Boolean {
        return getSignedInAccount() != null
    }
    
    /**
     * Sign out from Google
     */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            getSignInClient().signOut()
            driveService = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Initialize Drive service with signed-in account
     */
    private fun initDriveService(account: GoogleSignInAccount): Drive {
        if (driveService == null) {
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccount = account.account
            
            driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName(APP_NAME)
                .build()
        }
        return driveService!!
    }
    
    /**
     * Upload backup to Google Drive
     * @param jsonData JSON string with backup data
     * @return Result with file ID on success
     */
    suspend fun uploadBackup(jsonData: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = getSignedInAccount()
                ?: return@withContext Result.failure(Exception("Не авторизован в Google"))
            
            val drive = initDriveService(account)
            
            // Create file metadata
            val timestamp = dateFormat.format(Date())
            val fileName = "familyone_backup_$timestamp.json"
            
            val fileMetadata = DriveFile().apply {
                name = fileName
                parents = listOf("appDataFolder") // Save to app-specific folder
            }
            
            val content = ByteArrayContent(BACKUP_MIME_TYPE, jsonData.toByteArray(Charsets.UTF_8))
            
            val file = drive.files().create(fileMetadata, content)
                .setFields("id, name, createdTime, size")
                .execute()
            
            Result.success(file.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Download backup from Google Drive
     * @param fileId ID of the file to download
     * @return Result with JSON string on success
     */
    suspend fun downloadBackup(fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = getSignedInAccount()
                ?: return@withContext Result.failure(Exception("Не авторизован в Google"))
            
            val drive = initDriveService(account)
            
            val outputStream = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            
            val jsonData = outputStream.toString(Charsets.UTF_8.name())
            Result.success(jsonData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * List all backups in Google Drive
     */
    suspend fun listBackups(): Result<List<DriveBackupInfo>> = withContext(Dispatchers.IO) {
        try {
            val account = getSignedInAccount()
                ?: return@withContext Result.failure(Exception("Не авторизован в Google"))
            
            val drive = initDriveService(account)
            
            val result = drive.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name, createdTime, size)")
                .setOrderBy("createdTime desc")
                .execute()
            
            val backups = result.files?.map { file ->
                DriveBackupInfo(
                    id = file.id,
                    name = file.name,
                    createdTime = file.createdTime?.let { Date(it.value) } ?: Date(),
                    size = file.getSize() ?: 0
                )
            } ?: emptyList()
            
            Result.success(backups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a backup from Google Drive
     */
    suspend fun deleteBackup(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val account = getSignedInAccount()
                ?: return@withContext Result.failure(Exception("Не авторизован в Google"))
            
            val drive = initDriveService(account)
            drive.files().delete(fileId).execute()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class DriveBackupInfo(
    val id: String,
    val name: String,
    val createdTime: Date,
    val size: Long
)
