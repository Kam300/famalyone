package com.example.familyone.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.familyone.MainActivity
import com.example.familyone.databinding.ActivityOnboardingBinding
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardingAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViewPager()
        setupClickListeners()
    }
    
    private fun setupViewPager() {
        adapter = OnboardingAdapter()
        binding.viewPager.adapter = adapter
        
        // Связываем TabLayout с ViewPager
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ -> }.attach()
        
        // Слушаем изменения страниц
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateButtons(position)
            }
        })
        
        updateButtons(0)
    }
    
    private fun setupClickListeners() {
        binding.btnNext.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < adapter.itemCount - 1) {
                binding.viewPager.currentItem = currentItem + 1
            } else {
                finishOnboarding()
            }
        }
        
        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }
    }
    
    private fun updateButtons(position: Int) {
        if (position == adapter.itemCount - 1) {
            binding.btnNext.text = "Начать"
            binding.btnSkip.visibility = View.GONE
        } else {
            binding.btnNext.text = "Далее"
            binding.btnSkip.visibility = View.VISIBLE
        }
    }
    
    private fun finishOnboarding() {
        // Сохраняем что onboarding пройден
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_completed", true)
            .apply()
        
        // Переходим на главный экран
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
