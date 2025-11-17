package com.example.familyone.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.familyone.R
import com.example.familyone.data.FamilyMember
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.layout.properties.VerticalAlignment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class PdfPageFormat(val pageSize: PageSize, val displayName: String) {
    A4(PageSize.A4, "A4 (210×297 мм)"),
    A4_LANDSCAPE(PageSize.A4.rotate(), "A4 Альбомная"),
    A3(PageSize.A3, "A3 (297×420 мм)"),
    A3_LANDSCAPE(PageSize.A3.rotate(), "A3 Альбомная"),
    LETTER(PageSize.LETTER, "Letter (216×279 мм)"),
    LETTER_LANDSCAPE(PageSize.LETTER.rotate(), "Letter Альбомная")
}

object PdfExporter {
    
    private val orangeColor = DeviceRgb(255, 153, 51)
    private val purpleColor = DeviceRgb(94, 67, 236)
    private val grayColor = DeviceRgb(158, 158, 158)
    
    fun exportFamilyTree(
        context: Context,
        members: List<FamilyMember>,
        format: PdfPageFormat = PdfPageFormat.A4,
        fileName: String? = null
    ): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(context.getExternalFilesDir(null), fileName ?: "FamilyTree_$timestamp.pdf")
            
            val writer = PdfWriter(FileOutputStream(file))
            val pdfDoc = PdfDocument(writer)
            
            // Добавляем фоновое изображение древа
            addBackgroundImage(context, pdfDoc, format.pageSize)
            
            val document = Document(pdfDoc, format.pageSize)
            
            // Минимальные отступы для максимального использования пространства
            document.setMargins(30f, 30f, 30f, 30f)
            
            // Заголовок "СЕМЬЯ" вверху (как на картинке)
            addBeautifulTitle(document)
            
            // Группировка по поколениям
            val generations = groupMembersByGeneration(members)
            
            // Компактное размещение на одной странице
            addCompactGenerations(context, document, generations, members, format.pageSize)
            
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun addBackgroundImage(context: Context, pdfDoc: PdfDocument, pageSize: PageSize) {
        try {
            // Загружаем фоновое изображение (пергамент с древом)
            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.scale_1200)
            
            if (bitmap != null) {
                // Масштабируем изображение под размер страницы
                val scaledBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    pageSize.width.toInt(),
                    pageSize.height.toInt(),
                    true
                )
                
                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                
                // Добавляем изображение на первую страницу
                val page = pdfDoc.addNewPage()
                val canvas = PdfCanvas(page)
                canvas.addImageFittedIntoRectangle(
                    ImageDataFactory.create(stream.toByteArray()),
                    com.itextpdf.kernel.geom.Rectangle(0f, 0f, pageSize.width, pageSize.height),
                    false
                )
                
                // Освобождаем ресурсы
                bitmap.recycle()
                scaledBitmap.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Если не удалось загрузить фон, создаем страницу без него
            pdfDoc.addNewPage()
        }
    }
    
    private fun addBeautifulTitle(document: Document) {
        val title = Paragraph("СЕМЬЯ")
            .setFontSize(28f)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(DeviceRgb(101, 67, 33)) // Коричневый цвет для пергамента
            .setMarginBottom(15f)
        
        document.add(title)
    }
    
    private fun addCompactGenerations(
        context: Context,
        document: Document,
        generations: Map<String, List<FamilyMember>>,
        allMembers: List<FamilyMember>,
        pageSize: PageSize
    ) {
        // Определяем сколько места доступно
        val availableHeight = pageSize.height - 120f // Отступы + заголовок
        
        // Группируем поколения для компактного размещения
        generations.forEach { (generationName, members) ->
            if (members.isNotEmpty()) {
                // Компактный заголовок поколения
                val genTitle = Paragraph(generationName)
                    .setFontSize(12f)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(DeviceRgb(139, 69, 19)) // Коричневый
                    .setMarginTop(8f)
                    .setMarginBottom(5f)
                
                document.add(genTitle)
                
                // Находим пары
                val couples = findCouples(members, allMembers)
                val membersInCouples = couples.flatMap { listOf(it.first, it.second) }.toSet()
                val singleMembers = members.filter { it !in membersInCouples }
                
                // Компактно добавляем пары
                couples.forEach { (person1, person2, children) ->
                    addCompactCouple(context, document, person1, person2, children)
                }
                
                // Компактно добавляем одиночных
                if (singleMembers.isNotEmpty()) {
                    addCompactSingleMembers(context, document, singleMembers)
                }
            }
        }
        
        // Дата в самом низу маленьким шрифтом
        val footer = Paragraph("Создано: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}")
            .setFontSize(7f)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(DeviceRgb(100, 100, 100))
            .setMarginTop(10f)
        
        document.add(footer)
    }
    
    private fun addCompactCouple(
        context: Context,
        document: Document,
        person1: FamilyMember,
        person2: FamilyMember,
        children: List<FamilyMember>
    ) {
        // Таблица для пары (2 колонки) - очень компактная
        val coupleTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
            .setWidth(UnitValue.createPercentValue(70f))
            .setHorizontalAlignment(HorizontalAlignment.CENTER)
            .setMarginBottom(3f)
        
        coupleTable.addCell(createCompactMemberCell(context, person1))
        coupleTable.addCell(createCompactMemberCell(context, person2))
        
        document.add(coupleTable)
        
        // Дети если есть (очень компактно)
        if (children.isNotEmpty()) {
            val childrenText = children.joinToString(", ") { 
                "${it.firstName} ${it.lastName} (${it.birthDate})"
            }
            val childrenPara = Paragraph("Дети: $childrenText")
                .setFontSize(7f)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(DeviceRgb(80, 80, 80))
                .setMarginBottom(5f)
            
            document.add(childrenPara)
        }
    }
    
    private fun addCompactSingleMembers(
        context: Context,
        document: Document,
        members: List<FamilyMember>
    ) {
        val columns = minOf(members.size, 4)
        val table = Table(columns)
            .setWidth(UnitValue.createPercentValue(80f))
            .setHorizontalAlignment(HorizontalAlignment.CENTER)
            .setMarginBottom(5f)
        
        members.forEach { member ->
            table.addCell(createCompactMemberCell(context, member))
        }
        
        document.add(table)
    }
    
    private fun createCompactMemberCell(context: Context, member: FamilyMember): Cell {
        val cell = Cell()
            .setPadding(4f)
            .setBorder(Border.NO_BORDER)
            .setTextAlignment(TextAlignment.CENTER)
            .setVerticalAlignment(VerticalAlignment.MIDDLE)
            .setBackgroundColor(DeviceRgb(255, 250, 240), 0.7f) // Полупрозрачный бежевый
        
        // Фото (маленькое)
        if (!member.photoUri.isNullOrEmpty()) {
            val photoPath = member.photoUri.replace("file://", "")
            val photoFile = File(photoPath)
            
            if (photoFile.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(photoPath)
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 40, 40, true)
                    val stream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    
                    val image = Image(ImageDataFactory.create(stream.toByteArray()))
                        .setWidth(30f)
                        .setHeight(30f)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER)
                    
                    cell.add(image)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        // ФИО (компактно)
        val name = Paragraph("${member.firstName} ${member.lastName}")
            .setFontSize(8f)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(DeviceRgb(40, 40, 40))
            .setMarginTop(2f)
        cell.add(name)
        
        // Дата рождения
        val birthDate = Paragraph(member.birthDate)
            .setFontSize(7f)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(DeviceRgb(80, 80, 80))
        cell.add(birthDate)
        
        return cell
    }
    
    
    private fun groupMembersByGeneration(members: List<FamilyMember>): Map<String, List<FamilyMember>> {
        val map = mutableMapOf<String, MutableList<FamilyMember>>()
        
        members.forEach { member ->
            val generation = when (member.role) {
                com.example.familyone.data.FamilyRole.GRANDFATHER,
                com.example.familyone.data.FamilyRole.GRANDMOTHER -> "Бабушки и Дедушки"
                com.example.familyone.data.FamilyRole.FATHER,
                com.example.familyone.data.FamilyRole.MOTHER -> "Родители"
                com.example.familyone.data.FamilyRole.UNCLE,
                com.example.familyone.data.FamilyRole.AUNT -> "Дяди и Тёти"
                com.example.familyone.data.FamilyRole.SON,
                com.example.familyone.data.FamilyRole.DAUGHTER,
                com.example.familyone.data.FamilyRole.BROTHER,
                com.example.familyone.data.FamilyRole.SISTER -> "Дети"
                com.example.familyone.data.FamilyRole.NEPHEW,
                com.example.familyone.data.FamilyRole.NIECE -> "Племянники"
                com.example.familyone.data.FamilyRole.GRANDSON,
                com.example.familyone.data.FamilyRole.GRANDDAUGHTER -> "Внуки"
                else -> "Другие"
            }
            
            map.getOrPut(generation) { mutableListOf() }.add(member)
        }
        
        // Сортировка в правильном порядке
        val order = listOf(
            "Бабушки и Дедушки",
            "Родители",
            "Дяди и Тёти",
            "Дети",
            "Племянники",
            "Внуки",
            "Другие"
        )
        
        return map.entries.sortedBy { order.indexOf(it.key) }.associate { it.toPair() }
    }
    
    
    private fun findCouples(
        members: List<FamilyMember>,
        allMembers: List<FamilyMember>
    ): List<Triple<FamilyMember, FamilyMember, List<FamilyMember>>> {
        val couples = mutableListOf<Triple<FamilyMember, FamilyMember, List<FamilyMember>>>()
        val processed = mutableSetOf<Long>()
        
        val childrenByParents = allMembers
            .filter { it.fatherId != null && it.motherId != null }
            .groupBy { "${it.fatherId}_${it.motherId}" }
        
        childrenByParents.forEach { (_, children) ->
            val father = allMembers.find { it.id == children.first().fatherId }
            val mother = allMembers.find { it.id == children.first().motherId }
            
            if (father != null && mother != null &&
                father.id !in processed && mother.id !in processed &&
                (father in members || mother in members)) {
                couples.add(Triple(father, mother, children))
                processed.add(father.id)
                processed.add(mother.id)
            }
        }
        
        return couples
    }
}

