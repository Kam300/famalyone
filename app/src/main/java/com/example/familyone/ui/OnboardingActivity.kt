package com.example.familyone.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.familyone.MainActivity
import com.example.familyone.R
import com.example.familyone.databinding.ActivityOnboardingBinding
import com.example.familyone.utils.BiometricHelper
import com.example.familyone.utils.toast

class OnboardingActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardingAdapter
    private val dots = mutableListOf<ImageView>()
    private var privacyConsented = false
    private var biometricEnabled = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViewPager()
        setupClickListeners()
    }
    
    private fun setupViewPager() {
        adapter = OnboardingAdapter(
            onPrivacyConsentChanged = { isConsented ->
                privacyConsented = isConsented
                updateButtonState()
            },
            onBiometricEnabledChanged = { isEnabled ->
                biometricEnabled = isEnabled
            }
        )
        binding.viewPager.adapter = adapter
        
        // Создаём точки
        setupDots(adapter.itemCount)
        updateDots(0)
        
        // Слушаем изменения страниц
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDots(position)
                updateButtons(position)
            }
        })
        
        updateButtons(0)
    }
    
    private fun setupDots(count: Int) {
        binding.dotsLayout.removeAllViews()
        dots.clear()
        
        for (i in 0 until count) {
            val dot = ImageView(this)
            dot.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.dot_inactive))
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            dot.layoutParams = params
            
            binding.dotsLayout.addView(dot)
            dots.add(dot)
        }
    }
    
    private fun updateDots(position: Int) {
        for (i in dots.indices) {
            dots[i].setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    if (i == position) R.drawable.dot_active else R.drawable.dot_inactive
                )
            )
        }
    }
    
    private fun setupClickListeners() {
        binding.btnNext.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < adapter.itemCount - 1) {
                binding.viewPager.currentItem = currentItem + 1
            } else {
                if (privacyConsented) {
                    finishOnboarding()
                } else {
                    toast("Пожалуйста, примите политику конфиденциальности")
                }
            }
        }
        
        binding.btnSkip.setOnClickListener {
            // Переходим к последней странице с политикой
            binding.viewPager.currentItem = adapter.itemCount - 1
        }
    }
    
    private fun updateButtons(position: Int) {
        if (position == adapter.itemCount - 1) {
            binding.btnNext.text = "Принять и начать"
            binding.btnSkip.visibility = View.GONE
            updateButtonState()
        } else {
            binding.btnNext.text = "Далее"
            binding.btnNext.isEnabled = true
            binding.btnNext.alpha = 1f
            binding.btnSkip.visibility = View.VISIBLE
        }
    }
    
    private fun updateButtonState() {
        val isLastPage = binding.viewPager.currentItem == adapter.itemCount - 1
        if (isLastPage) {
            binding.btnNext.isEnabled = privacyConsented
            binding.btnNext.alpha = if (privacyConsented) 1f else 0.5f
        }
    }
    
    private fun finishOnboarding() {
        // Сохраняем что onboarding пройден и согласие получено
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_completed", true)
            .putBoolean("privacy_consented", true)
            .apply()
        
        // Сохраняем настройку биометрии
        if (biometricEnabled) {
            BiometricHelper.setEnabled(this, true)
        }
        
        // Переходим на главный экран
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
