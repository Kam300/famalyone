package com.example.familyone.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.familyone.R
import com.example.familyone.data.FamilyMember
import com.example.familyone.data.FamilyRole
import com.example.familyone.databinding.ActivityFamilyTreeBinding
import com.example.familyone.databinding.ItemTreeMemberBinding
import com.example.familyone.databinding.ItemClassicTreeMemberBinding
import com.example.familyone.databinding.ItemPrintTreeMemberBinding
import com.example.familyone.utils.toLocalizedString
import com.example.familyone.utils.TreeTemplate
import com.example.familyone.utils.TreeTemplateManager
import com.example.familyone.utils.toast
import com.example.familyone.viewmodel.FamilyViewModel

class FamilyTreeActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityFamilyTreeBinding
    private lateinit var viewModel: FamilyViewModel
    private var currentTemplate: TreeTemplate = TreeTemplate.MODERN
    private var cachedMembers: List<FamilyMember> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFamilyTreeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = ViewModelProvider(this)[FamilyViewModel::class.java]
        
        // Загружаем сохраненный шаблон
        currentTemplate = TreeTemplateManager.getTemplate(this)
        
        setupClickListeners()
        observeMembers()
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnChangeTemplate.setOnClickListener {
            showTemplateDialog()
        }
    }
    
    private fun showTemplateDialog() {
        val templates = arrayOf(
            getString(R.string.modern_template),
            getString(R.string.classic_template),
            getString(R.string.print_template)
        )
        
        val currentSelection = when (currentTemplate) {
            TreeTemplate.MODERN -> 0
            TreeTemplate.CLASSIC -> 1
            TreeTemplate.PRINT_A4 -> 2
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.select_template)
            .setSingleChoiceItems(templates, currentSelection) { dialog, which ->
                val newTemplate = when (which) {
                    0 -> TreeTemplate.MODERN
                    1 -> TreeTemplate.CLASSIC
                    2 -> TreeTemplate.PRINT_A4
                    else -> TreeTemplate.MODERN
                }
                
                if (newTemplate != currentTemplate) {
                    currentTemplate = newTemplate
                    TreeTemplateManager.saveTemplate(this, newTemplate)
                    buildFamilyTree(cachedMembers)
                    toast(getString(R.string.template_changed))
                }
                
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun observeMembers() {
        viewModel.allMembers.observe(this) { members ->
            cachedMembers = members
            if (members.isEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.llTreeContainer.visibility = View.GONE
            } else {
                binding.tvEmptyState.visibility = View.GONE
                binding.llTreeContainer.visibility = View.VISIBLE
                buildFamilyTree(members)
            }
        }
    }
    
    private fun buildFamilyTree(members: List<FamilyMember>) {
        binding.llTreeContainer.removeAllViews()
        
        if (members.isEmpty()) return
        
        // Создаем карту членов семьи по ID для быстрого доступа
        val membersMap = members.associateBy { it.id }
        
        // Находим все пары (по общим детям)
        val couples = findCouples(members)
        
        // Строим древо по поколениям с парами
        buildGenerationWithCouples(
            "Бабушки и Дедушки",
            members.filter { 
                it.role == FamilyRole.GRANDFATHER || it.role == FamilyRole.GRANDMOTHER 
            },
            couples,
            membersMap
        )
        
        buildGenerationWithCouples(
            "Родители",
            members.filter { 
                it.role == FamilyRole.FATHER || it.role == FamilyRole.MOTHER 
            },
            couples,
            membersMap
        )
        
        buildGenerationWithCouples(
            "Дяди и Тёти",
            members.filter { 
                it.role == FamilyRole.UNCLE || it.role == FamilyRole.AUNT 
            },
            couples,
            membersMap
        )
        
        buildGenerationWithCouples(
            "Дети",
            members.filter { 
                it.role == FamilyRole.SON || it.role == FamilyRole.DAUGHTER ||
                it.role == FamilyRole.BROTHER || it.role == FamilyRole.SISTER
            },
            couples,
            membersMap
        )
        
        buildGenerationWithCouples(
            "Племянники",
            members.filter { 
                it.role == FamilyRole.NEPHEW || it.role == FamilyRole.NIECE 
            },
            couples,
            membersMap
        )
        
        buildGenerationWithCouples(
            "Внуки",
            members.filter { 
                it.role == FamilyRole.GRANDSON || it.role == FamilyRole.GRANDDAUGHTER 
            },
            couples,
            membersMap
        )
        
        val other = members.filter { it.role == FamilyRole.OTHER }
        if (other.isNotEmpty()) {
            buildGenerationWithCouples("Другие", other, couples, membersMap)
        }
    }
    
    data class Couple(val person1: FamilyMember, val person2: FamilyMember, val children: List<FamilyMember>)
    
    private fun findCouples(members: List<FamilyMember>): List<Couple> {
        val couples = mutableListOf<Couple>()
        val processed = mutableSetOf<Long>()
        
        // Группируем детей по родителям
        val childrenByParents = members
            .filter { it.fatherId != null && it.motherId != null }
            .groupBy { "${it.fatherId}_${it.motherId}" }
        
        childrenByParents.forEach { (_, children) ->
            val father = members.find { it.id == children.first().fatherId }
            val mother = members.find { it.id == children.first().motherId }
            
            if (father != null && mother != null && 
                father.id !in processed && mother.id !in processed) {
                couples.add(Couple(father, mother, children))
                processed.add(father.id)
                processed.add(mother.id)
            }
        }
        
        return couples
    }
    
    private fun buildGenerationWithCouples(
        title: String,
        generationMembers: List<FamilyMember>,
        couples: List<Couple>,
        membersMap: Map<Long, FamilyMember>
    ) {
        if (generationMembers.isEmpty()) return
        
        // Добавляем индикатор связи если это не первое поколение
        if (binding.llTreeContainer.childCount > 0) {
            addConnectionIndicator()
        }
        
        // Заголовок поколения
        val titleView = LayoutInflater.from(this)
            .inflate(android.R.layout.simple_list_item_1, binding.llTreeContainer, false)
        titleView.findViewById<android.widget.TextView>(android.R.id.text1).apply {
            text = title
            textSize = 22f
            setTextColor(getColor(android.R.color.white))
            setPadding(0, 16, 0, 16)
            gravity = android.view.Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        binding.llTreeContainer.addView(titleView)
        
        // Находим пары в этом поколении
        val generationCouples = couples.filter { couple ->
            couple.person1 in generationMembers || couple.person2 in generationMembers
        }
        
        // Найдем одиночных членов (без пары)
        val membersInCouples = generationCouples.flatMap { listOf(it.person1, it.person2) }.toSet()
        val singleMembers = generationMembers.filter { it !in membersInCouples }
        
        // Создаем контейнер для этого поколения
        val generationContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Добавляем пары
        if (generationCouples.isNotEmpty()) {
            val couplesScrollView = android.widget.HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isHorizontalScrollBarEnabled = false
            }
            
            val couplesLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding(16, 0, 16, 0)
            }
            
            generationCouples.forEach { couple ->
                couplesLayout.addView(createCoupleView(couple))
            }
            
            couplesScrollView.addView(couplesLayout)
            generationContainer.addView(couplesScrollView)
        }
        
        // Добавляем одиночных членов
        if (singleMembers.isNotEmpty()) {
            val singlesScrollView = android.widget.HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isHorizontalScrollBarEnabled = false
            }
            
            val singlesLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding(16, 0, 16, 24)
            }
            
            singleMembers.forEach { member ->
                singlesLayout.addView(createMemberCard(member))
            }
            
            singlesScrollView.addView(singlesLayout)
            generationContainer.addView(singlesScrollView)
        }
        
        binding.llTreeContainer.addView(generationContainer)
    }
    
    private fun createCoupleView(couple: Couple): View {
        // Для печатного шаблона используем улучшенную визуализацию
        if (currentTemplate == TreeTemplate.PRINT_A4) {
            return createPrintCoupleView(couple)
        }
        
        val coupleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 0, 8, 24)
        }
        
        // Контейнер для пары (горизонтально)
        val pairLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        
        // Первый член пары
        pairLayout.addView(createMemberCard(couple.person1))
        
        // Линия связи между парой
        val lineWidth = if (currentTemplate == TreeTemplate.CLASSIC) 30 else 40
        val connectionLine = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(lineWidth, 4).apply {
                setMargins(8, 0, 8, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            setBackgroundColor(getColor(android.R.color.white))
            alpha = if (currentTemplate == TreeTemplate.CLASSIC) 0.9f else 0.8f
        }
        pairLayout.addView(connectionLine)
        
        // Второй член пары
        pairLayout.addView(createMemberCard(couple.person2))
        
        coupleContainer.addView(pairLayout)
        
        // Если у пары есть дети, показываем стрелку вниз
        if (couple.children.isNotEmpty()) {
            val arrowDown = android.widget.TextView(this).apply {
                text = "▼"
                textSize = 20f
                setTextColor(getColor(android.R.color.white))
                alpha = 0.7f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 0)
                }
            }
            coupleContainer.addView(arrowDown)
            
            // Показываем детей под парой
            val childrenLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
            }
            
            couple.children.take(3).forEach { child ->
                childrenLayout.addView(createSmallMemberCard(child))
            }
            
            if (couple.children.size > 3) {
                val moreText = android.widget.TextView(this).apply {
                    text = "+${couple.children.size - 3}"
                    textSize = 14f
                    setTextColor(getColor(android.R.color.white))
                    setPadding(8, 8, 8, 8)
                }
                childrenLayout.addView(moreText)
            }
            
            coupleContainer.addView(childrenLayout)
        }
        
        return coupleContainer
    }
    
    // Печатный шаблон с четкими связями для детей
    private fun createPrintCoupleView(couple: Couple): View {
        val coupleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 0, 8, 24)
        }
        
        // Контейнер для пары (горизонтально)
        val pairLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        
        // Первый член пары
        pairLayout.addView(createMemberCard(couple.person1))
        
        // Линия связи между парой (более толстая для печати)
        val connectionLine = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(25, 3).apply {
                setMargins(4, 0, 4, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            setBackgroundColor(getColor(android.R.color.white))
            alpha = 1.0f
        }
        pairLayout.addView(connectionLine)
        
        // Второй член пары
        pairLayout.addView(createMemberCard(couple.person2))
        
        coupleContainer.addView(pairLayout)
        
        // Если у пары есть дети
        if (couple.children.isNotEmpty()) {
            // Вертикальная линия от центра пары
            val verticalLine = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(3, 30).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    setMargins(0, 8, 0, 0)
                }
                setBackgroundColor(getColor(android.R.color.white))
                alpha = 1.0f
            }
            coupleContainer.addView(verticalLine)
            
            // Горизонтальная линия над детьми
            val childrenCount = couple.children.size
            val horizontalLineWidth = (childrenCount * 100) + ((childrenCount - 1) * 8)
            val horizontalLine = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(horizontalLineWidth, 3).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    setMargins(0, 0, 0, 0)
                }
                setBackgroundColor(getColor(android.R.color.white))
                alpha = 1.0f
            }
            coupleContainer.addView(horizontalLine)
            
            // Контейнер для детей с индивидуальными линиями
            val childrenWithLinesLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
            }
            
            couple.children.forEach { child ->
                // Контейнер для каждого ребенка с линией
                val childContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(4, 0, 4, 0)
                    }
                }
                
                // Вертикальная линия к ребенку
                val childLine = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(3, 20)
                    setBackgroundColor(getColor(android.R.color.white))
                    alpha = 1.0f
                }
                childContainer.addView(childLine)
                
                // Карточка ребенка
                childContainer.addView(createMemberCard(child))
                
                childrenWithLinesLayout.addView(childContainer)
            }
            
            coupleContainer.addView(childrenWithLinesLayout)
        }
        
        return coupleContainer
    }
    
    private fun createSmallMemberCard(member: FamilyMember): View {
        val cardView = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                setMargins(4, 8, 4, 0)
            }
            radius = 12f
            cardElevation = 4f
            setCardBackgroundColor(getColor(R.color.gray_card))
        }
        
        val imageView = com.google.android.material.imageview.ShapeableImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(getColor(R.color.primary_orange))
        }
        
        // Load photo
        if (!member.photoUri.isNullOrEmpty()) {
            val photoPath = member.photoUri!!.replace("file://", "")
            Glide.with(this)
                .load(java.io.File(photoPath))
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .centerCrop()
                .into(imageView)
        } else {
            imageView.setImageResource(R.mipmap.ic_launcher)
        }
        
        cardView.addView(imageView)
        return cardView
    }
    
    
    private fun createMemberCard(member: FamilyMember): View {
        return when (currentTemplate) {
            TreeTemplate.MODERN -> createModernMemberCard(member)
            TreeTemplate.CLASSIC -> createClassicMemberCard(member)
            TreeTemplate.PRINT_A4 -> createPrintMemberCard(member)
        }
    }
    
    private fun createModernMemberCard(member: FamilyMember): View {
        val binding = ItemTreeMemberBinding.inflate(LayoutInflater.from(this))
        
        binding.tvTreeMemberName.text = member.lastName
        binding.tvTreeMemberDetails.text = "${member.firstName}\n${member.patronymic}"
        
        // Load photo
        if (!member.photoUri.isNullOrEmpty()) {
            val photoPath = member.photoUri!!.replace("file://", "")
            Glide.with(this)
                .load(java.io.File(photoPath))
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .centerCrop()
                .into(binding.ivTreeMemberPhoto)
        } else {
            binding.ivTreeMemberPhoto.setImageResource(R.mipmap.ic_launcher)
        }
        
        return binding.root
    }
    
    private fun createClassicMemberCard(member: FamilyMember): View {
        val binding = ItemClassicTreeMemberBinding.inflate(LayoutInflater.from(this))
        
        binding.tvClassicMemberName.text = "${member.firstName}\n${member.lastName}"
        
        // Load photo
        if (!member.photoUri.isNullOrEmpty()) {
            val photoPath = member.photoUri!!.replace("file://", "")
            Glide.with(this)
                .load(java.io.File(photoPath))
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .centerCrop()
                .into(binding.ivClassicMemberPhoto)
        } else {
            binding.ivClassicMemberPhoto.setImageResource(R.mipmap.ic_launcher)
        }
        
        return binding.root
    }
    
    private fun createPrintMemberCard(member: FamilyMember): View {
        val binding = ItemPrintTreeMemberBinding.inflate(LayoutInflater.from(this))
        
        binding.tvPrintMemberName.text = member.firstName
        binding.tvPrintMemberLastName.text = member.lastName
        
        // Load photo
        if (!member.photoUri.isNullOrEmpty()) {
            val photoPath = member.photoUri!!.replace("file://", "")
            Glide.with(this)
                .load(java.io.File(photoPath))
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .centerCrop()
                .into(binding.ivPrintMemberPhoto)
        } else {
            binding.ivPrintMemberPhoto.setImageResource(R.mipmap.ic_launcher)
        }
        
        return binding.root
    }
    
    private fun addConnectionIndicator() {
        // Добавляем визуальный индикатор связи между поколениями
        val connectionView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                4,
                60
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 8, 0, 8)
            }
            setBackgroundColor(getColor(android.R.color.white))
            alpha = 0.5f
        }
        binding.llTreeContainer.addView(connectionView)
        
        // Добавляем стрелку вниз
        val arrowView = android.widget.TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            text = "▼"
            textSize = 20f
            setTextColor(getColor(android.R.color.white))
            alpha = 0.7f
        }
        binding.llTreeContainer.addView(arrowView)
    }
}


