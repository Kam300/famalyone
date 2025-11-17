package com.example.familyone.utils

import android.content.Context
import android.widget.Toast
import com.example.familyone.R
import com.example.familyone.data.FamilyRole
import com.example.familyone.data.Gender

fun Context.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun FamilyRole.toLocalizedString(context: Context): String {
    return when (this) {
        FamilyRole.GRANDFATHER -> context.getString(R.string.role_grandfather)
        FamilyRole.GRANDMOTHER -> context.getString(R.string.role_grandmother)
        FamilyRole.FATHER -> context.getString(R.string.role_father)
        FamilyRole.MOTHER -> context.getString(R.string.role_mother)
        FamilyRole.SON -> context.getString(R.string.role_son)
        FamilyRole.DAUGHTER -> context.getString(R.string.role_daughter)
        FamilyRole.GRANDSON -> context.getString(R.string.role_grandson)
        FamilyRole.GRANDDAUGHTER -> context.getString(R.string.role_granddaughter)
        FamilyRole.BROTHER -> context.getString(R.string.role_brother)
        FamilyRole.SISTER -> context.getString(R.string.role_sister)
        FamilyRole.UNCLE -> context.getString(R.string.role_uncle)
        FamilyRole.AUNT -> context.getString(R.string.role_aunt)
        FamilyRole.NEPHEW -> context.getString(R.string.role_nephew)
        FamilyRole.NIECE -> context.getString(R.string.role_niece)
        FamilyRole.OTHER -> context.getString(R.string.role_other)
    }
}

fun Gender.toLocalizedString(context: Context): String {
    return when (this) {
        Gender.MALE -> context.getString(R.string.male)
        Gender.FEMALE -> context.getString(R.string.female)
    }
}

