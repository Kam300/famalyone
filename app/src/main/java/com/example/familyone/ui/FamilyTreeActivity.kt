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
        
        // Создаем карту членов семьи по ID
        val membersMap = members.associateBy { it.id }
        
        // Находим корневых членов (без родителей)
        val rootMembers = members.filter { it.fatherId == null && it.motherId == null }
        
        // Находим пары
        val couples = findCouples(members)
        
        // Если есть корневые члены, строим от них
        if (rootMembers.isNotEmpty()) {
            buildFromRoots(rootMembers, couples, membersMap, members)
        } else {
            // Если нет корневых, показываем всех членов как отдельные ветки
            val scrollView = createHorizontalScrollLayout()
            val innerLayout = scrollView.getChildAt(0) as LinearLayout
            
            couples.forEach { couple ->
                val familyBranch = buildFamilyBranch(couple, members, mutableSetOf(), 0)
                innerLayout.addView(familyBranch)
                
                if (couple != couples.last()) {
                    innerLayout.addView(createBranchSeparator())
                }
            }
            
            // Добавляем одиночных членов
            val membersInCouples = couples.flatMap { listOf(it.person1, it.person2) }.toSet()
            val singles = members.filter { it !in membersInCouples }
            singles.forEach { member ->
                innerLayout.addView(createMemberCard(member))
            }
            
            binding.llTreeContainer.addView(scrollView)
        }
    }
    
    private fun buildFromRoots(
        roots: List<FamilyMember>,
        couples: List<Couple>,
        membersMap: Map<Long, FamilyMember>,
        allMembers: List<FamilyMember>
    ) {
        val processed = mutableSetOf<Long>()
        
        // Группируем корневых по парам
        val rootCouples = couples.filter { it.person1 in roots || it.person2 in roots }
        val rootSingles = roots.filter { member ->
            rootCouples.none { it.person1.id == member.id || it.person2.id == member.id }
        }
        
        // Создаём горизонтальный скролл для всех семейных веток
        val scrollView = createHorizontalScrollLayout()
        val innerLayout = scrollView.getChildAt(0) as LinearLayout
        
        // Добавляем каждую пару как отдельную вертикальную ветку
        rootCouples.forEach { couple ->
            val familyBranch = buildFamilyBranch(couple, allMembers, processed, 0)
            innerLayout.addView(familyBranch)
            
            // Добавляем разделитель между ветками
            if (couple != rootCouples.last()) {
                innerLayout.addView(createBranchSeparator())
            }
        }
        
        // Добавляем одиночных
        rootSingles.forEach { member ->
            val singleBranch = buildSingleBranch(member, allMembers, processed, 0)
            innerLayout.addView(singleBranch)
            processed.add(member.id)
        }
        
        binding.llTreeContainer.addView(scrollView)
    }
    
    // Строит вертикальную ветку семьи: пара -> их дети -> внуки и т.д.
    private fun buildFamilyBranch(
        couple: Couple,
        allMembers: List<FamilyMember>,
        processed: MutableSet<Long>,
        level: Int
    ): View {
        val branchContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(16, 0, 16, 0)
        }
        
        // Контейнер для пары с меткой поколения
        val pairWithLabelContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        
        // Метка поколения слева
        val generationLabel = android.widget.TextView(this).apply {
            text = getGenerationLabel(level)
            textSize = 11f
            setTextColor(getColor(android.R.color.white))
            alpha = 0.6f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 12, 0)
            }
            rotation = -90f
            setPadding(4, 4, 4, 4)
        }
        pairWithLabelContainer.addView(generationLabel)
        
        // Добавляем пару
        val pairLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        
        pairLayout.addView(createMemberCard(couple.person1))
        
        val connectionLine = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 4).apply {
                setMargins(8, 0, 8, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            setBackgroundColor(getColor(android.R.color.white))
            alpha = 0.8f
        }
        pairLayout.addView(connectionLine)
        
        pairLayout.addView(createMemberCard(couple.person2))
        pairWithLabelContainer.addView(pairLayout)
        
        branchContainer.addView(pairWithLabelContainer)
        
        processed.add(couple.person1.id)
        processed.add(couple.person2.id)
        
        // Находим детей этой пары
        val children = allMembers.filter { 
            it.id !in processed && (
                (it.fatherId == couple.person1.id && it.motherId == couple.person2.id) ||
                (it.fatherId == couple.person2.id && it.motherId == couple.person1.id)
            )
        }
        
        if (children.isNotEmpty()) {
            // Стрелка вниз
            val arrow = android.widget.TextView(this).apply {
                text = "↓"
                textSize = 24f
                setTextColor(getColor(android.R.color.white))
                alpha = 0.7f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }
            branchContainer.addView(arrow)
            
            // Контейнер для детей с меткой поколения
            val childrenWithLabelContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            
            // Метка поколения для детей
            val childGenLabel = android.widget.TextView(this).apply {
                text = getGenerationLabel(level + 1)
                textSize = 11f
                setTextColor(getColor(android.R.color.white))
                alpha = 0.6f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 12, 0)
                }
                rotation = -90f
                setPadding(4, 4, 4, 4)
            }
            childrenWithLabelContainer.addView(childGenLabel)
            
            // Контейнер для детей (горизонтально)
            val childrenLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
            }
            
            // Находим пары среди детей
            val allCouples = findCouples(allMembers)
            val childCouples = allCouples.filter { couple ->
                couple.person1 in children || couple.person2 in children
            }
            
            // Проверяем, какие пары имеют своих детей (проверяем по всем членам, не только необработанным)
            val childCouplesWithChildren = childCouples.filter { couple ->
                // Проверяем есть ли у этой пары дети вообще (не важно обработаны или нет)
                allMembers.any { member ->
                    member.id != couple.person1.id && 
                    member.id != couple.person2.id &&
                    ((member.fatherId == couple.person1.id && member.motherId == couple.person2.id) ||
                     (member.fatherId == couple.person2.id && member.motherId == couple.person1.id))
                }
            }
            
            // Пары БЕЗ детей показываем здесь
            val childCouplesWithoutChildren = childCouples.filter { it !in childCouplesWithChildren }
            
            // Отмечаем всех детей, которые в парах
            val childrenInCouples = childCouples.flatMap { listOf(it.person1, it.person2) }.toSet()
            
            // Показываем только пары БЕЗ детей
            childCouplesWithoutChildren.forEach { couple ->
                val pairLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                }
                
                pairLayout.addView(createMemberCard(couple.person1))
                
                val line = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(30, 3).apply {
                        setMargins(4, 0, 4, 0)
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    setBackgroundColor(getColor(android.R.color.white))
                    alpha = 0.7f
                }
                pairLayout.addView(line)
                
                pairLayout.addView(createMemberCard(couple.person2))
                childrenLayout.addView(pairLayout)
                
                processed.add(couple.person1.id)
                processed.add(couple.person2.id)
            }
            
            // Показываем одиночных детей (не в парах вообще)
            val singleChildren = children.filter { it !in childrenInCouples }
            
            // Если только 1 ребёнок - показываем по центру
            if (singleChildren.size == 1 && childCouplesWithoutChildren.isEmpty()) {
                val singleChild = singleChildren.first()
                val centeredLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                centeredLayout.addView(createMemberCard(singleChild))
                childrenLayout.addView(centeredLayout)
                processed.add(singleChild.id)
            } else {
                // Несколько детей - показываем горизонтально
                singleChildren.forEach { child ->
                    childrenLayout.addView(createMemberCard(child))
                    processed.add(child.id)
                }
            }
            
            // Добавляем контейнер только если есть что показать
            if (childCouplesWithoutChildren.isNotEmpty() || singleChildren.isNotEmpty()) {
                childrenWithLabelContainer.addView(childrenLayout)
                branchContainer.addView(childrenWithLabelContainer)
            }
            // Рекурсивно показываем пары с детьми
            if (childCouplesWithChildren.isNotEmpty()) {
                val nextLevelArrow = android.widget.TextView(this).apply {
                    text = "↓"
                    textSize = 24f
                    setTextColor(getColor(android.R.color.white))
                    alpha = 0.7f
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 8)
                    }
                }
                branchContainer.addView(nextLevelArrow)
                
                // Контейнер с меткой для следующего поколения
                val nextGenContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                
                // Метка поколения
                val nextGenLabel = android.widget.TextView(this).apply {
                    text = getGenerationLabel(level + 1)
                    textSize = 11f
                    setTextColor(getColor(android.R.color.white))
                    alpha = 0.6f
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 12, 0)
                    }
                    rotation = -90f
                    setPadding(4, 4, 4, 4)
                }
                nextGenContainer.addView(nextGenLabel)
                
                val nextGenLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                }
                
                childCouplesWithChildren.forEach { childCouple ->
                    nextGenLayout.addView(buildFamilyBranch(childCouple, allMembers, processed, level + 1))
                }
                
                nextGenContainer.addView(nextGenLayout)
                branchContainer.addView(nextGenContainer)
            }
        }
        
        return branchContainer
    }
    
    // Строит ветку для одиночного члена семьи
    private fun buildSingleBranch(
        member: FamilyMember,
        allMembers: List<FamilyMember>,
        processed: MutableSet<Long>,
        level: Int
    ): View {
        val branchContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(16, 0, 16, 0)
        }
        
        // Контейнер с меткой поколения
        val memberWithLabelContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        
        // Метка поколения
        val generationLabel = android.widget.TextView(this).apply {
            text = getGenerationLabel(level)
            textSize = 11f
            setTextColor(getColor(android.R.color.white))
            alpha = 0.6f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 12, 0)
            }
            rotation = -90f
            setPadding(4, 4, 4, 4)
        }
        memberWithLabelContainer.addView(generationLabel)
        memberWithLabelContainer.addView(createMemberCard(member))
        
        branchContainer.addView(memberWithLabelContainer)
        processed.add(member.id)
        
        // Находим детей
        val children = allMembers.filter { 
            it.id !in processed && (it.fatherId == member.id || it.motherId == member.id)
        }
        
        if (children.isNotEmpty()) {
            // Стрелка вниз
            val arrow = android.widget.TextView(this).apply {
                text = "↓"
                textSize = 24f
                setTextColor(getColor(android.R.color.white))
                alpha = 0.7f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }
            branchContainer.addView(arrow)
            
            // Контейнер для детей с меткой
            val childrenWithLabelContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            
            // Метка поколения для детей
            val childGenLabel = android.widget.TextView(this).apply {
                text = getGenerationLabel(level + 1)
                textSize = 11f
                setTextColor(getColor(android.R.color.white))
                alpha = 0.6f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 12, 0)
                }
                rotation = -90f
                setPadding(4, 4, 4, 4)
            }
            childrenWithLabelContainer.addView(childGenLabel)
            
            val childrenLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
            }
            
            // Если только 1 ребёнок - центрируем
            if (children.size == 1) {
                val centeredLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                centeredLayout.addView(createMemberCard(children.first()))
                childrenLayout.addView(centeredLayout)
                processed.add(children.first().id)
            } else {
                // Несколько детей
                children.forEach { child ->
                    childrenLayout.addView(createMemberCard(child))
                    processed.add(child.id)
                }
            }
            
            childrenWithLabelContainer.addView(childrenLayout)
            branchContainer.addView(childrenWithLabelContainer)
        }
        
        return branchContainer
    }
    
    private fun createBranchSeparator(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                setMargins(24, 0, 24, 0)
            }
            setBackgroundColor(getColor(android.R.color.white))
            alpha = 0.3f
        }
    }
    
    private fun getGenerationLabel(level: Int): String {
        return when(level) {
            0 -> "1-е"
            1 -> "2-е"
            2 -> "3-е"
            3 -> "4-е"
            4 -> "5-е"
            else -> "${level + 1}-е"
        }
    }
    

    

    
    private fun createHorizontalScrollLayout(): android.widget.HorizontalScrollView {
        val scrollView = android.widget.HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
        }
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(16, 0, 16, 24)
        }
        
        scrollView.addView(layout)
        return scrollView
    }
    
    data class Couple(val person1: FamilyMember, val person2: FamilyMember, val children: List<FamilyMember>)
    
    private fun findCouples(members: List<FamilyMember>): List<Couple> {
        val couples = mutableListOf<Couple>()
        val processed = mutableSetOf<Long>()
        
        // 1. Находим пары по общим детям
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
        
        // 2. Находим пары по дате свадьбы (weddingDate)
        val marriedMembers = members.filter { 
            it.weddingDate != null && it.id !in processed 
        }
        
        marriedMembers.forEach { person ->
            if (person.id in processed) return@forEach
            
            // Ищем партнёра с той же датой свадьбы
            val partner = members.find { other ->
                other.id != person.id &&
                other.id !in processed &&
                other.weddingDate == person.weddingDate &&
                // Проверяем что это разные полы
                other.gender != person.gender
            }
            
            if (partner != null) {
                // Находим их общих детей
                val commonChildren = members.filter { child ->
                    (child.fatherId == person.id && child.motherId == partner.id) ||
                    (child.fatherId == partner.id && child.motherId == person.id)
                }
                
                couples.add(Couple(person, partner, commonChildren))
                processed.add(person.id)
                processed.add(partner.id)
            }
        }
        
        return couples
    }
    

    
    private fun createCoupleViewWithChildren(couple: Couple, allMembers: List<FamilyMember>): View {
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
        val connectionLine = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 4).apply {
                setMargins(8, 0, 8, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            setBackgroundColor(getColor(android.R.color.white))
            alpha = 0.8f
        }
        pairLayout.addView(connectionLine)
        
        // Второй член пары
        pairLayout.addView(createMemberCard(couple.person2))
        
        coupleContainer.addView(pairLayout)
        
        // Подсчитываем детей этой пары
        val children = allMembers.filter { 
            (it.fatherId == couple.person1.id && it.motherId == couple.person2.id) ||
            (it.fatherId == couple.person2.id && it.motherId == couple.person1.id)
        }
        
        // Если есть дети, показываем индикатор
        if (children.isNotEmpty()) {
            val childrenIndicator = android.widget.TextView(this).apply {
                text = "↓ ${children.size} ${if (children.size == 1) "ребёнок" else if (children.size < 5) "ребёнка" else "детей"}"
                textSize = 12f
                setTextColor(getColor(android.R.color.white))
                alpha = 0.7f
                gravity = android.view.Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.ITALIC)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 0)
                }
            }
            coupleContainer.addView(childrenIndicator)
        }
        
        return coupleContainer
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
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .centerCrop()
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ic_person_placeholder)
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
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .centerCrop()
                .into(binding.ivTreeMemberPhoto)
        } else {
            binding.ivTreeMemberPhoto.setImageResource(R.drawable.ic_person_placeholder)
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
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .centerCrop()
                .into(binding.ivClassicMemberPhoto)
        } else {
            binding.ivClassicMemberPhoto.setImageResource(R.drawable.ic_person_placeholder)
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
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .centerCrop()
                .into(binding.ivPrintMemberPhoto)
        } else {
            binding.ivPrintMemberPhoto.setImageResource(R.drawable.ic_person_placeholder)
        }
        
        return binding.root
    }
    

    

}


