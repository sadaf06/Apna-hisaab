package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.List
import com.example.Screen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.blur
import com.example.utils.IncomeVisibilityManager
import com.example.ui.theme.Palette
import com.example.ui.theme.Dimens
import com.example.ui.theme.Shapes
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

// ---- Refined Glass palette — thin aliases onto the single source of truth (Palette). ----
private val GMint = Palette.Teal
private val GMintDeep = Palette.TealDeep
private val GViolet = Palette.Purple
private val GCoral = Palette.Danger
private val GText = Palette.TextPrimary
private val GText2 = Palette.TextSecondary
private val GInk = Palette.OnAccent

@Composable
fun LiquidGlassCard(
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
            .padding(Dimens.xxl)
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = Shapes.xl
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (isSelected) GMint.copy(alpha = 0.22f)
                else Palette.SurfaceInset
            )
            .border(
                width = 1.dp,
                color = if (isSelected) GMint.copy(alpha = 0.60f) else Palette.BorderSoft,
                shape = shape
            )
            .clickable { onClick() }
            .drawBehind {
                // Subtle sheen
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.4f, size.height * 0.8f)
                    )
                )
            }
            .padding(horizontal = Dimens.lg, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun AajScreen(viewModel: ExpenseViewModel, navController: androidx.navigation.NavController? = null) {
    val context = LocalContext.current
    var isPrivacyLocked by rememberSaveable { mutableStateOf(true) }
    var showGlassPinSheet by remember { mutableStateOf(false) }

    LaunchedEffect(showGlassPinSheet) {
        viewModel.isBottomBarVisible.value = !showGlassPinSheet
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.isBottomBarVisible.value = true
        }
    }

    var amount by rememberSaveable { mutableStateOf(viewModel.draftAmount.value) }
    var description by rememberSaveable { mutableStateOf(viewModel.draftDescription.value) }
    var category by rememberSaveable { mutableStateOf(viewModel.draftCategory.value) }

    // AI input states & parsed results
    var storyInput by rememberSaveable { mutableStateOf(viewModel.draftStoryInput.value) }
    var isLoadingByAi by remember { mutableStateOf(false) }
    var aiErrorMessage by remember { mutableStateOf<String?>(null) }
    
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(amount) {
        viewModel.updateDraftAmount(amount)
    }
    LaunchedEffect(description) {
        viewModel.updateDraftDescription(description)
    }
    LaunchedEffect(category) {
        viewModel.updateDraftCategory(category)
    }
    LaunchedEffect(storyInput) {
        viewModel.updateDraftStoryInput(storyInput)
    }

    var showSuccess by remember { mutableStateOf(false) }
    var currentToastMessage by remember { mutableStateOf("") }
    var manualErrorMessage by remember { mutableStateOf<String?>(null) }
    
    var isListeningSpeech by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val recognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra("android.speech.extra.ALSO_RECOGNIZE_SPEECH", "en-IN")
        }
    }

    val startSpeechToText = {
        speechError = null
        isListeningSpeech = true
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Kuch suna nahi, dobara bolo! 🎤"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> "Kuch error aaya, firse try karein!"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission do Settings mein 🙏"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice ke liye internet chahiye! 📶"
                    else -> "Kuch listener problem hui: $error"
                }
                speechError = message
                isListeningSpeech = false
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    storyInput = matches[0]
                    speechError = null
                } else {
                    speechError = "Kuch suna nahi, dobara bolo! 🎤"
                }
                isListeningSpeech = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    storyInput = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(listener)
        try {
            speechRecognizer.startListening(recognizerIntent)
        } catch (e: Exception) {
            speechError = "Speech recognition start nahi ho paaya!"
            isListeningSpeech = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechToText()
        } else {
            speechError = "Mic permission do Settings mein 🙏"
        }
    }

    val toggleVoiceListening = {
        if (isListeningSpeech) {
            try {
                speechRecognizer.stopListening()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isListeningSpeech = false
        } else {
            val audioPermission = Manifest.permission.RECORD_AUDIO
            if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
                startSpeechToText()
            } else {
                permissionLauncher.launch(audioPermission)
            }
        }
    }

    val placeholders = remember {
        listOf(
            "Chai pe kitna gaya aaj?",
            "Din kaisa gaya, kuch likho?",
            "Kya khaya aaj, batao?",
            "Paisa gaya toh hisaab do!"
        ).shuffled()
    }
    val placeholderText = remember { placeholders.first() }

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

    androidx.activity.compose.BackHandler(enabled = showGlassPinSheet && !isKeyboardVisible) {
        showGlassPinSheet = false
    }

    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            kotlinx.coroutines.delay(3000)
            showSuccess = false
        }
    }
    val predefinedCategories = remember { com.example.data.CategoryConfig.categories }

    val expenses by viewModel.allExpenses.collectAsStateWithLifecycle()
    val diaryEntries by viewModel.allDiaryEntries.collectAsStateWithLifecycle()
    val currentMoodState by viewModel.currentMood.collectAsStateWithLifecycle()
    val monthlyBudget by viewModel.monthlyBudget.collectAsStateWithLifecycle()
    val khataParties by viewModel.khataParties.collectAsStateWithLifecycle()

    val streakCount = remember(diaryEntries) {
        if (diaryEntries.isEmpty()) {
            0
        } else {
            val cal = Calendar.getInstance()
            fun getDayId(timestamp: Long): Int {
                cal.timeInMillis = timestamp
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH)
                val d = cal.get(Calendar.DAY_OF_MONTH)
                return y * 10000 + m * 100 + d
            }
            val entryDays = diaryEntries.map { getDayId(it.timestamp) }.toSet()
            val todayId = getDayId(System.currentTimeMillis())
            val tempCal = Calendar.getInstance().apply { add(Calendar.DATE, -1) }
            val yesterdayId = getDayId(tempCal.timeInMillis)
            
            var currentCheckCal = Calendar.getInstance()
            if (!entryDays.contains(todayId)) {
                if (entryDays.contains(yesterdayId)) {
                    currentCheckCal.add(Calendar.DATE, -1)
                }
            }
            
            if (!entryDays.contains(todayId) && !entryDays.contains(yesterdayId)) {
                0
            } else {
                var streak = 0
                while (true) {
                    val checkId = getDayId(currentCheckCal.timeInMillis)
                    if (entryDays.contains(checkId)) {
                        streak++
                        currentCheckCal.add(Calendar.DATE, -1)
                    } else {
                        break
                    }
                }
                streak
            }
        }
    }
    
    // Sort and get today's expenses
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startOfDay = calendar.timeInMillis
    
    val todayExpenses = expenses.filter { it.timestamp >= startOfDay }
    val todayTotal = todayExpenses.sumOf { it.amount }
    
    val parsingState by viewModel.parsingState.collectAsStateWithLifecycle()
    val aiParsingError by viewModel.aiParsingError.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.aiActionEvents.collect { event ->
            when (event) {
                is com.example.viewmodel.ExpenseViewModel.AiActionEvent.ShowToast -> {
                    com.example.utils.PremiumToast.show(context, event.message)
                }
                is com.example.viewmodel.ExpenseViewModel.AiActionEvent.NavigateToSettings -> {
                    navController?.navigate("settings")
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(parsingState) {
        if (parsingState == com.example.viewmodel.ExpenseViewModel.ParsingState.SUCCESS_AUTO_SAVED) {
            storyInput = ""
            showSuccess = true
            viewModel.resetParsingState()
        }
    }

    LaunchedEffect(aiParsingError) {
        aiParsingError?.let { error ->
            com.example.utils.PremiumToast.show(context, error, longDuration = true)
            viewModel.clearAiParsingError()
        }
    }

    val userNameState by viewModel.userName.collectAsStateWithLifecycle(initialValue = "")
    val currentFirebaseUser = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser }
    val userEmail = currentFirebaseUser?.email ?: "S.i.siddiqui06@gmail.com"
    val displayName = remember(userNameState, userEmail) {
        if (userNameState.isNotEmpty()) {
            userNameState
        } else if (userEmail.contains("siddiqui", ignoreCase = true)) {
            "Siddiqui Saab"
        } else {
            userEmail.substringBefore("@")
                .split(".", "_", "-")
                .joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() } }
        }
    }

    val dateStr = remember {
        SimpleDateFormat("EEEE, d MMMM", Locale("en", "IN")).format(Date())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    var aiGreeting by remember { mutableStateOf("Aaj ka hisaab dekhein!") }
    
    LaunchedEffect(todayTotal, monthlyBudget, displayName) {
        val result = com.example.api.GeminiClient.generateGreeting(todayTotal, monthlyBudget, displayName)
        if (result.isNotBlank()) {
            aiGreeting = result
        }
    }

    val motivationalLine = when {
        todayTotal < 200 -> "Bahut sahi! Aaj toh bachat ho rahi hai."
        todayTotal <= 500 -> "Theek-thaak kharcha hai, control mein hai."
        else -> "Thoda haath roko bhai, kharcha badh raha hai!"
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Confetti burst animation overlay triggered on successful entries
        ConfettiBurst(active = showSuccess)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .animateScreenEntrance()
                .verticalScroll(scrollState)
                .padding(horizontal = Dimens.xxl, vertical = Dimens.xl),
            verticalArrangement = Arrangement.spacedBy(Dimens.xl)
        ) {
            // Spacer for TopBar
            Spacer(modifier = Modifier.height(Dimens.sm))

            // Redesigned Top Header (Greeting + Profile/Streak Row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateCardEntrance(15),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = aiGreeting,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Palette.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = Palette.TextSecondary
                        )
                    )
                }

                if (streakCount > 0) {
                    Row(
                        modifier = Modifier
                            .clip(Shapes.sm)
                            .background(Palette.Danger.copy(alpha = 0.15f))
                            .border(
                                1.dp,
                                Palette.Danger.copy(alpha = 0.3f),
                                Shapes.sm
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🔥", fontSize = 12.sp)
                        Text(
                            text = "${streakCount} din streak",
                            color = Palette.Danger,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Big hero glass card: label "Aaj ka Kharcha", huge bold amount, progress bar
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateCardEntrance(30)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Aaj ka Kharcha",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Palette.TextSecondary
                            )
                        )
                        IconButton(
                            onClick = {
                                if (isPrivacyLocked) {
                                    showGlassPinSheet = true
                                } else {
                                    isPrivacyLocked = true
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text(
                                text = if (isPrivacyLocked) "🔒" else "🔓",
                                fontSize = 14.sp
                            )
                        }
                    }

                    val isOverBudget = monthlyBudget > 0 && todayTotal > (monthlyBudget / 30) * 1.5
                    val badgeBgColor = if (isOverBudget) Palette.Danger.copy(alpha = 0.2f) else Palette.Teal.copy(alpha = 0.2f)
                    val badgeTextColor = if (isOverBudget) Palette.Danger else Palette.Teal
                    val badgeLabel = if (isOverBudget) "Limit se zyada" else "Budget ke andar"

                    Box(
                        modifier = Modifier
                            .clip(Shapes.sm)
                            .background(badgeBgColor)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = badgeTextColor
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedAmountText(
                    targetAmount = todayTotal,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 44.sp,
                        letterSpacing = (-1.5).sp,
                        color = Palette.TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Shapes.md)
                        .clickable {
                            if (isPrivacyLocked) {
                                showGlassPinSheet = true
                            }
                        }
                ) {
                    val blurRadius = if (isPrivacyLocked) 16.dp else 0.dp
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .blur(blurRadius)
                    ) {
                        // Progress percentage & bar
                        val progressPercent = if (monthlyBudget > 0) {
                            ((todayTotal / (monthlyBudget / 30.0)) * 100).toInt().coerceIn(0, 100)
                        } else {
                            68 // Fallback to 68% as requested if budget is not set
                        }
                        val progressFraction = progressPercent / 100f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Budget: $progressPercent%",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Palette.TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            val budgetText = if (isPrivacyLocked) {
                                "₹••••"
                            } else {
                                val formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN"))
                                "₹" + formatter.format(monthlyBudget.toInt())
                            }
                            Text(
                                text = "Mahina: $budgetText",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Palette.TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressFraction)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Palette.Teal, Palette.Purple)
                                        )
                                    )
                            )
                        }
                    }

                    if (isPrivacyLocked) {
                        Column(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Transparent),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(Shapes.sm)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(1.dp, Color.White.copy(alpha = 0.20f), Shapes.sm)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🔒", fontSize = 16.sp)
                                Text(
                                    text = "Tap to unlock",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.TextPrimary
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = motivationalLine,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.Teal // Mint accent
                        )
                    )
                }
            }

            // Glass input pill card for the AI add
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateCardEntrance(45)
            ) {
                Text(
                    text = "Likho ya bolo",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.TextPrimary
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Input Field + Mic Button nested inside a glass input pill shape
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = storyInput,
                        onValueChange = { storyInput = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Palette.TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp),
                        enabled = !isLoadingByAi,
                        decorationBox = { innerTextField ->
                            if (storyInput.isEmpty()) {
                                Text(
                                    text = placeholderText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Palette.TextSecondary.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                    
                    val reducedMotion = com.example.ui.components.rememberReducedMotion()
                    val infiniteTransitionScale = rememberInfiniteTransition()
                    val pulseScale by infiniteTransitionScale.animateFloat(
                        initialValue = 1f,
                        targetValue = if (isListeningSpeech && !reducedMotion) 1.15f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .scale(if (isListeningSpeech) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(
                                if (isListeningSpeech) Palette.Danger // Coral warning red
                                else Color.White.copy(alpha = 0.1f)
                            )
                            .clickable { toggleVoiceListening() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isListeningSpeech) "⏹️" else "🎤",
                            fontSize = 16.sp,
                            color = if (isListeningSpeech) Color.White else Palette.Teal
                        )
                    }
                }

                aiErrorMessage?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        color = Palette.Danger,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp, start = 8.dp)
                    )
                }

                speechError?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        color = Palette.Danger,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp, start = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Mood Selector
                Text(
                    text = "Mood?",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.TextSecondary
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val moods = listOf(
                        Triple("khush", "😊", "Happy"),
                        Triple("normal", "😐", "Normal"),
                        Triple("sad", "😒", "Sad"),
                        Triple("thaka", "😴", "Tired"),
                        Triple("stressed", "🤯", "Stressed")
                    )
                    
                    moods.forEach { (moodKey, emoji, label) ->
                        val isSelected = currentMoodState == moodKey
                        val moodColor = when(moodKey) {
                            "khush" -> Palette.Teal
                            "normal" -> Palette.Teal
                            "sad" -> Palette.TextSecondary
                            "thaka" -> Palette.Purple
                            "stressed" -> Palette.Danger
                            else -> Palette.Teal
                        }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .scale(if (isSelected) 1.08f else 1.0f)
                                .clip(Shapes.md)
                                .background(
                                    if (isSelected) moodColor.copy(alpha = 0.15f) 
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) moodColor else Color.White.copy(alpha = 0.1f),
                                    shape = Shapes.md
                                )
                                .clickable { viewModel.updateMood(moodKey) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emoji, 
                                fontSize = 24.sp,
                                modifier = Modifier.graphicsLayer {
                                    alpha = if (isSelected) 1.0f else 0.4f
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                
                val isParsing = parsingState == com.example.viewmodel.ExpenseViewModel.ParsingState.PARSING
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(Shapes.xl)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Palette.Teal, Palette.TealDeep)
                            )
                        )
                        .clickable(enabled = !isParsing) {
                            if (storyInput.isBlank()) {
                                com.example.utils.PremiumToast.show(context, "Pehle kuch likho yaar!")
                            } else {
                                viewModel.startParsingEntry(storyInput, currentMoodState)
                            }
                        }
                        .animateButtonPress(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isParsing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Palette.OnAccent,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Analyse ho raha hai...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.OnAccent
                                )
                            )
                        }
                    } else {
                        Text(
                            text = "Hisaab/Action",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Palette.OnAccent
                            )
                        )
                    }
                }
            }

            // Row of glass quick-add pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateCardEntrance(60)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickAdds = listOf(
                    "Chai",
                    "Khana",
                    "Safar",
                    "Masti"
                )
                
                quickAdds.forEach { name ->
                    LiquidGlassPill(
                        isSelected = storyInput.contains(name, ignoreCase = true),
                        onClick = {
                            storyInput = "$name pe kharch kiye."
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.TextPrimary
                                )
                            )
                        }
                    }
                }
            }

            // Quick Entry Section (Manual fallback)
            AnimatedVisibility(
                visible = parsingState == com.example.viewmodel.ExpenseViewModel.ParsingState.FAILED_MANUAL_FALLBACK_REQUIRED,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "AI abhi thak gayi hai! Khud se entry add karein?", 
                        maxLines = 2,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold, 
                            color = Palette.Danger
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateCardEntrance()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Amount Input Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(Shapes.md)
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), Shapes.md)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "₹", 
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black, 
                                        color = Palette.Teal
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                BasicTextField(
                                    value = amount,
                                    onValueChange = { amount = it },
                                    textStyle = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black, 
                                        color = Palette.TextPrimary
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    decorationBox = { innerTextField ->
                                        if (amount.isEmpty()) {
                                            Text(
                                                "Rakam (Amount)", 
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.Bold, 
                                                    color = Palette.TextSecondary.copy(alpha = 0.4f)
                                                )
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Description Input Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(Shapes.md)
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), Shapes.md)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = description,
                                    onValueChange = { description = it },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = Palette.TextPrimary, 
                                        fontWeight = FontWeight.Medium
                                    ),
                                    decorationBox = { innerTextField ->
                                        if (description.isEmpty()) {
                                            Text(
                                                "Kya kharch kiya? (Details)", 
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = Palette.TextSecondary.copy(alpha = 0.5f),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Categories
                            Text(
                                "Category chunein:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.TextSecondary
                                )
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(predefinedCategories) { catInfo ->
                                    val isSelected = category == catInfo.name
                                    Box(
                                        modifier = Modifier
                                            .clip(Shapes.sm)
                                            .background(
                                                if (isSelected) {
                                                    catInfo.color.copy(alpha = 0.22f)
                                                } else {
                                                    Color.White.copy(alpha = 0.05f)
                                                }
                                            )
                                            .border(
                                                width = 1.dp, 
                                                color = if (isSelected) catInfo.color else Color.White.copy(alpha = 0.1f), 
                                                shape = Shapes.sm
                                            )
                                            .clickable { category = catInfo.name }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "${catInfo.icon} ${catInfo.name}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) {
                                                catInfo.color
                                            } else {
                                                Palette.TextSecondary
                                            }
                                        )
                                    }
                                }
                            }

                            manualErrorMessage?.let { errMsg ->
                                Text(
                                    text = errMsg,
                                    color = Palette.Danger,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    val amt = amount.toDoubleOrNull()
                                    if (amt == null || amt <= 0.0) {
                                        manualErrorMessage = "Paisa toh batao yaar, kitna gaya?"
                                        com.example.utils.PremiumToast.show(context, "Pehle amount to bharo! \uD83D\uDCB0")
                                    } else if (description.isBlank()) {
                                        manualErrorMessage = "Hisaab ka details (description) bhi likho yaar!"
                                    } else {
                                        manualErrorMessage = null
                                        val singleExpense = com.example.api.GeminiExpenseItem(description, amt, category.ifEmpty { "Other" })
                                        val serialized = com.example.api.GeminiClient.serializeExpenses(listOf(singleExpense))
                                        viewModel.addPreParsedDiaryEntry(
                                            originalText = description,
                                            parsedExpensesJson = serialized,
                                            mood = "Normal",
                                            aiInsight = "Manually add ki gayi entry"
                                        )
                                        
                                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                                        if (uid != null) {
                                            val db = FirebaseFirestore.getInstance()
                                            val data = hashMapOf(
                                                "date" to com.google.firebase.Timestamp(java.util.Date()),
                                                "originalText" to "$description: $amt",
                                                "expenses" to listOf(mapOf("item" to description, "amount" to amt, "category" to category.ifEmpty { "Other" })),
                                                "mood" to "Normal",
                                                "aiInsight" to "Manually add ki gayi entry",
                                                "totalAmount" to amt,
                                                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                            )
                                            db.collection("users").document(uid).collection("entries").add(data)
                                                .addOnSuccessListener {
                                                    com.example.utils.PremiumToast.show(context, "Hisaab mein jud gaya! ✅")
                                                }
                                        }
                                        
                                        amount = ""
                                        description = ""
                                        category = ""
                                        currentToastMessage = "Hisaab ho gaya! ✅"
                                        showSuccess = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Palette.Teal),
                                shape = Shapes.sm,
                                contentPadding = PaddingValues(vertical = 12.dp),
                                modifier = Modifier.fillMaxWidth().animateButtonPress()
                            ) {
                                Text(
                                    text = "Hisaab mein jodein (Save)", 
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.OnAccent
                                    )
                                )
                            }
                        }
                    }
                }
            }

            val totalLene = khataParties.filter { it.netBalance > 0 }.sumOf { it.netBalance }
            val totalDene = khataParties.filter { it.netBalance < 0 }.sumOf { kotlin.math.abs(it.netBalance) }
            if (khataParties.isNotEmpty()) {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateCardEntrance(70)
                        .clickable { navController?.navigate(Screen.Khata.route) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(Shapes.sm)
                                    .background(Palette.Purple.copy(alpha = 0.15f))
                                    .border(1.dp, Palette.Purple.copy(alpha = 0.3f), Shapes.sm),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = Palette.Purple,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Apna Khata (Udhaar)",
                                    color = Palette.TextPrimary,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${khataParties.size} grahak / dost jude hain",
                                    color = Palette.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (totalLene > 0) {
                                Text(
                                    text = "₹${totalLene.toInt()} Lene",
                                    color = Palette.Teal,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            if (totalDene > 0) {
                                Text(
                                    text = "₹${totalDene.toInt()} Dene",
                                    color = Palette.Danger,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            if (totalLene == 0.0 && totalDene == 0.0) {
                                Text(
                                    text = "Hisaab Barabar",
                                    color = Palette.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // Section header "Aaj ke transactions" + a glass list card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateCardEntrance(80),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aaj ke transactions", 
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, 
                            color = Palette.TextPrimary
                        )
                    )
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Palette.TextSecondary
                        )
                    )
                }
                
                if (todayExpenses.isEmpty()) {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🤷‍♂️", fontSize = 24.sp)
                            Text(
                                text = "Koi naya kharcha nahi mila.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Palette.TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                } else {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            todayExpenses.sortedByDescending { it.timestamp }.forEachIndexed { idx, expense ->
                                val catInfo = com.example.data.CategoryConfig.getCategoryByName(expense.category)
                                val categoryEmoji = catInfo.icon
                                val categoryColor = catInfo.color

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(categoryEmoji, fontSize = 18.sp)
                                        }
                                        Column {
                                            Text(
                                                text = expense.description,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Palette.TextPrimary
                                            )
                                            Text(
                                                text = expense.category,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Palette.TextSecondary
                                                )
                                            )
                                        }
                                    }
                                    
                                    // Expense amount coral + explicit "−" sign so meaning isn't color-only (colorblind-safe)
                                    Text(
                                        text = "−₹${expense.amount.toInt()}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            color = Palette.Danger
                                        )
                                    )
                                }

                                if (idx < todayExpenses.size - 1) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                }
                            }
                        }
                    }
                }
            }

            // High bottom spacing to avoid floating navigation bar clipping
            Spacer(modifier = Modifier.height(140.dp))
        }

        // Floating success notification overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(top = 16.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedVisibility(
                visible = showSuccess,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f)
            ) {
                Card(
                    shape = Shapes.md,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxHeight()
                                .background(Palette.Teal)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (currentToastMessage.isEmpty()) "Hisaab ho gaya! ✅" else currentToastMessage,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Palette.TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))

                            val isAiMessage = currentToastMessage.isNotEmpty() && currentToastMessage != "Hisaab ho gaya! ✅"
                            if (isAiMessage) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Palette.Teal.copy(alpha = 0.15f))
                                        .border(1.dp, Palette.Teal.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "AI",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Palette.Teal
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Beautiful glass bottom PIN sheet
        AnimatedVisibility(
            visible = showGlassPinSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Dimmed transparent glass backdrop
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showGlassPinSheet = false }
                )

                var enteredPin by remember { mutableStateOf("") }
                var pinErrorMsg by remember { mutableStateOf<String?>(null) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                        )
                        .drawBehind {
                            // Subtle luxury gradient sheen
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.12f),
                                        Color.Transparent
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width * 0.4f, size.height * 0.4f)
                                )
                            )
                        }
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Top drag indicator handle
                    Box(
                        modifier = Modifier
                            .size(40.dp, 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    )

                    Text(
                        text = "🔒 Unlock Privacy Data",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Palette.TextPrimary
                        )
                    )

                    Text(
                        text = "Apna 4-digit PIN daalein Aaj ka Kharcha unlock karne ke liye.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Palette.TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Dot indicators for entered PIN digits
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        for (i in 0 until 4) {
                            val isFilled = i < enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFilled) Palette.Teal else Color.White.copy(alpha = 0.08f)
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isFilled) Palette.Teal else Color.White.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    if (pinErrorMsg != null) {
                        Text(
                            text = pinErrorMsg ?: "",
                            color = Palette.Danger,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Glass Keyboard Layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val keys = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("✕", "0", "✓")
                        )

                        keys.forEach { rowKeys ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowKeys.forEach { key ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .clip(Shapes.md)
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .border(
                                                width = 1.dp,
                                                color = Color.White.copy(alpha = 0.1f),
                                                shape = Shapes.md
                                            )
                                            .clickable {
                                                when (key) {
                                                    "✕" -> {
                                                        if (enteredPin.isNotEmpty()) {
                                                            enteredPin = enteredPin.dropLast(1)
                                                            pinErrorMsg = null
                                                        }
                                                    }
                                                    "✓" -> {
                                                        if (enteredPin.length == 4) {
                                                            val pinIsSet = com.example.utils.IncomeVisibilityManager.isPinSet()
                                                            val pinCorrect = if (pinIsSet) {
                                                                com.example.utils.IncomeVisibilityManager.verifyPin(enteredPin)
                                                            } else {
                                                                enteredPin == "1234"
                                                            }
                                                            if (pinCorrect) {
                                                                isPrivacyLocked = false
                                                                showGlassPinSheet = false
                                                            } else {
                                                                pinErrorMsg = "Galat PIN! Koshish karein firse."
                                                                enteredPin = ""
                                                            }
                                                        } else {
                                                            pinErrorMsg = "Poora 4-digit PIN enter karein!"
                                                        }
                                                    }
                                                    else -> {
                                                        if (enteredPin.length < 4) {
                                                            enteredPin += key
                                                            pinErrorMsg = null

                                                            if (enteredPin.length == 4) {
                                                                val pinIsSet = com.example.utils.IncomeVisibilityManager.isPinSet()
                                                                val pinCorrect = if (pinIsSet) {
                                                                    com.example.utils.IncomeVisibilityManager.verifyPin(enteredPin)
                                                                } else {
                                                                    enteredPin == "1234"
                                                                }
                                                                if (pinCorrect) {
                                                                    isPrivacyLocked = false
                                                                    showGlassPinSheet = false
                                                                } else {
                                                                    pinErrorMsg = "Galat PIN! Koshish karein firse."
                                                                    enteredPin = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            .animateButtonPress(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (key == "✓") Palette.Teal else if (key == "✕") Palette.Danger else Palette.TextPrimary,
                                                fontSize = 20.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!com.example.utils.IncomeVisibilityManager.isPinSet()) {
                        Text(
                            text = "Tip: Default PIN '1234' hai (Set custom PIN in settings)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Palette.TextSecondary.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Normal
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

    }
}

// Particle model for satisfying confetti burst
class ConfettiParticle(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    val rotationSpeed: Float
)

@Composable
fun ConfettiBurst(active: Boolean) {
    if (!active) return
    val particles = remember {
        val random = java.util.Random()
        List(60) {
            val angle = random.nextDouble() * 2 * Math.PI
            val speed = 4f + random.nextFloat() * 16f
            ConfettiParticle(
                x = 0f,
                y = 0f,
                vx = (Math.cos(angle) * speed).toFloat(),
                vy = (Math.sin(angle) * speed - 6f).toFloat(),
                color = Color(
                    red = random.nextFloat(),
                    green = random.nextFloat(),
                    blue = random.nextFloat(),
                    alpha = 1.0f
                ),
                size = 12f + random.nextFloat() * 20f,
                rotationSpeed = (random.nextFloat() - 0.5f) * 15f
            )
        }
    }

    var ticks by remember { mutableStateOf(0) }
    LaunchedEffect(active) {
        while (active) {
            kotlinx.coroutines.delay(16)
            ticks++
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 3f

        particles.forEach { p ->
            if (p.x == 0f && p.y == 0f) {
                p.x = cx
                p.y = cy
            }
            p.x += p.vx
            p.y += p.vy + (ticks * 0.16f)

            drawRect(
                color = p.color.copy(alpha = (1f - (ticks / 100f)).coerceIn(0f, 1f)),
                topLeft = Offset(p.x, p.y),
                size = androidx.compose.ui.geometry.Size(p.size, p.size)
            )
        }
    }
}

@Composable
fun AnimatedAmountText(
    targetAmount: Double,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle
) {
    val animatedAmountState = animateFloatAsState(
        targetValue = targetAmount.toFloat(),
        animationSpec = tween(durationMillis = 350, easing = EaseOutExpo),
        label = "amount_reveal"
    )
    Text(
        text = "₹${animatedAmountState.value.toInt()}",
        style = style,
        modifier = modifier
    )
}

@Composable
fun Modifier.animateCardEntrance(delayMillis: Int = 0): Modifier {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) {
            kotlinx.coroutines.delay(delayMillis.toLong())
        }
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
        label = "card_alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.95f,
        animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
        label = "card_scale"
    )
    return this.graphicsLayer {
        this.alpha = alpha
        this.scaleX = scale
        this.scaleY = scale
    }
}

