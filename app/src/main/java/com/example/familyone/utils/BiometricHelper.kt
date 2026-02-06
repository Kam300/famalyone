package com.example.familyone.utils

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Helper class for biometric authentication
 */
object BiometricHelper {
    
    private const val PREFS_NAME = "biometric_prefs"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    
    /**
     * Check if device supports biometric authentication
     * @return BiometricManager.BIOMETRIC_SUCCESS if available, error code otherwise
     */
    fun canAuthenticate(context: Context): Int {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
    }
    
    /**
     * Check if biometric is available and can be used
     */
    fun isBiometricAvailable(context: Context): Boolean {
        return canAuthenticate(context) == BiometricManager.BIOMETRIC_SUCCESS
    }
    
    /**
     * Get human-readable error message for biometric status
     */
    fun getStatusMessage(context: Context): String {
        return when (canAuthenticate(context)) {
            BiometricManager.BIOMETRIC_SUCCESS -> "Биометрия доступна"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Устройство не поддерживает биометрию"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Биометрический датчик недоступен"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Настройте отпечаток или Face ID в настройках устройства"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Требуется обновление безопасности"
            else -> "Биометрия недоступна"
        }
    }
    
    /**
     * Check if biometric lock is enabled by user
     */
    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }
    
    /**
     * Set biometric lock enabled/disabled
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }
    
    /**
     * Show biometric authentication prompt
     */
    fun showPrompt(
        activity: FragmentActivity,
        title: String = "Аутентификация",
        subtitle: String = "Используйте отпечаток или Face ID",
        negativeButtonText: String = "Отмена",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
            
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onCancel()
                    else -> onError(errString.toString())
                }
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Called when fingerprint is not recognized, user can try again
            }
        }
        
        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
}
