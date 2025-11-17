# 🚀 План Новых Функций

## ✅ Что Уже Добавлено

### 1. Анимации Переходов
- ✅ slide_in_left.xml
- ✅ slide_in_right.xml
- ✅ slide_out_left.xml
- ✅ slide_out_right.xml
- ✅ scale_in.xml
- ✅ fade_in.xml
- ✅ fade_out.xml
- ✅ Анимации применены в MainActivity

### 2. Поиск (Частично)
- ✅ UI поиска добавлен в activity_member_list.xml
- ⏳ Нужно: реализовать логику фильтрации в MemberListActivity.kt

## 📋 Что Нужно Доделать

### 2. Поиск - Логика (MemberListActivity.kt)
```kotlin
// Добавить в setupClickListeners():
binding.etSearch.addTextChangedListener { text ->
    filterMembers(text.toString())
}

private fun filterMembers(query: String) {
    val filtered = allMembers.filter {
        it.firstName.contains(query, ignoreCase = true) ||
        it.lastName.contains(query, ignoreCase = true) ||
        it.role.toString().contains(query, ignoreCase = true)
    }
    adapter.submitList(filtered)
}
```

### 3. Splash Screen
Создать файлы:
- `res/drawable/splash_background.xml`
- `res/values/themes.xml` - добавить SplashTheme
- Обновить AndroidManifest.xml

### 4. Дополнительная Информация
Обновить FamilyMember.kt:
```kotlin
data class FamilyMember(
    // Существующие поля...
    val weddingDate: String? = null,
    val anniversaryDate: String? = null,
    val importantDates: List<ImportantDate>? = null
)

data class ImportantDate(
    val name: String,
    val date: String
)
```

### 5. Уведомления о Днях Рождения
Создать:
- `BirthdayNotificationWorker.kt`
- `NotificationHelper.kt`
- Добавить WorkManager dependency

### 6. Поделиться Древом
Добавить в FamilyTreeActivity:
```kotlin
private fun shareTree() {
    // Создать screenshot древа
    // Поделиться через Intent.ACTION_SEND
}
```

## 📦 Необходимые Зависимости

```gradle
// WorkManager для уведомлений
implementation "androidx.work:work-runtime-ktx:2.9.0"

// Для создания скриншотов
// Уже есть в проекте
```

## 🎯 Приоритеты

1. **Высокий**: Поиск (логика), Splash Screen
2. **Средний**: Уведомления, Поделиться
3. **Низкий**: Дополнительная информация

---

**Продолжить реализацию?** 🚀
