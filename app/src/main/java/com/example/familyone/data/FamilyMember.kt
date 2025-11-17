package com.example.familyone.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val firstName: String,
    val lastName: String,
    val patronymic: String? = null,
    val gender: Gender,
    val birthDate: String,
    val phoneNumber: String? = null,
    val role: FamilyRole,
    val photoUri: String? = null,
    val maidenName: String? = null,
    val fatherId: Long? = null,
    val motherId: Long? = null,
    val weddingDate: String? = null
)

enum class Gender {
    MALE,
    FEMALE
}

enum class FamilyRole {
    GRANDFATHER,
    GRANDMOTHER,
    FATHER,
    MOTHER,
    SON,
    DAUGHTER,
    GRANDSON,
    GRANDDAUGHTER,
    BROTHER,
    SISTER,
    UNCLE,
    AUNT,
    NEPHEW,
    NIECE,
    OTHER
}

