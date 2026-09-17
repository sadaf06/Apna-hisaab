package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import com.example.viewmodel.MonthSummary
import com.example.viewmodel.MonthDetail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CategoryConfig
import com.example.ui.components.*
import com.example.ui.theme.Dimens
import com.example.ui.theme.Palette
import com.example.ui.theme.Shapes
import com.example.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

// Top-level clean data class for type safety & editing state reference
data class HisaabExpense(
    val id: String,
    val description: String,
    val category: String,
    val amount: Double,
    val timestamp: Long,
    val entryId: Int,
    val itemIndex: Int
)

@Composable
fun HisaabScreen(viewModel: ExpenseViewModel) {
    val diaryEntries by viewModel.allDiaryEntries.collectAsStateWithLifecycle()
    val monthlySummaries by viewModel.monthlySummaries.collectAsStateWithLifecycle()
    val monthDetails by viewModel.monthDetails.collectAsStateWithLifecycle()
    val loadingMonthKey by viewModel.loadingMonthKey.collectAsStateWithLifecycle()

    var expandedMonthKey by remember { mutableStateOf<String?>(null) } // key: "year-month"

    LaunchedEffect(Unit) {
        viewModel.loadMonthlySummaries()
    }

    val currentCal = remember { Calendar.getInstance() }
    val currentYear = remember { currentCal.get(Calendar.YEAR) }
    val currentMonth = remember { currentCal.get(Calendar.MONTH) }

    val currentMonthEntries = remember(diaryEntries, currentYear, currentMonth) {
        diaryEntries.filter { entry ->
            val entryCal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            entryCal.get(Calendar.YEAR) == currentYear && entryCal.get(Calendar.MONTH) == currentMonth
        }
    }

    val currentMonthExpenses = remember(currentMonthEntries) {
        currentMonthEntries.flatMap { entry ->
            val parsed = com.example.api.GeminiClient.deserializeExpenses(entry.parsedExpensesJson)
            parsed.mapIndexed { index, expenseItem ->
                HisaabExpense(
                    id = "${entry.id}_${index}",
                    description = expenseItem.item,
                    category = expenseItem.category,
                    amount = expenseItem.amount,
                    timestamp = entry.timestamp,
                    entryId = entry.id,
                    itemIndex = index
                )
            }
        }.sortedByDescending { it.timestamp }
    }

    val oldMonths = remember(monthlySummaries, currentYear, currentMonth) {
        monthlySummaries.filterNot { it.year == currentYear && it.month == currentMonth }
    }
    
    // Flatten entries to get a list of expenses for "Sare Kharche" tab
    val expenses = remember(diaryEntries) {
        diaryEntries.flatMap { entry ->
            val parsed = com.example.api.GeminiClient.deserializeExpenses(entry.parsedExpensesJson)
            parsed.mapIndexed { index, expenseItem ->
                HisaabExpense(
                    id = "${entry.id}_${index}",
                    description = expenseItem.item,
                    category = expenseItem.category,
                    amount = expenseItem.amount,
                    timestamp = entry.timestamp,
                    entryId = entry.id,
                    itemIndex = index
                )
            }
        }.sortedByDescending { it.timestamp }
    }

    var selectedTab by remember { mutableStateOf(0) } // 0 = Kahaniya, 1 = Sare Kharche
    var expenseViewMode by remember { mutableStateOf(0) } // 0 = List (default), 1 = Category
    var expandedEntryId by remember { mutableStateOf<Int?>(null) }
    var entryToDelete by remember { mutableStateOf<Int?>(null) }
    var animatingDeleteId by remember { mutableStateOf<Int?>(null) }
    var editingExpense by remember { mutableStateOf<HisaabExpense?>(null) }
    var editingEntry by remember { mutableStateOf<com.example.data.DiaryEntry?>(null) }
    val hisaabHazeState = remember { HazeState() }
    val context = LocalContext.current
    
    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { 
                Text(
                    text = "Entry Delete Karein? 🗑️", 
                    fontWeight = FontWeight.Bold,
                    color = Palette.TextPrimary
                ) 
            },
            text = { 
                Text(
                    text = "Ye entry hamesha ke liye delete ho jaayegi!",
                    color = Palette.TextSecondary
                ) 
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Danger),
                    onClick = {
                        animatingDeleteId = entryToDelete
                        entryToDelete = null
                    }
                ) {
                    Text("Haan Delete ❌", fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Nahi Rukna ✅", fontWeight = FontWeight.Bold, color = Palette.TextTertiary)
                }
            },
            shape = Shapes.xl,
            containerColor = Palette.BaseTop,
            modifier = Modifier.border(Dimens.hairline, Palette.BorderSoft, Shapes.xl)
        )
    }

    LaunchedEffect(animatingDeleteId) {
        val id = animatingDeleteId
        if (id != null) {
            // Wait for slide-left and fade-out animation to finish (e.g. 300ms)
            kotlinx.coroutines.delay(300)
            viewModel.deleteDiaryEntry(id) { success ->
                if (success) {
                    com.example.utils.PremiumToast.show(context, "Entry delete ho gayi ✅")
                } else {
                    com.example.utils.PremiumToast.show(context, "Delete nahi hua, dobara try karo")
                }
            }
            animatingDeleteId = null
        }
    }

    // Edit Kharcha Glass Dialog
    if (editingExpense != null) {
        val expenseToEdit = editingExpense!!
        var editDescription by remember(expenseToEdit) { mutableStateOf(expenseToEdit.description) }
        var editAmountStr by remember(expenseToEdit) { mutableStateOf(expenseToEdit.amount.toInt().toString()) }
        var editCategory by remember(expenseToEdit) { mutableStateOf(expenseToEdit.category) }
        var isSaving by remember { mutableStateOf(false) }
        var dropdownExpanded by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { editingExpense = null }) {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            val view = androidx.compose.ui.platform.LocalView.current
            var isKeyboardVisible by remember { mutableStateOf(false) }

            DisposableEffect(view) {
                val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    val rect = android.graphics.Rect()
                    view.getWindowVisibleDisplayFrame(rect)
                    val screenHeight = view.rootView.height
                    val keypadHeight = screenHeight - rect.bottom
                    isKeyboardVisible = keypadHeight > screenHeight * 0.15
                }
                view.viewTreeObserver.addOnGlobalLayoutListener(listener)
                onDispose {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                }
            }

            androidx.activity.compose.BackHandler(enabled = isKeyboardVisible) {
                keyboardController?.hide()
                focusManager.clearFocus()
            }

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.lg),
                shape = Shapes.xl,
                contentPadding = PaddingValues(Dimens.xl)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Kharcha Edit Karein ✏️",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Palette.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    // Description text field
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Description", color = Palette.TextSecondary) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Palette.Purple,
                            unfocusedBorderColor = Palette.BorderSoft,
                            focusedContainerColor = Palette.SurfaceInset,
                            unfocusedContainerColor = Palette.SurfaceInset,
                            focusedLabelColor = Palette.Purple,
                            unfocusedLabelColor = Palette.TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Amount text field
                    OutlinedTextField(
                        value = editAmountStr,
                        onValueChange = { editAmountStr = it },
                        label = { Text("Amount (₹)", color = Palette.TextSecondary) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Palette.Purple,
                            unfocusedBorderColor = Palette.BorderSoft,
                            focusedContainerColor = Palette.SurfaceInset,
                            unfocusedContainerColor = Palette.SurfaceInset,
                            focusedLabelColor = Palette.Purple,
                            unfocusedLabelColor = Palette.TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Category Dropdown field
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category", color = Palette.TextSecondary) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Palette.Purple,
                                unfocusedBorderColor = Palette.BorderSoft,
                                focusedContainerColor = Palette.SurfaceInset,
                                unfocusedContainerColor = Palette.SurfaceInset,
                                focusedLabelColor = Palette.Purple,
                                unfocusedLabelColor = Palette.TextSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dropdownExpanded = true },
                            trailingIcon = {
                                IconButton(onClick = { dropdownExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Category",
                                        tint = Palette.TextSecondary
                                    )
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier
                                .background(Palette.BaseTop)
                                .border(Dimens.hairline, Palette.BorderSoft, Shapes.md)
                        ) {
                            com.example.data.CategoryConfig.categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${cat.icon} ${cat.name}",
                                            color = Palette.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        editCategory = cat.name
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.xs))

                    // Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { editingExpense = null }) {
                            Text("Cancel", color = Palette.TextTertiary)
                        }

                        Spacer(modifier = Modifier.width(Dimens.sm))

                        Button(
                            onClick = {
                                val amountDouble = editAmountStr.toDoubleOrNull() ?: 0.0
                                if (editDescription.isNotEmpty() && amountDouble > 0.0) {
                                    isSaving = true
                                    viewModel.updateExpenseItem(
                                        entryId = expenseToEdit.entryId,
                                        itemIndex = expenseToEdit.itemIndex,
                                        newItem = editDescription,
                                        newAmount = amountDouble,
                                        newCategory = editCategory
                                    ) { success ->
                                        isSaving = false
                                        if (success) {
                                            com.example.utils.PremiumToast.show(context, "Kharcha update ho gaya ✅")
                                            editingExpense = null
                                        } else {
                                            com.example.utils.PremiumToast.show(context, "Update nahi hua, dobara try karo")
                                        }
                                    }
                                } else {
                                    com.example.utils.PremiumToast.show(context, "Sahi details bharein!")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Purple),
                            enabled = !isSaving
                        ) {
                            Text("Save", color = Palette.BaseTop, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (editingEntry != null) {
        val entryToEdit = editingEntry!!
        var editStoryText by remember(entryToEdit) { mutableStateOf(entryToEdit.originalText) }
        var editMood by remember(entryToEdit) { mutableStateOf(entryToEdit.mood) }
        var isSavingEntry by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = { editingEntry = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            val view = androidx.compose.ui.platform.LocalView.current
            var isKeyboardVisible by remember { mutableStateOf(false) }

            DisposableEffect(view) {
                val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    val rect = android.graphics.Rect()
                    view.getWindowVisibleDisplayFrame(rect)
                    val screenHeight = view.rootView.height
                    val keypadHeight = screenHeight - rect.bottom
                    isKeyboardVisible = keypadHeight > screenHeight * 0.15
                }
                view.viewTreeObserver.addOnGlobalLayoutListener(listener)
                onDispose {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                }
            }

            androidx.activity.compose.BackHandler(enabled = isKeyboardVisible) {
                keyboardController?.hide()
                focusManager.clearFocus()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)) // Dim full-screen scrim
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { editingEntry = null },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.lg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Prevent click through */ }
                        .localGlassShadow(
                            borderRadius = 28.dp,
                            blurRadius = 24.dp,
                            offsetY = 12.dp,
                            color = Color.Black.copy(alpha = 0.35f)
                        )
                        .clip(RoundedCornerShape(28.dp))
                        .hazeChild(
                            state = hisaabHazeState,
                            shape = RoundedCornerShape(28.dp),
                            style = HazeStyle(
                                blurRadius = 30.dp,
                                tint = Color.White.copy(alpha = 0.10f)
                            )
                        )
                        .background(Color.White.copy(alpha = 0.13f), RoundedCornerShape(28.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
                        .drawBehind {
                            // top specular highlight (white 45% fading down)
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.White.copy(alpha = 0.45f),
                                    20.dp.toPx() / size.height to Color.Transparent
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx())
                            )
                            // faint diagonal sheen (white ~0.18)
                            drawLine(
                                color = Color.White.copy(alpha = 0.18f),
                                start = Offset(0f, size.height * 0.15f),
                                end = Offset(size.width, size.height * 0.45f),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            // soft bottom inner shadow (represented by a very subtle dark gradient at bottom)
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.8f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.08f)
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx())
                            )
                        }
                        .padding(Dimens.xl)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimens.md),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Kahani Edit Karein",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Palette.TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        // Story Text Box
                        OutlinedTextField(
                            value = editStoryText,
                            onValueChange = { editStoryText = it },
                            label = { Text("Apni Kahani", color = Palette.TextSecondary) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Palette.Purple,
                                unfocusedBorderColor = Palette.BorderSoft,
                                focusedContainerColor = Palette.SurfaceInset,
                                unfocusedContainerColor = Palette.SurfaceInset,
                                focusedLabelColor = Palette.Purple,
                                unfocusedLabelColor = Palette.TextSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            maxLines = 5
                        )

                        // Mood selector
                        Text(
                            text = "Mood Select Karein:",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Palette.TextSecondary
                            )
                        )

                        val moods = listOf(
                            "khush" to "😊 Khush",
                            "thaka" to "😴 Thaka",
                            "stressed" to "🤯 Stressed",
                            "sad" to "😢 Sad",
                            "normal" to "😐 Normal"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                        ) {
                            moods.forEach { (moodVal, moodLabel) ->
                                val isSelected = editMood.lowercase().trim() == moodVal || 
                                                 editMood.lowercase().trim().contains(moodVal) || 
                                                 moodVal.contains(editMood.lowercase().trim())
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Palette.Purple.copy(alpha = 0.2f) else Color.Transparent)
                                        .border(
                                            width = Dimens.hairline,
                                            color = if (isSelected) Palette.Purple else Palette.BorderSoft,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { editMood = moodVal }
                                        .padding(vertical = Dimens.sm),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = moodLabel.split(" ").first(),
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimens.xs))

                        if (isSavingEntry) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimens.sm),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = Palette.Purple,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(Dimens.sm))
                                Text(
                                    text = "Soch raha hoon... 🤔",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Palette.Purple,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        } else {
                            // Buttons Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { editingEntry = null }) {
                                    Text("Cancel", color = Palette.TextTertiary)
                                }

                                Spacer(modifier = Modifier.width(Dimens.sm))

                                Button(
                                    onClick = {
                                        if (editStoryText.isNotEmpty()) {
                                            isSavingEntry = true
                                            viewModel.updateDiaryEntryWithAi(
                                                id = entryToEdit.id,
                                                editedText = editStoryText,
                                                selectedMood = editMood
                                            ) { success ->
                                                isSavingEntry = false
                                                if (success) {
                                                    com.example.utils.PremiumToast.show(context, "Kahani update ho gayi ✅")
                                                    editingEntry = null
                                                } else {
                                                    com.example.utils.PremiumToast.show(context, "Bhai, AI parsing ya update mein galti hui ❌")
                                                }
                                            }
                                        } else {
                                            com.example.utils.PremiumToast.show(context, "Kahani likhein!")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Purple)
                                ) {
                                    Text("Save", color = Palette.BaseTop, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .haze(state = hisaabHazeState)
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.lg)
            .animateScreenEntrance(),
        verticalArrangement = Arrangement.spacedBy(Dimens.lg)
    ) {
            // Header "Hisaab"
            Text(
                text = "Hisaab",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Palette.TextPrimary
                )
            )

            // Calculate Personality stats dynamically
            val cal = Calendar.getInstance()
            val expenseDayMap = expenses.groupBy {
                cal.timeInMillis = it.timestamp
                cal.get(Calendar.DAY_OF_WEEK)
            }
            val dayNames = mapOf(
                Calendar.SUNDAY to "Sunday",
                Calendar.MONDAY to "Monday",
                Calendar.TUESDAY to "Tuesday",
                Calendar.WEDNESDAY to "Wednesday",
                Calendar.THURSDAY to "Thursday",
                Calendar.FRIDAY to "Friday",
                Calendar.SATURDAY to "Saturday"
            )
            val mostExpensiveDayEntry = expenseDayMap.mapValues { it.value.sumOf { exp -> exp.amount } }.maxByOrNull { it.value }
            val mostExpensiveDayName = mostExpensiveDayEntry?.let { dayNames[it.key] } ?: "Saturday"

            val expenseCategoryMap = expenses.groupBy { it.category }
            val mostExpensiveCategoryEntry = expenseCategoryMap.mapValues { it.value.sumOf { exp -> exp.amount } }.maxByOrNull { it.value }
            val mostExpensiveCategoryName = mostExpensiveCategoryEntry?.key?.split(" ")?.firstOrNull() ?: "Khana"

            val totalSpent = expenses.sumOf { it.amount }
            
            val personalityTitle: String
            val personalityTag: String
            val personalityColor: Color

            if (expenses.isEmpty()) {
                personalityTitle = "Beginner"
                personalityTag = "Beginner"
                personalityColor = Palette.Purple
            } else if (totalSpent < 1500.0) {
                personalityTitle = "Kanjoos King"
                personalityTag = "Saver"
                personalityColor = Palette.Success
            } else if (totalSpent > 6000.0) {
                personalityTitle = "Dil Khula Dost"
                personalityTag = "Spender"
                personalityColor = Palette.Danger
            } else {
                personalityTitle = "Samajhdaar Sipahi"
                personalityTag = "Balanced"
                personalityColor = Palette.Warning
            }

            // 1. PERSONALITY CARD (GlassCard)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Dimens.lg, vertical = Dimens.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isKing = personalityTitle.contains("King")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
                        ) {
                            Text(
                                text = personalityTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isKing) Palette.Warning else Palette.Purple
                                )
                            )
                        }

                        val badgeColor = when (personalityTag.lowercase()) {
                            "saver" -> Palette.Success
                            "spender" -> Palette.Danger
                            "balanced" -> Palette.Warning
                            else -> Palette.Purple
                        }
                        GlassPill(accent = badgeColor) {
                            Text(
                                text = personalityTag,
                                color = Palette.TextPrimary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📅 $mostExpensiveDayName",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Palette.TextSecondary
                            )
                        )
                        Text(
                            text = "🏷️ $mostExpensiveCategoryName Category",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Palette.TextSecondary
                            )
                        )
                    }
                }
            }

            // 2. TAB TOGGLE (Glass container with brandBrush selection)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Shapes.pill)
                    .background(Palette.SurfaceInset)
                    .border(Dimens.hairline, Palette.BorderSoft, Shapes.pill)
                    .padding(Dimens.xs),
                horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
            ) {
                // Tab 0: Kahaniyan
                val selected0 = selectedTab == 0
                val modifier0 = if (selected0) {
                    Modifier.background(brandBrush)
                } else {
                    Modifier
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(Shapes.pill)
                        .then(modifier0)
                        .clickable { selectedTab = 0 }
                        .padding(vertical = Dimens.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Kahaniyan",
                        color = if (selected0) Palette.TextPrimary else Palette.TextTertiary,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (selected0) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }

                // Tab 1: Kharche
                val selected1 = selectedTab == 1
                val modifier1 = if (selected1) {
                    Modifier.background(brandBrush)
                } else {
                    Modifier
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(Shapes.pill)
                        .then(modifier1)
                        .clickable { selectedTab = 1 }
                        .padding(vertical = Dimens.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Kharche",
                        color = if (selected1) Palette.TextPrimary else Palette.TextTertiary,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (selected1) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }

            // Tab Content Switcher with Crossfade
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(150)) togetherWith
                    fadeOut(animationSpec = tween(150))
                },
                label = "tab_crossfade",
                modifier = Modifier.weight(1f)
            ) { targetTab ->
                if (targetTab == 0) {
                    // Kahaniya (Stories Timeline) View
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.md),
                        contentPadding = PaddingValues(bottom = 140.dp)
                    ) {
                        // Current month section
                        if (currentMonthEntries.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Is Mahine Ki Kahaniyan",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Palette.Purple,
                                    modifier = Modifier.padding(vertical = Dimens.sm)
                                )
                            }
                            itemsIndexed(currentMonthEntries, key = { _, it -> it.id }) { index, entry ->
                                val isExpanded = expandedEntryId == entry.id
                                DiaryEntryCard(
                                    index = index,
                                    entry = entry,
                                    isExpanded = isExpanded,
                                    onExpandToggle = { expandedEntryId = if (isExpanded) null else entry.id },
                                    onEditClick = { editingEntry = entry },
                                    onDeleteClick = { entryToDelete = entry.id },
                                    animatingDeleteId = animatingDeleteId,
                                    showExpensesList = true
                                )
                            }
                        } else {
                            item {
                                GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.md)) {
                                    Text(
                                        text = "Is mahine koi kahani nahi hai.",
                                        color = Palette.TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(Dimens.md)
                                    )
                                }
                            }
                        }

                        // Old months sections
                        if (oldMonths.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Purane Mahine",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Palette.Purple,
                                    modifier = Modifier.padding(top = Dimens.lg, bottom = Dimens.sm)
                                )
                            }
                            itemsIndexed(oldMonths, key = { _, it -> "${it.year}-${it.month}" }) { index, summary ->
                                val key = "${summary.year}-${summary.month}"
                                val isExpanded = expandedMonthKey == key
                                val rotationAngle by animateFloatAsState(
                                    targetValue = if (isExpanded) 180f else 0f,
                                    label = "rotation"
                                )

                                HisaabLiquidGlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Dimens.xs)
                                        .clickable {
                                            if (isExpanded) {
                                                expandedMonthKey = null
                                            } else {
                                                expandedMonthKey = key
                                                viewModel.fetchMonthDetail(summary.year, summary.month)
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = summary.label,
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = Palette.TextPrimary
                                                )
                                                Text(
                                                    text = "Total spent: ₹${summary.totalSpent.toInt()}",
                                                    style = MaterialTheme.typography.labelSmall.copy(color = Palette.TextSecondary)
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Expand old month",
                                                tint = Palette.Purple,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .graphicsLayer { rotationZ = rotationAngle }
                                            )
                                        }

                                        if (isExpanded) {
                                            if (loadingMonthKey == key) {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(Dimens.md),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(color = Palette.Purple, modifier = Modifier.size(24.dp))
                                                }
                                            } else {
                                                val detail = monthDetails[key]
                                                if (detail != null) {
                                                    Column(
                                                        modifier = Modifier.padding(top = Dimens.md),
                                                        verticalArrangement = Arrangement.spacedBy(Dimens.md)
                                                    ) {
                                                        if (detail.entries.isEmpty()) {
                                                            Text(
                                                                text = "Koi kahani nahi hai is mahine.",
                                                                color = Palette.TextTertiary,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                modifier = Modifier.padding(vertical = Dimens.md)
                                                            )
                                                        } else {
                                                            detail.entries.forEachIndexed { idx, entry ->
                                                                var isEntryExpanded by remember { mutableStateOf(false) }
                                                                DiaryEntryCard(
                                                                    index = idx,
                                                                    entry = entry,
                                                                    isExpanded = isEntryExpanded,
                                                                    onExpandToggle = { isEntryExpanded = !isEntryExpanded },
                                                                    onEditClick = { editingEntry = entry },
                                                                    onDeleteClick = { entryToDelete = entry.id },
                                                                    animatingDeleteId = animatingDeleteId,
                                                                    showExpensesList = false
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Saare Kharche Tab (Single GlassCard wrapping a clean list)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 140.dp)
                    ) {
                        // Current month section
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimens.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Is Mahine Ke Kharche",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Palette.Purple,
                                    modifier = Modifier.weight(1f)
                                )

                                // Segmented toggle
                                Row(
                                    modifier = Modifier
                                        .background(Palette.SurfaceInset, RoundedCornerShape(12.dp))
                                        .border(Dimens.hairline, Palette.BorderSoft, RoundedCornerShape(12.dp))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // List mode (0)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (expenseViewMode == 0) Palette.Purple.copy(alpha = 0.2f)
                                                else Color.Transparent
                                            )
                                            .clickable { expenseViewMode = 0 }
                                            .padding(horizontal = Dimens.md, vertical = Dimens.xs),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.List,
                                            contentDescription = "List View",
                                            tint = if (expenseViewMode == 0) Palette.Purple else Palette.TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Category mode (1)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (expenseViewMode == 1) Palette.Purple.copy(alpha = 0.2f)
                                                else Color.Transparent
                                            )
                                            .clickable { expenseViewMode = 1 }
                                            .padding(horizontal = Dimens.md, vertical = Dimens.xs),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "Category View",
                                            tint = if (expenseViewMode == 1) Palette.Purple else Palette.TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (currentMonthExpenses.isNotEmpty()) {
                            item {
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = Dimens.lg, vertical = Dimens.md)
                                ) {
                                    Column {
                                        if (expenseViewMode == 0) {
                                            // Flat list view (List mode)
                                            currentMonthExpenses.forEachIndexed { index, expense ->
                                                ExpenseRow(index = index, expense = expense)
                                                if (index < currentMonthExpenses.lastIndex) {
                                                    HorizontalDivider(
                                                        color = Palette.BorderSoft,
                                                        thickness = Dimens.hairline
                                                    )
                                                }
                                            }
                                        } else {
                                            // Category grouped view (Category mode)
                                            val groupedExpenses = currentMonthExpenses.groupBy { it.category }
                                            val sortedCategories = groupedExpenses.map { (category, list) ->
                                                val total = list.sumOf { it.amount }
                                                Triple(category, total, list)
                                            }.sortedByDescending { it.second }

                                            sortedCategories.forEachIndexed { catIndex, (category, total, expenses) ->
                                                val catInfo = com.example.data.CategoryConfig.getCategoryByName(category)

                                                // Category Header Row
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = Dimens.md),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        // Circular emoji icon bubble (32dp)
                                                        Box(
                                                            modifier = Modifier
                                                                .size(32.dp)
                                                                .background(Palette.SurfaceInset, CircleShape)
                                                                .border(Dimens.hairline, Palette.BorderSoft, CircleShape),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(text = catInfo.icon, fontSize = 16.sp)
                                                        }
                                                        Spacer(modifier = Modifier.width(Dimens.sm))
                                                        Text(
                                                            text = catInfo.name,
                                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = Palette.TextPrimary
                                                        )
                                                    }
                                                    Text(
                                                        text = "₹${total.toInt()}",
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = Palette.Purple
                                                    )
                                                }

                                                // Category Expenses List
                                                expenses.forEachIndexed { expIndex, expense ->
                                                    CategoryExpenseRow(index = expIndex, expense = expense)
                                                    if (expIndex < expenses.lastIndex) {
                                                        HorizontalDivider(
                                                            color = Palette.BorderSoft.copy(alpha = 0.5f),
                                                            thickness = Dimens.hairline,
                                                            modifier = Modifier.padding(start = Dimens.xs)
                                                        )
                                                    }
                                                }

                                                // Divider between categories
                                                if (catIndex < sortedCategories.lastIndex) {
                                                    HorizontalDivider(
                                                        color = Palette.BorderSoft,
                                                        thickness = 1.dp,
                                                        modifier = Modifier.padding(vertical = Dimens.sm)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.md)) {
                                    Text(
                                        text = "Is mahine koi kharcha nahi hai.",
                                        color = Palette.TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(Dimens.md)
                                    )
                                }
                            }
                        }

                        // Old months sections
                        if (oldMonths.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Purane Mahine Ke Kharche",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Palette.Purple,
                                    modifier = Modifier.padding(top = Dimens.lg, bottom = Dimens.sm)
                                )
                            }
                            itemsIndexed(oldMonths, key = { _, it -> "${it.year}-${it.month}" }) { index, summary ->
                                val key = "${summary.year}-${summary.month}"
                                val isExpanded = expandedMonthKey == key
                                val rotationAngle by animateFloatAsState(
                                    targetValue = if (isExpanded) 180f else 0f,
                                    label = "rotation"
                                )

                                HisaabLiquidGlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Dimens.xs)
                                        .clickable {
                                            if (isExpanded) {
                                                expandedMonthKey = null
                                            } else {
                                                expandedMonthKey = key
                                                viewModel.fetchMonthDetail(summary.year, summary.month)
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = summary.label,
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = Palette.TextPrimary
                                                )
                                                Text(
                                                    text = "Total spent: ₹${summary.totalSpent.toInt()}",
                                                    style = MaterialTheme.typography.labelSmall.copy(color = Palette.TextSecondary)
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Expand old month",
                                                tint = Palette.Purple,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .graphicsLayer { rotationZ = rotationAngle }
                                            )
                                        }

                                        if (isExpanded) {
                                            if (loadingMonthKey == key) {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(Dimens.md),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(color = Palette.Purple, modifier = Modifier.size(24.dp))
                                                }
                                            } else {
                                                val detail = monthDetails[key]
                                                if (detail != null) {
                                                    val oldMonthExpenses = remember(detail.entries) {
                                                        detail.entries.flatMap { entry ->
                                                            val parsed = com.example.api.GeminiClient.deserializeExpenses(entry.parsedExpensesJson)
                                                            parsed.mapIndexed { idx, expenseItem ->
                                                                HisaabExpense(
                                                                    id = "${entry.id}_${idx}",
                                                                    description = expenseItem.item,
                                                                    category = expenseItem.category,
                                                                    amount = expenseItem.amount,
                                                                    timestamp = entry.timestamp,
                                                                    entryId = entry.id,
                                                                    itemIndex = idx
                                                                )
                                                            }
                                                        }.sortedByDescending { it.timestamp }
                                                    }

                                                    if (oldMonthExpenses.isEmpty()) {
                                                        Text(
                                                            text = "Koi kharcha nahi hai is mahine.",
                                                            color = Palette.TextTertiary,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            modifier = Modifier.padding(vertical = Dimens.md)
                                                        )
                                                    } else {
                                                        Column(
                                                            modifier = Modifier.padding(top = Dimens.md),
                                                            verticalArrangement = Arrangement.spacedBy(Dimens.xs)
                                                        ) {
                                                            GlassCard(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                contentPadding = PaddingValues(horizontal = Dimens.lg, vertical = Dimens.md)
                                                            ) {
                                                                Column {
                                                                    if (expenseViewMode == 0) {
                                                                        // Flat list view
                                                                        oldMonthExpenses.forEachIndexed { idx, expense ->
                                                                            ExpenseRow(index = idx, expense = expense)
                                                                            if (idx < oldMonthExpenses.lastIndex) {
                                                                                HorizontalDivider(
                                                                                    color = Palette.BorderSoft,
                                                                                    thickness = Dimens.hairline
                                                                                )
                                                                            }
                                                                        }
                                                                    } else {
                                                                        // Category grouped view
                                                                        val groupedExpenses = oldMonthExpenses.groupBy { it.category }
                                                                        val sortedCategories = groupedExpenses.map { (category, list) ->
                                                                            val total = list.sumOf { it.amount }
                                                                            Triple(category, total, list)
                                                                        }.sortedByDescending { it.second }

                                                                        sortedCategories.forEachIndexed { catIndex, (category, total, expenses) ->
                                                                            val catInfo = com.example.data.CategoryConfig.getCategoryByName(category)

                                                                            // Category Header Row
                                                                            Row(
                                                                                modifier = Modifier
                                                                                    .fillMaxWidth()
                                                                                    .padding(vertical = Dimens.md),
                                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                                verticalAlignment = Alignment.CenterVertically
                                                                            ) {
                                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                                    // Circular emoji icon bubble (32dp)
                                                                                    Box(
                                                                                        modifier = Modifier
                                                                                            .size(32.dp)
                                                                                            .background(Palette.SurfaceInset, CircleShape)
                                                                                            .border(Dimens.hairline, Palette.BorderSoft, CircleShape),
                                                                                        contentAlignment = Alignment.Center
                                                                                    ) {
                                                                                        Text(text = catInfo.icon, fontSize = 16.sp)
                                                                                    }
                                                                                    Spacer(modifier = Modifier.width(Dimens.sm))
                                                                                    Text(
                                                                                        text = catInfo.name,
                                                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                                                        color = Palette.TextPrimary
                                                                                    )
                                                                                }
                                                                                Text(
                                                                                    text = "₹${total.toInt()}",
                                                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                                                    color = Palette.Purple
                                                                                )
                                                                            }

                                                                            // Category Expenses List
                                                                            expenses.forEachIndexed { expIndex, expense ->
                                                                                CategoryExpenseRow(index = expIndex, expense = expense)
                                                                                if (expIndex < expenses.lastIndex) {
                                                                                    HorizontalDivider(
                                                                                        color = Palette.BorderSoft.copy(alpha = 0.5f),
                                                                                        thickness = Dimens.hairline,
                                                                                        modifier = Modifier.padding(start = Dimens.xs)
                                                                                    )
                                                                                }
                                                                            }

                                                                            // Divider between categories
                                                                            if (catIndex < sortedCategories.lastIndex) {
                                                                                HorizontalDivider(
                                                                                    color = Palette.BorderSoft,
                                                                                    thickness = 1.dp,
                                                                                    modifier = Modifier.padding(vertical = Dimens.sm)
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

private fun Modifier.localGlassShadow(
    borderRadius: androidx.compose.ui.unit.Dp = 28.dp,
    blurRadius: androidx.compose.ui.unit.Dp = 24.dp,
    offsetY: androidx.compose.ui.unit.Dp = 12.dp,
    color: Color = Color.Black.copy(alpha = 0.35f)
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = android.graphics.Color.TRANSPARENT
        frameworkPaint.setShadowLayer(
            blurRadius.toPx(),
            0f,
            offsetY.toPx(),
            color.toArgb()
        )
        canvas.drawRoundRect(
            left = 0f,
            top = 0f,
            right = size.width,
            bottom = size.height,
            radiusX = borderRadius.toPx(),
            radiusY = borderRadius.toPx(),
            paint = paint
        )
    }
}

fun getHindiRelativeDate(timestamp: Long): String {
    val smsTime = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }
    val dayBeforeYesterday = Calendar.getInstance().apply { add(Calendar.DATE, -2) }

    return when {
        smsTime.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                smsTime.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Aaj"
        smsTime.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                smsTime.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Kal"
        smsTime.get(Calendar.YEAR) == dayBeforeYesterday.get(Calendar.YEAR) &&
                smsTime.get(Calendar.DAY_OF_YEAR) == dayBeforeYesterday.get(Calendar.DAY_OF_YEAR) -> "Parso"
        else -> {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

@Composable
fun DiaryEntryCard(
    index: Int,
    entry: com.example.data.DiaryEntry,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    animatingDeleteId: Int?,
    showExpensesList: Boolean = true
) {
    val parsedExp = remember(entry.parsedExpensesJson) {
        com.example.api.GeminiClient.deserializeExpenses(entry.parsedExpensesJson)
    }
    val totalExpenseOfEntry = remember(parsedExp) { parsedExp.sumOf { it.amount } }

    val (moodText, moodColor, moodEmoji) = when (entry.mood.lowercase().trim()) {
        "khush" -> Triple("Khush", Palette.Success, "😊")
        "thaka" -> Triple("Thaka", Palette.mood("thaka"), "😴")
        "stressed" -> Triple("Stressed", Palette.Warning, "🤯")
        "sad" -> Triple("Sad", Palette.mood("sad"), "😢")
        else -> Triple("Normal", Palette.Purple, "😐")
    }

    val smsTime = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }
    val isToday = smsTime.get(Calendar.YEAR) == today.get(Calendar.YEAR) && smsTime.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    val isYesterday = smsTime.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) && smsTime.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)

    val (dateText, dateBgColor) = when {
        isToday -> "Aaj" to Palette.Purple
        isYesterday -> "Kal" to Palette.mood("thaka")
        else -> {
            val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
            sdf.format(Date(entry.timestamp)) to Palette.mood("sad")
        }
    }

    val isDeleting = entry.id == animatingDeleteId
    val deleteTranslateX by animateFloatAsState(
        targetValue = if (isDeleting) -800f else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "delete_x"
    )
    val deleteAlpha by animateFloatAsState(
        targetValue = if (isDeleting) 0f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "delete_alpha"
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateCardStagger(index)
            .graphicsLayer {
                translationX = deleteTranslateX
                alpha = deleteAlpha
            }
            .clickable { onExpandToggle() },
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(moodColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(Dimens.lg)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassPill(accent = dateBgColor) {
                        Text(
                            text = dateText,
                            color = Palette.TextPrimary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    GlassPill(accent = moodColor) {
                        Text(
                            text = "$moodEmoji $moodText",
                            color = Palette.TextPrimary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "“ ",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = moodColor.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(end = Dimens.xs)
                    )
                    Text(
                        text = entry.originalText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp,
                            color = Palette.TextSecondary
                        ),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Total kharch: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextSecondary
                        )
                        val amountColor = when {
                            totalExpenseOfEntry < 100 -> Palette.Success
                            totalExpenseOfEntry <= 500 -> Palette.Warning
                            else -> Palette.Danger
                        }
                        Text(
                            text = "₹${totalExpenseOfEntry.toInt()}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = amountColor
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.md)
                    ) {
                        Text(
                            text = if (isExpanded) "See less" else "See full",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Palette.Purple,
                            modifier = Modifier.clickable { onExpandToggle() }
                        )

                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Story",
                                tint = Palette.Purple,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Palette.Danger,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.md),
                        verticalArrangement = Arrangement.spacedBy(Dimens.md)
                    ) {
                        HorizontalDivider(color = Palette.BorderSoft, thickness = Dimens.hairline)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(Shapes.md)
                                .background(Palette.Success.copy(alpha = 0.10f), Shapes.md)
                                .border(Dimens.hairline, Palette.Success.copy(alpha = 0.25f), Shapes.md)
                                .padding(Dimens.md)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("💡", fontSize = 18.sp)
                                Text(
                                    text = entry.aiInsight,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Palette.Success,
                                        lineHeight = 20.sp
                                    )
                                )
                            }
                        }

                        if (showExpensesList && parsedExp.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                                Text(
                                    text = "PARSED ITEMS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Palette.Purple
                                    )
                                )
                                parsedExp.forEach { exp ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = Dimens.xs),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Palette.PurpleDeep)
                                            )
                                            val catInfo = com.example.data.CategoryConfig.getCategoryByName(exp.category)
                                            Text(
                                                text = "${exp.item} (${catInfo.icon} ${catInfo.name})",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Palette.TextPrimary
                                                )
                                            )
                                        }
                                        Text(
                                            text = "₹${exp.amount.toInt()}",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                color = Palette.TextPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseRow(
    index: Int,
    expense: HisaabExpense
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.md)
            .animateCardStagger(index),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            val catInfo = com.example.data.CategoryConfig.getCategoryByName(expense.category, expense.description)
            val circleBg = Palette.SurfaceInset

            // Left circular icon bubble (40dp)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(circleBg, CircleShape)
                    .border(Dimens.hairline, Palette.BorderSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = catInfo.icon, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(Dimens.md))
            Column {
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Palette.TextPrimary
                )
                val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(expense.timestamp))
                Text(
                    text = "${expense.category} · $dateStr",
                    style = MaterialTheme.typography.labelSmall.copy(color = Palette.TextSecondary)
                )
            }
        }

        val amountColor = when {
            expense.amount <= 100.0 -> Palette.Success
            expense.amount <= 500.0 -> Palette.Warning
            else -> Palette.Danger
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
        ) {
            Text(
                text = "₹${expense.amount.toInt()}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = amountColor
            )
        }
    }
}

@Composable
fun CategoryExpenseRow(
    index: Int,
    expense: HisaabExpense
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.sm)
            .animateCardStagger(index),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = expense.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Palette.TextPrimary
            )
            val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(expense.timestamp))
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall.copy(color = Palette.TextSecondary)
            )
        }

        val amountColor = when {
            expense.amount <= 100.0 -> Palette.Success
            expense.amount <= 500.0 -> Palette.Warning
            else -> Palette.Danger
        }

        Text(
            text = "₹${expense.amount.toInt()}",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = amountColor
        )
    }
}

@Composable
fun HisaabLiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = Shapes.xl,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Palette.SurfaceInset)
            .border(1.dp, Palette.BorderSoft, shape)
            .drawBehind {
                // Diagonal sheen top-left
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.35f, size.height * 0.35f)
                    )
                )
                // Bright top inner highlight (white ~30%)
                val strokeWidth = 1.5.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.30f),
                    start = Offset(24.dp.toPx(), strokeWidth / 2),
                    end = Offset(size.width - 24.dp.toPx(), strokeWidth / 2),
                    strokeWidth = strokeWidth
                )
                // Soft bottom inner shadow/glow
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f)),
                        startY = size.height * 0.75f,
                        endY = size.height
                    )
                )
            }
            .padding(Dimens.lg)
    ) {
        Column {
            content()
        }
    }
}
