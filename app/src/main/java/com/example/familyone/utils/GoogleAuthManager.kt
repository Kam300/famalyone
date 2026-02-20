package com.example.familyone.utils

import android.content.Context
import android.content.Intent
import com.example.familyone.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleAuthManager(private val context: Context) {

    private val webClientId: String by lazy {
        context.getString(R.string.google_web_client_id).trim()
    }

    fun isConfigured(): Boolean {
        return webClientId.isNotBlank() && !webClientId.startsWith("YOUR_", ignoreCase = true)
    }

    fun getSignInClient(): GoogleSignInClient {
        if (!isConfigured()) {
            throw IllegalStateException(
                "Google OAuth WEB client id is not configured. " +
                    "Set @string/google_web_client_id."
            )
        }

        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(webClientId)
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }

    fun getSignInIntent(): Intent {
        return getSignInClient().signInIntent
    }

    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).result
                ?: return Result.failure(Exception("Google account is null"))
            Result.success(account)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    suspend fun getValidIdToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(
                    Exception("Google OAuth не настроен. Укажите google_web_client_id")
                )
            }

            val signInClient = getSignInClient()
            val currentAccount = getSignedInAccount()
            val account = if (currentAccount?.idToken.isNullOrBlank()) {
                Tasks.await(signInClient.silentSignIn())
            } else {
                currentAccount
            }

            val idToken = account?.idToken
            if (idToken.isNullOrBlank()) {
                Result.failure(
                    Exception(
                        "Не удалось получить Google ID token. Проверьте WEB client ID в Google Console."
                    )
                )
            } else {
                Result.success(idToken)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Tasks.await(getSignInClient().signOut())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
