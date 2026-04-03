package com.example.familyone.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class YandexAuthCallbackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = intent?.data?.getQueryParameter("status").orEmpty()
        val message = intent?.data?.getQueryParameter("message").orEmpty()
        val provider = intent?.data?.getQueryParameter("provider").orEmpty()

        val targetIntent = Intent(this, BackupActivity::class.java).apply {
            putExtra(EXTRA_AUTH_STATUS, status)
            putExtra(EXTRA_AUTH_MESSAGE, message)
            putExtra(EXTRA_AUTH_PROVIDER, provider)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        startActivity(targetIntent)
        finish()
    }

    companion object {
        const val CALLBACK_URI = "familyone://auth/yandex/callback"
        const val EXTRA_AUTH_STATUS = "familyone_auth_status"
        const val EXTRA_AUTH_MESSAGE = "familyone_auth_message"
        const val EXTRA_AUTH_PROVIDER = "familyone_auth_provider"
    }
}
