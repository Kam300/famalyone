package com.example.familyone.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.familyone.R
import com.example.familyone.databinding.ItemOnboardingBinding

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: String
)

class OnboardingAdapter : RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {
    
    private val pages = listOf(
        OnboardingPage(
            title = "Добро пожаловать!",
            description = "FamilyOne - это современное приложение для создания и управления вашим семейным древом",
            icon = "👋"
        ),
        OnboardingPage(
            title = "Создайте семейное древо",
            description = "Добавляйте членов семьи, указывайте родственные связи и стройте красивое генеалогическое древо",
            icon = "🌳"
        ),
        OnboardingPage(
            title = "Храните воспоминания",
            description = "Добавляйте фотографии, даты рождения, свадьбы и другую важную информацию о родственниках",
            icon = "📸"
        ),
        OnboardingPage(
            title = "AI Распознавание лиц",
            description = "Загружайте фото, и нейросеть автоматически определит, кто на них изображён из вашей семьи",
            icon = "🤖"
        ),
        OnboardingPage(
            title = "Оставайтесь на связи",
            description = "Быстро связывайтесь с родственниками через WhatsApp, Telegram или телефон прямо из приложения",
            icon = "💬"
        ),
        OnboardingPage(
            title = "Начните прямо сейчас!",
            description = "Создайте свою первую семейную карточку и начните строить историю вашей семьи",
            icon = "🚀"
        )
    )
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val binding = ItemOnboardingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OnboardingViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        holder.bind(pages[position])
    }
    
    override fun getItemCount() = pages.size
    
    class OnboardingViewHolder(
        private val binding: ItemOnboardingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(page: OnboardingPage) {
            binding.tvIcon.text = page.icon
            binding.tvTitle.text = page.title
            binding.tvDescription.text = page.description
        }
    }
}
