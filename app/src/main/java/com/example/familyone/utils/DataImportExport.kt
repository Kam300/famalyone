package com.example.familyone.utils

import android.content.Context
import android.net.Uri
import com.example.familyone.data.FamilyMember
import com.example.familyone.data.FamilyRole
import com.example.familyone.data.Gender
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object DataImportExport {
    
    /**
     * Импорт данных из JSON файла
     * @param context Контекст приложения
     * @param uri URI выбранного файла
     * @return Список членов семьи для добавления в БД
     */
    fun importFromJson(context: Context, uri: Uri): Result<List<FamilyMember>> {
        return try {
            val jsonString = readFileContent(context, uri)
            val members = parseJsonToMembers(jsonString)
            Result.success(members)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Экспорт данных в JSON с полными связями
     * @param members Список членов семьи
     * @return JSON строка
     */
    fun exportToJson(members: List<FamilyMember>): String {
        val jsonArray = JSONArray()
        
        members.forEach { member ->
            val jsonObject = JSONObject().apply {
                // Основная информация
                put("id", member.id)
                put("firstName", member.firstName)
                put("lastName", member.lastName)
                put("patronymic", member.patronymic ?: "")
                put("gender", member.gender.name)
                put("birthDate", member.birthDate)
                put("role", member.role.name)
                put("phoneNumber", member.phoneNumber ?: "")
                
                // Связи с родителями
                put("fatherId", member.fatherId ?: JSONObject.NULL)
                put("motherId", member.motherId ?: JSONObject.NULL)
                
                // Дополнительная информация
                put("weddingDate", member.weddingDate ?: "")
                put("maidenName", member.maidenName ?: "")
                
                // Добавляем имена родителей для удобства чтения
                if (member.fatherId != null) {
                    val father = members.find { it.id == member.fatherId }
                    put("fatherName", father?.let { "${it.firstName} ${it.lastName}" } ?: "")
                }
                if (member.motherId != null) {
                    val mother = members.find { it.id == member.motherId }
                    put("motherName", mother?.let { "${it.firstName} ${it.lastName}" } ?: "")
                }
                
                // Добавляем список детей для удобства
                val children = members.filter { 
                    it.fatherId == member.id || it.motherId == member.id 
                }
                if (children.isNotEmpty()) {
                    val childrenArray = JSONArray()
                    children.forEach { child ->
                        childrenArray.put("${child.firstName} ${child.lastName}")
                    }
                    put("children", childrenArray)
                }
            }
            jsonArray.put(jsonObject)
        }
        
        return jsonArray.toString(2) // Pretty print with indent
    }
    
    private fun readFileContent(context: Context, uri: Uri): String {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line)
                }
            }
        }
        return stringBuilder.toString()
    }
    
    private fun parseJsonToMembers(jsonString: String): List<FamilyMember> {
        val members = mutableListOf<FamilyMember>()
        val jsonArray = JSONArray(jsonString)
        
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            
            val member = FamilyMember(
                id = jsonObject.getLong("id"), // Сохраняем оригинальный ID для маппинга
                firstName = jsonObject.getString("firstName"),
                lastName = jsonObject.getString("lastName"),
                patronymic = jsonObject.optString("patronymic").takeIf { it.isNotEmpty() },
                gender = Gender.valueOf(jsonObject.getString("gender")),
                birthDate = jsonObject.getString("birthDate"),
                role = FamilyRole.valueOf(jsonObject.getString("role")),
                phoneNumber = jsonObject.optString("phoneNumber").takeIf { it.isNotEmpty() },
                fatherId = if (jsonObject.isNull("fatherId")) null else jsonObject.getLong("fatherId"),
                motherId = if (jsonObject.isNull("motherId")) null else jsonObject.getLong("motherId"),
                weddingDate = jsonObject.optString("weddingDate").takeIf { it.isNotEmpty() },
                maidenName = jsonObject.optString("maidenName").takeIf { it.isNotEmpty() },
                photoUri = null // Импорт без фотографий
            )
            
            members.add(member)
        }
        
        return members
    }
}
