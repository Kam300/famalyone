package com.example.familyone.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.familyone.R
import com.example.familyone.databinding.ItemOnboardingBinding
import com.example.familyone.databinding.ItemOnboardingPrivacyBinding

data class OnboardingPage(
    val title: String,
    val description: String,
    val iconRes: Int,
    val isPrivacyPage: Boolean = false
)

class OnboardingAdapter(
    private val onPrivacyConsentChanged: ((Boolean) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val VIEW_TYPE_NORMAL = 0
        private const val VIEW_TYPE_PRIVACY = 1
    }
    
    private val pages = listOf(
        OnboardingPage(
            title = "Добро пожаловать!",
            description = "FamilyOne - это современное приложение для создания и управления вашим семейным древом",
            iconRes = R.drawable.ic_family_tree
        ),
        OnboardingPage(
            title = "Создайте семейное древо",
            description = "Добавляйте членов семьи, указывайте родственные связи и стройте красивое генеалогическое древо",
            iconRes = R.drawable.ic_group
        ),
        OnboardingPage(
            title = "Храните воспоминания",
            description = "Добавляйте фотографии, даты рождения, свадьбы и другую важную информацию о родственниках",
            iconRes = R.drawable.ic_photo_library
        ),
        OnboardingPage(
            title = "AI Распознавание лиц",
            description = "Загружайте фото, и нейросеть автоматически определит, кто на них изображён из вашей семьи",
            iconRes = R.drawable.ic_auto_fix
        ),
        OnboardingPage(
            title = "Оставайтесь на связи",
            description = "Быстро связывайтесь с родственниками через WhatsApp, Telegram или телефон прямо из приложения",
            iconRes = R.drawable.ic_phone
        ),
        OnboardingPage(
            title = "Политика конфиденциальности",
            description = "",
            iconRes = R.drawable.ic_info,
            isPrivacyPage = true
        )
    )
    
    override fun getItemViewType(position: Int): Int {
        return if (pages[position].isPrivacyPage) VIEW_TYPE_PRIVACY else VIEW_TYPE_NORMAL
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_PRIVACY -> {
                val binding = ItemOnboardingPrivacyBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                PrivacyViewHolder(binding, onPrivacyConsentChanged)
            }
            else -> {
                val binding = ItemOnboardingBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                OnboardingViewHolder(binding)
            }
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is OnboardingViewHolder -> holder.bind(pages[position])
            is PrivacyViewHolder -> holder.bind()
        }
    }
    
    override fun getItemCount() = pages.size
    
    class OnboardingViewHolder(
        private val binding: ItemOnboardingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(page: OnboardingPage) {
            binding.ivIcon.setImageResource(page.iconRes)
            binding.tvTitle.text = page.title
            binding.tvDescription.text = page.description
        }
    }
    
    class PrivacyViewHolder(
        private val binding: ItemOnboardingPrivacyBinding,
        private val onConsentChanged: ((Boolean) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind() {
            binding.cbPrivacyConsent.setOnCheckedChangeListener { _, isChecked ->
                onConsentChanged?.invoke(isChecked)
            }
        }
    }
}
