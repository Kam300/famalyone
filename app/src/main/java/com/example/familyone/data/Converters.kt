package com.example.familyone.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromGender(value: Gender): String {
        return value.name
    }
    
    @TypeConverter
    fun toGender(value: String): Gender {
        return Gender.valueOf(value)
    }
    
    @TypeConverter
    fun fromFamilyRole(value: FamilyRole): String {
        return value.name
    }
    
    @TypeConverter
    fun toFamilyRole(value: String): FamilyRole {
        return FamilyRole.valueOf(value)
    }
}

