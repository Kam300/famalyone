package com.example.familyone.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.familyone.data.FamilyDatabase
import com.example.familyone.data.FamilyMember
import com.example.familyone.data.FamilyRole
import com.example.familyone.data.Gender
import com.example.familyone.data.MemberPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class BackupArchiveBuildResult(
    val archiveFile: File,
    val schemaVersion: Int,
    val createdAtUtc: String,
    val membersCount: Int,
    val memberPhotosCount: Int,
    val assetsCount: Int,
    val sizeBytes: Long,
    val checksumSha256: String
)

data class BackupRestoreReport(
    val membersInserted: Int,
    val membersMatched: Int,
    val photosAdded: Int,
    val photosSkippedDuplicates: Int,
    val errors: Int
)

object BackupArchiveManager {

    private const val SCHEMA_VERSION = 1
    private const val JPEG_QUALITY = 80
    private const val MAX_IMAGE_EDGE = 1280

    private val utcDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private data class BackupMemberRecord(
        val backupMemberKey: String,
        val firstName: String,
        val lastName: String,
        val patronymic: String?,
        val gender: Gender,
        val birthDate: String,
        val role: FamilyRole,
        val phoneNumber: String?,
        val maidenName: String?,
        val weddingDate: String?,
        val fatherBackupKey: String?,
        val motherBackupKey: String?,
        val profilePhotoAssetId: String?
    )

    private data class BackupPhotoRecord(
        val backupMemberKey: String,
        val photoAssetId: String,
        val dateAdded: Long,
        val description: String?,
        val isProfilePhoto: Boolean
    )

    suspend fun createArchive(
        context: Context,
        maxSizeBytes: Long = 250L * 1024L * 1024L
    ): Result<BackupArchiveBuildResult> = withContext(Dispatchers.IO) {
        try {
            val database = FamilyDatabase.getDatabase(context)
            val memberDao = database.familyMemberDao()
            val photoDao = database.memberPhotoDao()

            val members = memberDao.getAllMembersSync()
            val photos = photoDao.getAllPhotosSync()

            val memberKeyById = members.associate { it.id to "member_${it.id}" }
            val profileAssetByMemberKey = mutableMapOf<String, String?>()
            val assetBytesById = linkedMapOf<String, ByteArray>()

            fun ensureAsset(uriString: String?): String? {
                if (uriString.isNullOrBlank()) return null
                val bitmap = loadBitmapFromUriString(context, uriString) ?: return null
                return try {
                    val bytes = compressBitmapForBackup(bitmap)
                    val assetId = sha256(bytes)
                    if (!assetBytesById.containsKey(assetId)) {
                        assetBytesById[assetId] = bytes
                    }
                    assetId
                } finally {
                    bitmap.recycle()
                }
            }

            for (member in members) {
                val key = memberKeyById[member.id] ?: continue
                profileAssetByMemberKey[key] = ensureAsset(member.photoUri)
            }

            val memberPhotosArray = JSONArray()
            for (photo in photos) {
                val backupMemberKey = memberKeyById[photo.memberId] ?: continue
                val assetId = ensureAsset(photo.photoUri) ?: continue
                val photoJson = JSONObject().apply {
                    put("backupMemberKey", backupMemberKey)
                    put("photoAssetId", assetId)
                    put("dateAdded", photo.dateAdded)
                    put("description", photo.description ?: "")
                    put("isProfilePhoto", photo.isProfilePhoto)
                }
                memberPhotosArray.put(photoJson)
            }

            val membersArray = JSONArray()
            for (member in members) {
                val backupMemberKey = memberKeyById[member.id] ?: continue
                val memberJson = JSONObject().apply {
                    put("backupMemberKey", backupMemberKey)
                    put("firstName", member.firstName)
                    put("lastName", member.lastName)
                    put("patronymic", member.patronymic ?: "")
                    put("gender", member.gender.name)
                    put("birthDate", member.birthDate)
                    put("role", member.role.name)
                    put("phoneNumber", member.phoneNumber ?: "")
                    put("maidenName", member.maidenName ?: "")
                    put("weddingDate", member.weddingDate ?: "")
                    put("fatherBackupKey", member.fatherId?.let { memberKeyById[it] } ?: JSONObject.NULL)
                    put("motherBackupKey", member.motherId?.let { memberKeyById[it] } ?: JSONObject.NULL)
                    put("profilePhotoAssetId", profileAssetByMemberKey[backupMemberKey] ?: JSONObject.NULL)
                }
                membersArray.put(memberJson)
            }

            val membersJson = membersArray.toString()
            val memberPhotosJson = memberPhotosArray.toString()
            val checksumInput = buildString {
                append(membersJson)
                append(memberPhotosJson)
                assetBytesById.keys.sorted().forEach { append(it) }
            }
            val checksumSha256 = sha256(checksumInput.toByteArray(Charsets.UTF_8))
            val createdAtUtc = utcDateFormat.format(Date())

            val manifestJson = JSONObject().apply {
                put("schemaVersion", SCHEMA_VERSION)
                put("createdAtUtc", createdAtUtc)
                put("appVersion", resolveAppVersion(context))
                put("compression", "jpeg_1280_q80")
                put("counts", JSONObject().apply {
                    put("members", members.size)
                    put("memberPhotos", memberPhotosArray.length())
                    put("assets", assetBytesById.size)
                })
                put("checksumSha256", checksumSha256)
            }.toString()

            val archiveFile = File(context.cacheDir, "familyone_backup_${System.currentTimeMillis()}.zip")
            ZipOutputStream(FileOutputStream(archiveFile)).use { zip ->
                writeZipEntry(zip, "manifest.json", manifestJson.toByteArray(Charsets.UTF_8))
                writeZipEntry(zip, "members.json", membersJson.toByteArray(Charsets.UTF_8))
                writeZipEntry(zip, "member_photos.json", memberPhotosJson.toByteArray(Charsets.UTF_8))

                for ((assetId, bytes) in assetBytesById) {
                    writeZipEntry(zip, "assets/$assetId.jpg", bytes)
                }
            }

            val sizeBytes = archiveFile.length()
            if (sizeBytes <= 0L) {
                archiveFile.delete()
                return@withContext Result.failure(Exception("Не удалось создать архив backup"))
            }
            if (sizeBytes > maxSizeBytes) {
                archiveFile.delete()
                return@withContext Result.failure(
                    Exception("Размер backup превышает лимит ${maxSizeBytes / 1024 / 1024} MB")
                )
            }

            Result.success(
                BackupArchiveBuildResult(
                    archiveFile = archiveFile,
                    schemaVersion = SCHEMA_VERSION,
                    createdAtUtc = createdAtUtc,
                    membersCount = members.size,
                    memberPhotosCount = memberPhotosArray.length(),
                    assetsCount = assetBytesById.size,
                    sizeBytes = sizeBytes,
                    checksumSha256 = checksumSha256
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromArchive(
        context: Context,
        archiveFile: File
    ): Result<BackupRestoreReport> = withContext(Dispatchers.IO) {
        try {
            if (!archiveFile.exists()) {
                return@withContext Result.failure(Exception("Архив не найден"))
            }

            ZipFile(archiveFile).use { zip ->
                ensureRequiredEntries(zip)

                val manifest = JSONObject(readEntryAsString(zip, "manifest.json"))
                val schemaVersion = manifest.optInt("schemaVersion", 0)
                if (schemaVersion < 1) {
                    return@withContext Result.failure(Exception("Неподдерживаемая версия backup schema"))
                }

                val membersArray = JSONArray(readEntryAsString(zip, "members.json"))
                val photosArray = JSONArray(readEntryAsString(zip, "member_photos.json"))
                val backupMembers = parseBackupMembers(membersArray)
                val backupPhotos = parseBackupPhotos(photosArray)

                val database = FamilyDatabase.getDatabase(context)
                val memberDao = database.familyMemberDao()
                val photoDao = database.memberPhotoDao()

                val existingMembers = memberDao.getAllMembersSync()
                val fingerprintIndex = existingMembers.associateBy { fingerprint(it) }.toMutableMap()
                val memberKeyToLocalId = mutableMapOf<String, Long>()
                val pendingRelations = mutableListOf<Triple<Long, String?, String?>>()
                val pendingProfileAssignments = mutableListOf<Pair<Long, String>>()

                var membersInserted = 0
                var membersMatched = 0
                var errors = 0

                for (backupMember in backupMembers) {
                    if (backupMember.backupMemberKey.isBlank()) {
                        errors++
                        continue
                    }

                    val key = fingerprint(backupMember)
                    val existing = fingerprintIndex[key]
                    if (existing != null) {
                        val merged = mergeMember(existing, backupMember)
                        if (merged != existing) {
                            memberDao.updateMember(merged)
                            fingerprintIndex[key] = merged
                        }
                        memberKeyToLocalId[backupMember.backupMemberKey] = existing.id
                        membersMatched++
                    } else {
                        val insertedId = memberDao.insertMember(
                            FamilyMember(
                                firstName = backupMember.firstName,
                                lastName = backupMember.lastName,
                                patronymic = backupMember.patronymic,
                                gender = backupMember.gender,
                                birthDate = backupMember.birthDate,
                                phoneNumber = backupMember.phoneNumber,
                                role = backupMember.role,
                                photoUri = null,
                                maidenName = backupMember.maidenName,
                                fatherId = null,
                                motherId = null,
                                weddingDate = backupMember.weddingDate
                            )
                        )
                        val insertedMember = memberDao.getMemberById(insertedId)
                        if (insertedMember != null) {
                            fingerprintIndex[key] = insertedMember
                        }
                        memberKeyToLocalId[backupMember.backupMemberKey] = insertedId
                        membersInserted++
                    }

                    val localId = memberKeyToLocalId[backupMember.backupMemberKey] ?: continue
                    pendingRelations.add(
                        Triple(
                            localId,
                            backupMember.fatherBackupKey,
                            backupMember.motherBackupKey
                        )
                    )
                    backupMember.profilePhotoAssetId?.let {
                        pendingProfileAssignments.add(localId to it)
                    }
                }

                for ((localId, fatherKey, motherKey) in pendingRelations) {
                    val member = memberDao.getMemberById(localId) ?: continue
                    val resolvedFatherId = if (member.fatherId == null) {
                        fatherKey?.let { memberKeyToLocalId[it] }
                    } else {
                        member.fatherId
                    }
                    val resolvedMotherId = if (member.motherId == null) {
                        motherKey?.let { memberKeyToLocalId[it] }
                    } else {
                        member.motherId
                    }

                    if (resolvedFatherId != member.fatherId || resolvedMotherId != member.motherId) {
                        memberDao.updateMember(
                            member.copy(
                                fatherId = resolvedFatherId,
                                motherId = resolvedMotherId
                            )
                        )
                    }
                }

                val memberHashSets = mutableMapOf<Long, MutableSet<String>>()
                val memberHashPaths = mutableMapOf<Long, MutableMap<String, String>>()
                val savedAssetPathByMember = mutableMapOf<String, String>()

                suspend fun ensureMemberHashes(memberId: Long) {
                    if (memberHashSets.containsKey(memberId)) return

                    val hashSet = mutableSetOf<String>()
                    val hashPaths = mutableMapOf<String, String>()
                    val existingPhotosForMember = photoDao.getPhotosForMemberSync(memberId)
                    for (photo in existingPhotosForMember) {
                        val file = File(normalizePath(photo.photoUri))
                        if (!file.exists()) continue
                        val hash = hashFile(file) ?: continue
                        hashSet.add(hash)
                        hashPaths.putIfAbsent(hash, file.absolutePath)
                    }
                    memberHashSets[memberId] = hashSet
                    memberHashPaths[memberId] = hashPaths
                }

                var photosAdded = 0
                var photosSkippedDuplicates = 0

                for (photo in backupPhotos) {
                    val localMemberId = memberKeyToLocalId[photo.backupMemberKey]
                    if (localMemberId == null) {
                        errors++
                        continue
                    }
                    ensureMemberHashes(localMemberId)

                    val assetBytes = readAssetBytes(zip, photo.photoAssetId)
                    if (assetBytes == null) {
                        errors++
                        continue
                    }

                    val hash = sha256(assetBytes)
                    val knownHashes = memberHashSets.getValue(localMemberId)
                    val knownPaths = memberHashPaths.getValue(localMemberId)
                    if (knownHashes.contains(hash)) {
                        photosSkippedDuplicates++
                        continue
                    }

                    val savedFile = savePhotoBytes(context, localMemberId, assetBytes)
                    photoDao.insertPhoto(
                        MemberPhoto(
                            memberId = localMemberId,
                            photoUri = savedFile.absolutePath,
                            dateAdded = photo.dateAdded,
                            description = photo.description,
                            isProfilePhoto = photo.isProfilePhoto
                        )
                    )
                    knownHashes.add(hash)
                    knownPaths[hash] = savedFile.absolutePath
                    savedAssetPathByMember["$localMemberId|${photo.photoAssetId}"] = savedFile.absolutePath
                    photosAdded++
                }

                for ((localMemberId, assetId) in pendingProfileAssignments) {
                    if (assetId.isBlank()) continue
                    ensureMemberHashes(localMemberId)

                    val member = memberDao.getMemberById(localMemberId) ?: continue
                    if (!member.photoUri.isNullOrBlank()) continue

                    var profilePath = savedAssetPathByMember["$localMemberId|$assetId"]
                    if (profilePath.isNullOrBlank()) {
                        val assetBytes = readAssetBytes(zip, assetId)
                        if (assetBytes == null) {
                            errors++
                            continue
                        }

                        val hash = sha256(assetBytes)
                        val knownHashes = memberHashSets.getValue(localMemberId)
                        val knownPaths = memberHashPaths.getValue(localMemberId)

                        profilePath = knownPaths[hash]
                        if (profilePath.isNullOrBlank()) {
                            val savedFile = savePhotoBytes(context, localMemberId, assetBytes, "profile")
                            photoDao.insertPhoto(
                                MemberPhoto(
                                    memberId = localMemberId,
                                    photoUri = savedFile.absolutePath,
                                    dateAdded = System.currentTimeMillis(),
                                    description = null,
                                    isProfilePhoto = true
                                )
                            )
                            knownHashes.add(hash)
                            knownPaths[hash] = savedFile.absolutePath
                            profilePath = savedFile.absolutePath
                            photosAdded++
                        }
                    }

                    if (!profilePath.isNullOrBlank()) {
                        memberDao.updateMember(member.copy(photoUri = profilePath))
                    }
                }

                Result.success(
                    BackupRestoreReport(
                        membersInserted = membersInserted,
                        membersMatched = membersMatched,
                        photosAdded = photosAdded,
                        photosSkippedDuplicates = photosSkippedDuplicates,
                        errors = errors
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun ensureRequiredEntries(zip: ZipFile) {
        val required = listOf("manifest.json", "members.json", "member_photos.json")
        for (entryName in required) {
            if (zip.getEntry(entryName) == null) {
                throw IllegalArgumentException("В архиве отсутствует обязательный файл: $entryName")
            }
        }
    }

    private fun readEntryAsString(zip: ZipFile, entryName: String): String {
        val entry = zip.getEntry(entryName)
            ?: throw IllegalArgumentException("Zip entry not found: $entryName")
        return zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun parseBackupMembers(source: JSONArray): List<BackupMemberRecord> {
        val result = mutableListOf<BackupMemberRecord>()
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index) ?: continue
            val backupMemberKey = item.optString("backupMemberKey", "").trim()
            val firstName = item.optString("firstName", "").trim()
            val lastName = item.optString("lastName", "").trim()
            if (backupMemberKey.isBlank() || firstName.isBlank() || lastName.isBlank()) {
                continue
            }

            val role = parseRole(item.optString("role"))
            val gender = parseGender(item.optString("gender"))
            val record = BackupMemberRecord(
                backupMemberKey = backupMemberKey,
                firstName = firstName,
                lastName = lastName,
                patronymic = item.optString("patronymic").ifBlank { null },
                gender = gender,
                birthDate = item.optString("birthDate", ""),
                role = role,
                phoneNumber = item.optString("phoneNumber").ifBlank { null },
                maidenName = item.optString("maidenName").ifBlank { null },
                weddingDate = item.optString("weddingDate").ifBlank { null },
                fatherBackupKey = item.optString("fatherBackupKey").ifBlank { null },
                motherBackupKey = item.optString("motherBackupKey").ifBlank { null },
                profilePhotoAssetId = item.optString("profilePhotoAssetId").ifBlank { null }
            )
            result.add(record)
        }
        return result
    }

    private fun parseBackupPhotos(source: JSONArray): List<BackupPhotoRecord> {
        val result = mutableListOf<BackupPhotoRecord>()
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index) ?: continue
            val backupMemberKey = item.optString("backupMemberKey", "").trim()
            val photoAssetId = item.optString("photoAssetId", "").trim()
            if (backupMemberKey.isBlank() || photoAssetId.isBlank()) {
                continue
            }
            result.add(
                BackupPhotoRecord(
                    backupMemberKey = backupMemberKey,
                    photoAssetId = photoAssetId,
                    dateAdded = item.optLong("dateAdded", System.currentTimeMillis()),
                    description = item.optString("description").ifBlank { null },
                    isProfilePhoto = item.optBoolean("isProfilePhoto", false)
                )
            )
        }
        return result
    }

    private fun parseRole(raw: String?): FamilyRole {
        return try {
            FamilyRole.valueOf(raw.orEmpty().trim().uppercase(Locale.US))
        } catch (_: Exception) {
            FamilyRole.OTHER
        }
    }

    private fun parseGender(raw: String?): Gender {
        return try {
            Gender.valueOf(raw.orEmpty().trim().uppercase(Locale.US))
        } catch (_: Exception) {
            Gender.MALE
        }
    }

    private fun mergeMember(current: FamilyMember, backup: BackupMemberRecord): FamilyMember {
        return current.copy(
            firstName = current.firstName.ifBlank { backup.firstName },
            lastName = current.lastName.ifBlank { backup.lastName },
            patronymic = current.patronymic ?: backup.patronymic,
            birthDate = current.birthDate.ifBlank { backup.birthDate },
            phoneNumber = current.phoneNumber ?: backup.phoneNumber,
            maidenName = current.maidenName ?: backup.maidenName,
            weddingDate = current.weddingDate ?: backup.weddingDate
        )
    }

    private fun fingerprint(member: FamilyMember): String {
        return buildString {
            append(member.lastName.trim().lowercase(Locale.US))
            append('|')
            append(member.firstName.trim().lowercase(Locale.US))
            append('|')
            append(member.patronymic.orEmpty().trim().lowercase(Locale.US))
            append('|')
            append(member.birthDate.trim())
            append('|')
            append(member.role.name)
        }
    }

    private fun fingerprint(member: BackupMemberRecord): String {
        return buildString {
            append(member.lastName.trim().lowercase(Locale.US))
            append('|')
            append(member.firstName.trim().lowercase(Locale.US))
            append('|')
            append(member.patronymic.orEmpty().trim().lowercase(Locale.US))
            append('|')
            append(member.birthDate.trim())
            append('|')
            append(member.role.name)
        }
    }

    private fun loadBitmapFromUriString(context: Context, rawUri: String): Bitmap? {
        val normalized = normalizePath(rawUri)
        return try {
            val directFile = File(normalized)
            if (directFile.exists()) {
                BitmapFactory.decodeFile(directFile.absolutePath)
            } else {
                val parsed = Uri.parse(rawUri)
                context.contentResolver.openInputStream(parsed)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun compressBitmapForBackup(bitmap: Bitmap): ByteArray {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        val processed = if (maxSide > MAX_IMAGE_EDGE) {
            val ratio = MAX_IMAGE_EDGE.toFloat() / maxSide.toFloat()
            val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
            val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }

        val output = ByteArrayOutputStream()
        try {
            processed.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            return output.toByteArray()
        } finally {
            if (processed !== bitmap) {
                processed.recycle()
            }
            output.close()
        }
    }

    private fun sha256(source: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(source)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hashFile(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun readAssetBytes(zip: ZipFile, assetId: String): ByteArray? {
        val knownNames = listOf(
            "assets/$assetId.jpg",
            "assets/$assetId.jpeg",
            "assets/$assetId.png"
        )
        val entry = knownNames.firstNotNullOfOrNull { name ->
            zip.getEntry(name)
        } ?: findAssetEntryByPrefix(zip, "assets/$assetId")
        return entry?.let { zip.getInputStream(it).use { input -> input.readBytes() } }
    }

    private fun findAssetEntryByPrefix(zip: ZipFile, prefix: String): ZipEntry? {
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.isDirectory && entry.name.startsWith(prefix)) {
                return entry
            }
        }
        return null
    }

    private fun savePhotoBytes(
        context: Context,
        memberId: Long,
        bytes: ByteArray,
        prefix: String = "photo"
    ): File {
        val dir = File(context.filesDir, "backup_photos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        var attempt = 0
        var file = File(dir, "${prefix}_${memberId}_${System.currentTimeMillis()}.jpg")
        while (file.exists()) {
            attempt++
            file = File(dir, "${prefix}_${memberId}_${System.currentTimeMillis()}_$attempt.jpg")
        }
        FileOutputStream(file).use { output -> output.write(bytes) }
        return file
    }

    private fun normalizePath(rawUri: String): String {
        return if (rawUri.startsWith("file://")) {
            rawUri.removePrefix("file://")
        } else {
            rawUri
        }
    }

    private fun resolveAppVersion(context: Context): String {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
