package com.example.familyone.utils

import android.content.Context
import android.graphics.BitmapFactory
import com.example.familyone.api.FaceRecognitionApi
import com.example.familyone.data.FamilyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class FaceSyncReport(
    val registered: Int,
    val skipped: Int,
    val failed: Int
)

object FaceSyncManager {

    suspend fun syncProfilePhotos(context: Context): FaceSyncReport = withContext(Dispatchers.IO) {
        val database = FamilyDatabase.getDatabase(context)
        val members = database.familyMemberDao().getAllMembersSync()

        val serverFaceIds = FaceRecognitionApi.listFaces()
            .getOrDefault(emptyList())
            .map { it.memberId }
            .toSet()

        var registered = 0
        var skipped = 0
        var failed = 0

        for (member in members) {
            val profileUri = member.photoUri
            if (profileUri.isNullOrBlank()) {
                skipped++
                continue
            }

            val serverId = UniqueIdHelper.toServerId(context, member.id).toString()
            if (serverFaceIds.contains(serverId)) {
                skipped++
                continue
            }

            val photoPath = normalizePath(profileUri)
            val photoFile = File(photoPath)
            if (!photoFile.exists()) {
                failed++
                continue
            }

            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            if (bitmap == null) {
                failed++
                continue
            }

            val fullName = "${member.firstName} ${member.lastName}".trim()
            val result = FaceRecognitionApi.registerFace(
                memberId = UniqueIdHelper.toServerId(context, member.id),
                memberName = fullName,
                photo = bitmap
            )
            bitmap.recycle()

            if (result.isSuccess) {
                registered++
            } else {
                failed++
            }
        }

        FaceSyncReport(
            registered = registered,
            skipped = skipped,
            failed = failed
        )
    }

    private fun normalizePath(rawUri: String): String {
        return if (rawUri.startsWith("file://")) {
            rawUri.removePrefix("file://")
        } else {
            rawUri
        }
    }
}
