package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppPurple
import com.example.ui.theme.AppText
import com.example.ui.theme.AppTextLight
import com.example.ui.theme.Palette
import com.example.ui.theme.Dimens
import com.example.ui.theme.Shapes
import com.example.ui.theme.isAutoDarkTheme
import com.example.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.HazeMaterials

// ---- Refined Glass palette — thin aliases onto the single source of truth (Palette). ----
private val KMint = Palette.Teal
private val KMintDeep = Palette.TealDeep
private val KViolet = Palette.Purple
private val KCoral = Palette.Danger
private val KAmber = Palette.Warning
private val KText = Palette.TextPrimary
private val KText2 = Palette.TextSecondary

// WhatsApp brand colours — via Palette.Brand (single source)
private val WhatsAppGreenDark = com.example.ui.theme.Palette.Brand.WhatsAppDark
private val WhatsAppGreen = com.example.ui.theme.Palette.Brand.WhatsApp

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun KahaniScreen(viewModel: ExpenseViewModel) {
    val shareHazeState = remember { HazeState() }
    val expenses by viewModel.allExpenses.collectAsStateWithLifecycle()
    val diaryEntries by viewModel.allDiaryEntries.collectAsStateWithLifecycle()
    val monthlySummary by viewModel.monthlySummary.collectAsStateWithLifecycle()
    val isSummaryLoading by viewModel.isSummaryLoading.collectAsStateWithLifecycle()
    val summaryError by viewModel.summaryError.collectAsStateWithLifecycle()
    val monthlyBudget by viewModel.monthlyBudget.collectAsStateWithLifecycle(initialValue = 0.0)
    val categoryBudgets by viewModel.categoryBudgets.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()

    val monthlySummaries by viewModel.monthlySummaries.collectAsStateWithLifecycle()
    val monthDetails by viewModel.monthDetails.collectAsStateWithLifecycle()
    val loadingMonthKey by viewModel.loadingMonthKey.collectAsStateWithLifecycle()
    val monthStories by viewModel.monthStories.collectAsStateWithLifecycle()
    val storyLoadingKey by viewModel.storyLoadingKey.collectAsStateWithLifecycle()

    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    // key on uid so summaries load once login resolves (Unit would fire too early and stay empty for the session)
    LaunchedEffect(currentUserId) {
        viewModel.loadMonthlySummaries()
    }

    val isDark = com.example.ui.theme.LocalThemeIsDark.current
    val context = LocalContext.current
    val monthName = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()) }

    // Filter data for current month
    val calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfMonth = calendar.timeInMillis

    val monthlyExpenses = expenses.filter { it.timestamp >= startOfMonth }
    val monthlyDiaryEntries = diaryEntries.filter { it.timestamp >= startOfMonth }
    val monthlyTotal = monthlyExpenses.sumOf { it.amount }

    // Last 30 days entries for Mood stream (sorted chronologically)
    val thirtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DATE, -30) }.timeInMillis
    val last30DaysEntries = diaryEntries
        .filter { it.timestamp >= thirtyDaysAgo }
        .sortedBy { it.timestamp }

    // Calculate totals by category
    val totalsByCategory = monthlyExpenses
        .groupBy { standardizeCategory(it.category) }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    val scrollState = rememberScrollState()

    // Monthly data for AI summary
    val compileMonthlyEntriesText = remember(monthlyDiaryEntries) {
        if (monthlyDiaryEntries.isEmpty()) "" else {
            monthlyDiaryEntries.joinToString("\n") { entry ->
                val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(entry.timestamp))
                val parsedExp = com.example.api.GeminiClient.deserializeExpenses(entry.parsedExpensesJson)
                val spentTotal = parsedExp.sumOf { it.amount }
                "Date: $dateStr, Mood: ${entry.mood}, Story text: ${entry.originalText}, Total Spent: ₹${spentTotal.toInt()}"
            }
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

    val totalIncomeVal = monthlyBudget
    val bachat = totalIncomeVal - monthlyTotal
    val bachatPercent = if (totalIncomeVal > 0) ((bachat / totalIncomeVal) * 100).toInt() else 0

    val (bachatColor, bachatMessage, bachatPillText) = when {
        bachatPercent > 30 -> Triple(KMint, "Wah! Bahut acha! 🎉", "Wah! 🎉")
        bachatPercent in 10..30 -> Triple(KAmber, "Theek hai, aur bachaao!", "Theek hai")
        else -> Triple(KCoral, "Yaar thoda sambhalo!", "Sambhalo!")
    }

    // Daily expenses aggregation for Best/Worst day
    val dailyExpenses = remember(monthlyExpenses) {
        monthlyExpenses.groupBy {
            SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(it.timestamp))
        }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }
    val bestDayPair = dailyExpenses.minByOrNull { it.value }
    val worstDayPair = dailyExpenses.maxByOrNull { it.value }

    // Stagger animation controller for the main sections
    var sectionsVisible by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in 1..8) {
            delay(120)
            sectionsVisible = i
        }
    }

    @Composable
    fun StaggeredSection(index: Int, content: @Composable () -> Unit) {
        val alpha by animateFloatAsState(
            targetValue = if (sectionsVisible >= index) 1f else 0f,
            animationSpec = tween(300, easing = LinearOutSlowInEasing),
            label = "section_alpha_$index"
        )
        val scale by animateFloatAsState(
            targetValue = if (sectionsVisible >= index) 1f else 0.95f,
            animationSpec = tween(300, easing = LinearOutSlowInEasing),
            label = "section_scale_$index"
        )
        Box(
            modifier = Modifier
                .graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale)
                .fillMaxWidth()
        ) {
            content()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .animateScreenEntrance()
                .haze(state = shareHazeState)
                .verticalScroll(scrollState)
        ) {
            // 1. HEADER — plain centered title (no hard purple gradient)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = Dimens.xxl, end = Dimens.xxl, top = Dimens.xl, bottom = Dimens.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$displayName ki $monthName ki Kahani",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = KText,
                        fontSize = 21.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }

            // Inner Grid Layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.lg, vertical = Dimens.md),
                verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap)
            ) {

                // 2. INCOME/KHARCH/BACHAT CARD
                StaggeredSection(index = 1) {
                    val isIncomeVisible by com.example.utils.IncomeVisibilityManager.isIncomeVisible.collectAsState()
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Dimens.lg)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Is Mahine",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KMint
                                    )
                                )
                                IconButton(
                                    onClick = {
                                        com.example.utils.IncomeVisibilityManager.toggleVisibility(context, com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "")
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Text(
                                        text = if (isIncomeVisible) "🔓" else "🔒",
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                                // Row 1: Kamaai (blurred when locked)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Kamaai:",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = KText
                                    )
                                    com.example.ui.components.BlurLockable(
                                        locked = !isIncomeVisible,
                                        onUnlock = { com.example.utils.IncomeVisibilityManager.toggleVisibility(context, currentFirebaseUser?.uid ?: "") }
                                    ) {
                                        AnimatedNumber(
                                            targetValue = totalIncomeVal.toInt(),
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                color = KMint
                                            ),
                                            prefix = "₹"
                                        )
                                    }
                                }

                                HorizontalDivider(color = Palette.BorderSoft)

                                // Row 2: Kharch (always clear)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Kharch:",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = KText
                                    )
                                    AnimatedNumber(
                                        targetValue = monthlyTotal.toInt(),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            color = KCoral
                                        ),
                                        prefix = "₹"
                                    )
                                }

                                HorizontalDivider(color = Palette.BorderSoft)

                                // Row 3: Bachat + Pill (blurred when locked)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🏦 Bachat:",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = KText
                                    )
                                    com.example.ui.components.BlurLockable(
                                        locked = !isIncomeVisible,
                                        onUnlock = { com.example.utils.IncomeVisibilityManager.toggleVisibility(context, currentFirebaseUser?.uid ?: "") }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
                                        ) {
                                            AnimatedNumber(
                                                targetValue = bachat.toInt(),
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Black,
                                                    color = bachatColor
                                                ),
                                                prefix = "₹"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(Shapes.sm)
                                                    .background(bachatColor.copy(alpha = 0.15f))
                                                    .border(1.dp, bachatColor.copy(alpha = 0.4f), Shapes.sm)
                                                    .padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                                            ) {
                                                Text(
                                                    text = "$bachatPercent% $bachatPillText",
                                                    color = bachatColor,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. KHARCHA DNA DONUT CHART
                StaggeredSection(index = 2) {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Dimens.lg)
                        ) {
                            Text(
                                text = "Kharcha Breakdown",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = KMint,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )

                            DonutChart(
                                totalsByCategory = totalsByCategory,
                                monthlyTotal = monthlyTotal,
                                isDark = isDark
                            )
                        }
                    }
                }

                // 3. SAPNE KI PROGRESS
                val activeGoals = goals.filter { !it.isCompleted }
                if (activeGoals.isNotEmpty()) {
                    StaggeredSection(index = 3) {
                        LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                // Faint twinkling stars over glass
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    val random = java.util.Random(42)
                                    repeat(18) {
                                        val x = random.nextFloat() * size.width
                                        val y = random.nextFloat() * size.height
                                        val radius = random.nextFloat() * 2.5f + 0.8f
                                        val alpha = random.nextFloat() * 0.35f + 0.15f
                                        drawCircle(
                                            Color.White,
                                            radius,
                                            center = androidx.compose.ui.geometry.Offset(x, y),
                                            alpha = alpha
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(Dimens.xl)
                                ) {
                                    Text(
                                        text = "Mera Sapna",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = KText,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )

                                    val reducedMotionShimmer = com.example.ui.components.rememberReducedMotion()
                                    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
                                    val shimmerOffset by infiniteTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = if (reducedMotionShimmer) 0f else 1800f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(900, easing = LinearEasing),
                                            repeatMode = RepeatMode.Restart
                                        ),
                                        label = "shimmer_offset"
                                    )

                                    activeGoals.forEach { goal ->
                                        val remaining = goal.targetAmount - goal.savedAmount
                                        val dailySavings = bachat / 30.0
                                        val daysAway = if (dailySavings > 0) Math.ceil(remaining / dailySavings).toInt() else -1

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(Dimens.md)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
                                            ) {
                                                Text(text = goal.emoji, fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics {})
                                                Text(
                                                    text = goal.name,
                                                    fontWeight = FontWeight.Black,
                                                    color = KText,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            }

                                            val progressFraction = if (goal.targetAmount > 0) {
                                                (goal.savedAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f)
                                            } else 0f

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(14.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.1f))
                                            ) {
                                                var animatedFraction by remember { mutableStateOf(0f) }
                                                LaunchedEffect(progressFraction) {
                                                    animate(
                                                        initialValue = 0f,
                                                        targetValue = progressFraction,
                                                        animationSpec = tween(1200, easing = FastOutSlowInEasing)
                                                    ) { value, _ ->
                                                        animatedFraction = value
                                                    }
                                                }

                                                if (animatedFraction > 0f) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .fillMaxWidth(animatedFraction)
                                                            .clip(CircleShape)
                                                            .background(
                                                                Brush.horizontalGradient(
                                                                    colors = listOf(KMint, KViolet)
                                                                )
                                                            )
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(
                                                                    Brush.linearGradient(
                                                                        colors = listOf(
                                                                            Color.Transparent,
                                                                            Color.White.copy(alpha = 0.35f),
                                                                            Color.Transparent
                                                                        ),
                                                                        start = androidx.compose.ui.geometry.Offset(shimmerOffset - 250f, 0f),
                                                                        end = androidx.compose.ui.geometry.Offset(shimmerOffset, 0f)
                                                                    )
                                                                )
                                                        )
                                                    }
                                                }
                                            }

                                            if (daysAway > 0) {
                                                Text(
                                                    text = "$daysAway din mein poora ho sakta hai!",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = KMint
                                                    )
                                                )
                                            } else {
                                                Text(
                                                    text = "Thodi bachat shuru karein, sapna jald poora hoga! 💪",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = KCoral
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

                // 5. BEST/WORST DAY CARDS
                StaggeredSection(index = 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.md)
                    ) {
                        // Best Day
                        LiquidGlassCard(
                            modifier = Modifier.weight(1f),
                            shape = Shapes.lg
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                                Box(
                                    modifier = Modifier
                                        .clip(Shapes.sm)
                                        .background(KMint.copy(alpha = 0.15f))
                                        .border(1.dp, KMint.copy(alpha = 0.4f), Shapes.sm)
                                        .padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                                ) {
                                    Text("Sabse Acha Din", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = KMint)
                                }
                                if (bestDayPair != null) {
                                    Text(
                                        text = bestDayPair.key,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black),
                                        color = KText
                                    )
                                    AnimatedNumber(
                                        targetValue = bestDayPair.value.toInt(),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = KMint,
                                        prefix = "Sirf ₹"
                                    )
                                } else {
                                    Text("No Data Yet", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = KText2)
                                }
                            }
                        }

                        // Worst Day
                        LiquidGlassCard(
                            modifier = Modifier.weight(1f),
                            shape = Shapes.lg
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                                Box(
                                    modifier = Modifier
                                        .clip(Shapes.sm)
                                        .background(KCoral.copy(alpha = 0.15f))
                                        .border(1.dp, KCoral.copy(alpha = 0.4f), Shapes.sm)
                                        .padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                                ) {
                                    Text("Thoda Zyada Din", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = KCoral)
                                }
                                if (worstDayPair != null) {
                                    Text(
                                        text = worstDayPair.key,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black),
                                        color = KText
                                    )
                                    AnimatedNumber(
                                        targetValue = worstDayPair.value.toInt(),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = KCoral,
                                        prefix = "₹",
                                        suffix = " kharch"
                                    )
                                } else {
                                    Text("No Data Yet", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = KText2)
                                }
                            }
                        }
                    }
                }

                // 7. MOOD STREAM
                StaggeredSection(index = 5) {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.lg)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Mood Calendar",
                                    tint = KMint
                                )
                                Text(
                                    text = "Mood — Last 30 Days",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = KMint
                                )
                            }

                            if (last30DaysEntries.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Dimens.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Koi mood stream nahi mila. Kahani likhna shuru karein!",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = KText2,
                                            textAlign = TextAlign.Center
                                        )
                                    )
                                }
                            } else {
                                val moodByDate = remember(last30DaysEntries) {
                                    last30DaysEntries
                                        .groupBy { SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(it.timestamp)) }
                                        .values
                                        .map { it.last() }
                                }

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.lg),
                                    contentPadding = PaddingValues(vertical = Dimens.xs),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(moodByDate) { idx, entry ->
                                        val (emoji, color, _) = getMoodAttributes(entry.mood)
                                        val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(entry.timestamp))

                                        MoodCircle(
                                            emoji = emoji,
                                            color = color,
                                            dateStr = dateStr,
                                            index = idx
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 8. AI LETTER CARD
                StaggeredSection(index = 6) {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Dimens.lg)
                        ) {
                            Text("🪶", fontSize = 36.sp, modifier = Modifier.clearAndSetSemantics {})
                            Text(
                                text = "Is Mahine Ki Kahani",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = KText
                                )
                            )

                            if (isSummaryLoading) {
                                // Fixed-height placeholder to avoid layout shift while loading.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(96.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(Dimens.md)
                                    ) {
                                        CircularProgressIndicator(color = KViolet)
                                        Text(
                                            text = "Kahani likhi jaa rahi hai... ✍️",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                lineHeight = 24.sp,
                                                color = KText2
                                            ),
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }
                            } else if (monthlySummary != null) {
                                Text(
                                    text = monthlySummary!!,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = KText
                                    ),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(Dimens.sm))

                                // Mint gradient "Regenerate Karo 🪶"
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Dimens.xxl)
                                        .clip(Shapes.pill)
                                        .background(Brush.horizontalGradient(listOf(KMint, KMintDeep)))
                                        .clickable { viewModel.fetchMonthlySummary(compileMonthlyEntriesText) }
                                        .animateButtonPress()
                                        .padding(vertical = Dimens.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Regenerate Karo 🪶",
                                        color = Palette.OnAccent,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Is mahine ke saare moods aur kharchon ko ek anokhi AI kahani aur insightful guidance mein badlein.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = 24.sp,
                                        color = KText2
                                    ),
                                    fontStyle = FontStyle.Italic,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(Dimens.sm))

                                // Mint gradient "Generate Karo ✨"
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Dimens.xxl)
                                        .clip(Shapes.pill)
                                        .background(Brush.horizontalGradient(listOf(KMint, KMintDeep)))
                                        .clickable { viewModel.fetchMonthlySummary(compileMonthlyEntriesText) }
                                        .animateButtonPress()
                                        .padding(vertical = Dimens.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Generate Karo",
                                        color = Palette.OnAccent,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            summaryError?.let { err ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(Shapes.sm)
                                        .background(Palette.DangerBg)
                                        .padding(horizontal = Dimens.md, vertical = Dimens.sm)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(Dimens.xs)
                                    ) {
                                        Text(
                                            text = err,
                                            color = KCoral,
                                            style = MaterialTheme.typography.labelSmall,
                                            textAlign = TextAlign.Center
                                        )
                                        TextButton(
                                            onClick = { viewModel.fetchMonthlySummary(compileMonthlyEntriesText) }
                                        ) {
                                            Text(
                                                text = "Retry",
                                                color = KCoral,
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 8. PURANE MAHINE SECTION (Historical monthly summaries & details)
                StaggeredSection(index = 7) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Dimens.xxl),
                        verticalArrangement = Arrangement.spacedBy(Dimens.md)
                    ) {
                        Text(
                            text = "Purane Mahine",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = KMint
                            ),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.sm, vertical = Dimens.xs),
                            textAlign = TextAlign.Start
                        )
                        
                        if (monthlySummaries.isEmpty()) {
                            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Purana koi hisaab nahi mila.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Palette.TextSecondary,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            var expandedKey by remember { mutableStateOf<String?>(null) }
                            val isIncomeVisible by com.example.utils.IncomeVisibilityManager.isIncomeVisible.collectAsState()
                            val reducedMotion = com.example.ui.components.rememberReducedMotion()
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Dimens.md),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                monthlySummaries.forEach { summary ->
                                    val key = "${summary.year}-${summary.month}"
                                    val isExpanded = expandedKey == key
                                    val rotation by animateFloatAsState(
                                        targetValue = if (isExpanded) 180f else 0f,
                                        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 300),
                                        label = "chevron_rotate"
                                    )
                                    
                                    LiquidGlassCard(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            // Row for Summary — only the header toggles expand (not the expanded body)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        if (isExpanded) {
                                                            expandedKey = null
                                                        } else {
                                                            expandedKey = key
                                                            viewModel.fetchMonthDetail(summary.year, summary.month)
                                                        }
                                                    }
                                                    .padding(vertical = Dimens.sm),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(Dimens.xs)
                                                ) {
                                                    Text(
                                                        text = summary.label,
                                                        style = MaterialTheme.typography.bodyLarge.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = Palette.TextPrimary
                                                        )
                                                    )
                                                    val (moodEmoji, _, moodLabel) = getMoodAttributes(summary.topMood)
                                                    Text(
                                                        text = "$moodEmoji $moodLabel • ${summary.entryCount} stories",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = Palette.TextSecondary
                                                    )

                                                    // Income, Kharch & Bachat — FlowRow so items wrap to next line instead of squeezing a number into a vertical char-stack
                                                    FlowRow(
                                                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.xs),
                                                        horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                                                        verticalArrangement = Arrangement.spacedBy(Dimens.xs)
                                                    ) {
                                                        // Income — blur-reveal (tap to unlock) instead of ₹•••• dots
                                                        if (summary.income == null) {
                                                            Text(
                                                                text = "Kamaai —",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = Palette.TextSecondary
                                                            )
                                                        } else {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(
                                                                    text = "Kamaai ",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                    color = Palette.Teal
                                                                )
                                                                com.example.ui.components.BlurLockable(
                                                                    locked = !isIncomeVisible,
                                                                    onUnlock = { com.example.utils.IncomeVisibilityManager.toggleVisibility(context, currentFirebaseUser?.uid ?: "") }
                                                                ) {
                                                                    Text(
                                                                        text = "₹${summary.income.toInt()}",
                                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                        color = Palette.Teal,
                                                                        maxLines = 1,
                                                                        softWrap = false
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Text(
                                                            text = "•",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Palette.TextSecondary.copy(alpha = 0.5f)
                                                        )

                                                        // Kharch
                                                        Text(
                                                            text = "Kharch −₹${summary.totalSpent.toInt()}",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = Palette.Danger,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )

                                                        // Bachat
                                                        if (summary.income != null) {
                                                            val bachatVal = summary.income - summary.totalSpent
                                                            Text(
                                                                text = "•",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Palette.TextSecondary.copy(alpha = 0.5f)
                                                            )
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(
                                                                    text = "Bachat ",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                    color = if (bachatVal >= 0) Palette.Teal else Palette.Danger
                                                                )
                                                                com.example.ui.components.BlurLockable(
                                                                    locked = !isIncomeVisible,
                                                                    onUnlock = { com.example.utils.IncomeVisibilityManager.toggleVisibility(context, currentFirebaseUser?.uid ?: "") }
                                                                ) {
                                                                    Text(
                                                                        text = "₹${bachatVal.toInt()}",
                                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                        color = if (bachatVal >= 0) Palette.Teal else Palette.Danger,
                                                                        maxLines = 1,
                                                                        softWrap = false
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                
                                                Icon(
                                                    imageVector = androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                    tint = Palette.TextSecondary,
                                                    modifier = Modifier
                                                        .graphicsLayer(rotationZ = rotation)
                                                        .size(24.dp)
                                                )
                                            }
                                            
                                            // Expanded detail body
                                            AnimatedVisibility(
                                                visible = isExpanded,
                                                enter = expandVertically(),
                                                exit = shrinkVertically()
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = Dimens.lg),
                                                    verticalArrangement = Arrangement.spacedBy(Dimens.lg)
                                                ) {
                                                    HorizontalDivider(color = Palette.BorderSoft, thickness = Dimens.hairline)
                                                    
                                                    if (loadingMonthKey == key && !monthDetails.containsKey(key)) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = Dimens.xl),
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(Dimens.md)
                                                        ) {
                                                            CircularProgressIndicator(
                                                                color = Palette.Teal,
                                                                modifier = Modifier.size(36.dp)
                                                            )
                                                            Text(
                                                                text = "Hisaab nikala jaa raha hai...",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = Palette.TextSecondary
                                                            )
                                                        }
                                                    } else if (monthDetails[key] == null) {
                                                        // not loading + no cached detail = fetch failed → offer inline retry
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = Dimens.xl),
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(Dimens.md)
                                                        ) {
                                                            Text(
                                                                text = "Hisaab load nahi hua.",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = Palette.TextSecondary
                                                            )
                                                            Text(
                                                                text = "Dobara try karein",
                                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                                color = Palette.Teal,
                                                                modifier = Modifier
                                                                    .clip(Shapes.pill)
                                                                    .clickable { viewModel.fetchMonthDetail(summary.year, summary.month) }
                                                                    .padding(horizontal = Dimens.lg, vertical = Dimens.sm)
                                                            )
                                                        }
                                                    } else {
                                                        val detail = monthDetails[key]
                                                        if (detail != null) {
                                                            // 1. Donut Chart
                                                            if (detail.categoryTotals.isNotEmpty()) {
                                                                DonutChart(
                                                                    totalsByCategory = detail.categoryTotals.toList().sortedByDescending { it.second }, showLegend = false,
                                                                    monthlyTotal = detail.totalSpent,
                                                                    isDark = isDark,
                                                                    modifier = Modifier.fillMaxWidth()
                                                                )
                                                            }
                                                            
                                                            // 2. Category Bars
                                                            if (detail.categoryTotals.isNotEmpty()) {
                                                                Column(
                                                                    verticalArrangement = Arrangement.spacedBy(Dimens.md),
                                                                    modifier = Modifier.fillMaxWidth()
                                                                ) {
                                                                    Text(
                                                                        text = "Kharch breakdown",
                                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                                        color = Palette.TextPrimary
                                                                    )
                                                                    detail.categoryTotals.toList().sortedByDescending { it.second }.forEachIndexed { idx, (catName, amount) ->
                                                                        CategoryBarRow(
                                                                            categoryName = catName,
                                                                            amount = amount,
                                                                            monthlyTotal = detail.totalSpent,
                                                                            budgetLimit = 0.0,
                                                                            isBudgetSet = false,
                                                                            index = idx,
                                                                            isDark = isDark
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            
                                                            // 3. Mood Row
                                                            if (detail.moodCounts.isNotEmpty()) {
                                                                Column(
                                                                    verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                                                                    modifier = Modifier.fillMaxWidth()
                                                                ) {
                                                                    Text(
                                                                        text = "Mood breakdown",
                                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                                        color = Palette.TextPrimary
                                                                    )
                                                                    FlowRow(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                                                                        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                                                                    ) {
                                                                        detail.moodCounts.forEach { (moodName, count) ->
                                                                            val (emoji, color, label) = getMoodAttributes(moodName)
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .clip(Shapes.pill)
                                                                                    .background(color.copy(alpha = 0.15f))
                                                                                    .border(1.dp, color.copy(alpha = 0.3f), Shapes.pill)
                                                                                    .padding(horizontal = Dimens.md, vertical = Dimens.xs)
                                                                            ) {
                                                                                Text(
                                                                                    text = "$emoji $label: $count",
                                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                                    color = Palette.TextPrimary,
                                                                                    maxLines = 1,
                                                                                    softWrap = false
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            // Past Month Kahani & Share actions
                                                            HorizontalDivider(
                                                                color = Palette.BorderSoft,
                                                                thickness = Dimens.hairline,
                                                                modifier = Modifier.padding(vertical = Dimens.md)
                                                            )

                                                            val isThisMonthStoryLoading = storyLoadingKey == key

                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                // Button A — "Is mahine ki kahani"
                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .height(48.dp)
                                                                        .clip(Shapes.pill)
                                                                        .background(Color.White.copy(alpha = 0.06f))
                                                                        .border(
                                                                            width = 1.dp,
                                                                            color = KMint.copy(alpha = if (isThisMonthStoryLoading) 0.15f else 0.35f),
                                                                            shape = Shapes.pill
                                                                        )
                                                                        .clickable(enabled = !isThisMonthStoryLoading) {
                                                                            val monthEntriesText = detail.entries.joinToString("\n") { entry ->
                                                                                val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(entry.timestamp))
                                                                                val parsedExp = com.example.api.GeminiClient.deserializeExpenses(entry.parsedExpensesJson)
                                                                                val spentTotal = parsedExp.sumOf { it.amount }
                                                                                "Date: $dateStr, Mood: ${entry.mood}, Story text: ${entry.originalText}, Total Spent: ₹${spentTotal.toInt()}"
                                                                            }
                                                                            viewModel.generateMonthStory(summary.year, summary.month, monthEntriesText)
                                                                        }
                                                                        .animateButtonPress()
                                                                        .padding(horizontal = Dimens.sm),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = "Is mahine ki kahani",
                                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                                            fontWeight = FontWeight.Bold,
                                                                            fontSize = 12.sp,
                                                                            color = if (isThisMonthStoryLoading) KMint.copy(alpha = 0.4f) else KMint
                                                                        ),
                                                                        textAlign = TextAlign.Center
                                                                    )
                                                                }

                                                                // Button B — "Dosto ko dikhao" (share)
                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .height(48.dp)
                                                                        .clip(Shapes.pill)
                                                                        .background(Color.White.copy(alpha = 0.06f))
                                                                        .border(
                                                                            width = 1.dp,
                                                                            color = KMint.copy(alpha = if (isThisMonthStoryLoading) 0.15f else 0.35f),
                                                                            shape = Shapes.pill
                                                                        )
                                                                        .clickable(enabled = !isThisMonthStoryLoading) {
                                                                            val shareKamaai = detail.income ?: 0.0
                                                                            val shareBachat = if (detail.income != null) detail.income - detail.totalSpent else 0.0
                                                                            val shareBachatPercent = if (detail.income != null && detail.income > 0) { ((detail.income - detail.totalSpent) / detail.income * 100).toInt() } else 0
                                                                            generateShareImageAndShare(
                                                                                context = context,
                                                                                displayName = displayName,
                                                                                monthName = summary.label,
                                                                                kamaai = shareKamaai,
                                                                                kharch = detail.totalSpent,
                                                                                bachat = shareBachat,
                                                                                bachatPercent = shareBachatPercent,
                                                                                isIncomeVisible = isIncomeVisible
                                                                            )
                                                                        }
                                                                        .animateButtonPress()
                                                                        .padding(horizontal = Dimens.sm),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = "Dosto ko dikhao",
                                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                                            fontWeight = FontWeight.Bold,
                                                                            fontSize = 12.sp,
                                                                            color = if (isThisMonthStoryLoading) KMint.copy(alpha = 0.4f) else KMint
                                                                        ),
                                                                        textAlign = TextAlign.Center
                                                                    )
                                                                }
                                                            }

                                                            // Loading Indicator
                                                            if (isThisMonthStoryLoading) {
                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(vertical = Dimens.xl),
                                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                                    verticalArrangement = Arrangement.spacedBy(Dimens.md)
                                                                ) {
                                                                    CircularProgressIndicator(
                                                                        color = KMint,
                                                                        modifier = Modifier.size(36.dp)
                                                                    )
                                                                    Text(
                                                                        text = "Kahani likhi jaa rahi hai...",
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        color = Palette.TextSecondary
                                                                    )
                                                                }
                                                            }

                                                            // Story text display inside LiquidGlassCard with left mint accent
                                                            val storyText = monthStories[key]
                                                            if (storyText != null) {
                                                                Spacer(modifier = Modifier.height(Dimens.md))
                                                                LiquidGlassCard(
                                                                    modifier = Modifier.fillMaxWidth()
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
                                                                                .background(KMint)
                                                                        )
                                                                        Row(
                                                                            modifier = Modifier
                                                                                .weight(1f)
                                                                                .padding(Dimens.lg),
                                                                            horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                                                                            verticalAlignment = Alignment.Top
                                                                        ) {
                                                                            Text("💡", fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics {})
                                                                            Column(
                                                                                modifier = Modifier.weight(1f),
                                                                                verticalArrangement = Arrangement.spacedBy(Dimens.xs)
                                                                            ) {
                                                                                Text(
                                                                                    text = "Mahine ki Kahani",
                                                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                                                        fontWeight = FontWeight.Bold,
                                                                                        color = KMint
                                                                                    )
                                                                                )
                                                                                Text(
                                                                                    text = storyText,
                                                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                                                        lineHeight = 22.sp,
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
                                    }
                                }
                            }
                        }
                    }
                }

                // 9. WHATSAPP SHARE CARD (green gradient kept for brand recognition)
                StaggeredSection(index = 8) {
                    val isIncomeVisible by com.example.utils.IncomeVisibilityManager.isIncomeVisible.collectAsState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Dimens.xxl)
                            .clip(Shapes.lg)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(WhatsAppGreenDark, WhatsAppGreen)
                                )
                            )
                            .clickable {
                                generateShareImageAndShare(
                                    context = context,
                                    displayName = displayName,
                                    monthName = monthName,
                                    kamaai = totalIncomeVal,
                                    kharch = monthlyTotal,
                                    bachat = bachat,
                                    bachatPercent = bachatPercent,
                                    isIncomeVisible = isIncomeVisible
                                )
                            }
                            .padding(Dimens.xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.lg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📱", fontSize = 32.sp, modifier = Modifier.clearAndSetSemantics {})
                            Column {
                                Text(
                                    text = "Dosto ko dikhao! 📱",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "WhatsApp par apna hisaab card share karein",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(140.dp))
            }
        }
    }
}

// ----------------------------------------------------
// CUSTOM DESIGN SUB-COMPONENTS
// ----------------------------------------------------

@Composable
fun AnimatedNumber(
    targetValue: Int,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    style: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
    color: Color = Color.Unspecified
) {
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "number_anim"
    )
    Text(
        text = "$prefix$animatedValue$suffix",
        style = style,
        color = color,
        modifier = modifier
    )
}

@Composable
fun DonutChart(
    totalsByCategory: List<Pair<String, Double>>,
    monthlyTotal: Double,
    isDark: Boolean,
    showLegend: Boolean = true,
    modifier: Modifier = Modifier
) {
    val reducedMotion = com.example.ui.components.rememberReducedMotion()
    var animateFactor by remember { mutableStateOf(0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) { animateFactor = 1f; return@LaunchedEffect }
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing)
        ) { value, _ ->
            animateFactor = value
        }
    }

    val totalAmount = monthlyTotal.toInt()

    // Screen-reader description built from the same data the chart renders.
    val chartDescription = if (totalsByCategory.isEmpty()) {
        "Kharcha breakdown: koi data nahi"
    } else {
        "Kharcha breakdown. " + totalsByCategory.joinToString(", ") { (category, amount) ->
            "${getCategoryUiInfo(category).displayName} ₹${amount.toInt()}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = chartDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.xxl)
    ) {
        // Complete Donut Circle
        Box(
            modifier = Modifier.size(170.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 24.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val centerOffset = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)

                if (totalsByCategory.isEmpty()) {
                    drawArc(
                        color = Color.Gray.copy(alpha = 0.2f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                    )
                } else {
                    var currentStartAngle = -90f
                    val gapDegrees = if (totalsByCategory.size > 1) 2.5f else 0f

                    totalsByCategory.forEach { (category, amount) ->
                        val uiInfo = getCategoryUiInfo(category)
                        val sweepAngle = ((amount / monthlyTotal) * 360f).toFloat()

                        val animatedSweep = sweepAngle * animateFactor
                        val adjustedSweep = (animatedSweep - gapDegrees).coerceAtLeast(0.1f)

                        drawArc(
                            color = uiInfo.color,
                            startAngle = currentStartAngle,
                            sweepAngle = adjustedSweep,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round
                            ),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            topLeft = androidx.compose.ui.geometry.Offset(centerOffset.x - radius, centerOffset.y - radius)
                        )
                        currentStartAngle += sweepAngle
                    }
                }
            }

            // Center Bold text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedNumber(
                    targetValue = totalAmount,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    ),
                    prefix = "₹",
                    color = KText
                )
                Text(
                    text = "Total Kharch",
                    style = MaterialTheme.typography.labelSmall,
                    color = KText2,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showLegend) {
            // Color legend underneath
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                totalsByCategory.forEach { (category, amount) ->
                    val uiInfo = getCategoryUiInfo(category)
                    val percent = if (monthlyTotal > 0) ((amount / monthlyTotal) * 100).toInt() else 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(uiInfo.color)
                            )
                            Text(
                                text = "${uiInfo.emoji} ${uiInfo.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = KText
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedNumber(
                                targetValue = amount.toInt(),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                                prefix = "₹",
                                color = KText
                            )
                            Text(
                                text = "($percent%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = KText2,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryBarRow(
    categoryName: String,
    amount: Double,
    monthlyTotal: Double,
    budgetLimit: Double,
    isBudgetSet: Boolean,
    index: Int,
    isDark: Boolean
) {
    val uiInfo = getCategoryUiInfo(categoryName)
    val percent = if (monthlyTotal > 0) ((amount / monthlyTotal) * 100).toInt() else 0
    val budgetPercent = if (isBudgetSet) ((amount / budgetLimit) * 100).toInt() else 0

    val exceedsBudget = isBudgetSet && amount > budgetLimit
    val barColor = if (exceedsBudget) KCoral else uiInfo.color

    val reducedMotion = com.example.ui.components.rememberReducedMotion()
    var animatedProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(amount, budgetLimit, isBudgetSet, reducedMotion) {
        val targetProgress = if (isBudgetSet) {
            (amount / budgetLimit).toFloat().coerceIn(0.01f, 1.0f)
        } else {
            (amount / monthlyTotal).toFloat().coerceIn(0.01f, 1.0f)
        }
        if (reducedMotion) { animatedProgress = targetProgress; return@LaunchedEffect }
        animate(
            initialValue = 0f,
            targetValue = targetProgress,
            animationSpec = tween(durationMillis = 350, delayMillis = index.coerceAtMost(5) * 30, easing = FastOutSlowInEasing)
        ) { value, _ ->
            animatedProgress = value
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(uiInfo.emoji, fontSize = 20.sp)
                Text(
                    text = uiInfo.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = KText
                )
                if (exceedsBudget) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("⚠️", fontSize = 16.sp)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedNumber(
                    targetValue = amount.toInt(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                    color = KText
                )
                Text(
                    text = if (isBudgetSet) "/₹${budgetLimit.toInt()} ($budgetPercent%)" else "($percent%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (exceedsBudget) KCoral else KText2,
                    fontWeight = if (exceedsBudget) FontWeight.Black else FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(Palette.BorderSoft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(CircleShape)
                    .background(barColor)
            )
        }

        if (exceedsBudget) {
            Text(
                text = "⚠️ Budget exceed ho gaya yaar!",
                color = KCoral,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black)
            )
        }
    }
}

@Composable
fun MoodCircle(
    emoji: String,
    color: Color,
    dateStr: String,
    index: Int
) {
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        startAnim = true
    }

    val scale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "mood_circle_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.xs),
        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .border(1.5.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 18.sp)
        }
        Text(
            text = dateStr,
            style = MaterialTheme.typography.labelSmall.copy(
                color = KText2,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

// ----------------------------------------------------
// UTILS & IMAGE GENERATOR FOR WHATSAPP
// ----------------------------------------------------

data class CategoryUiInfo(
    val displayName: String,
    val emoji: String,
    val color: Color
)

fun getCategoryUiInfo(standardizedName: String): CategoryUiInfo {
    val configCat = com.example.data.CategoryConfig.getCategoryByName(standardizedName)
    return CategoryUiInfo(
        displayName = configCat.name,
        emoji = configCat.icon,
        color = configCat.color
    )
}

fun standardizeCategory(category: String): String {
    val configCat = com.example.data.CategoryConfig.getCategoryByName(category)
    return configCat.name
}

fun getMoodAttributes(mood: String): Triple<String, Color, String> {
    return when (mood.lowercase().trim()) {
        "khush" -> Triple("😊", com.example.ui.theme.Palette.Success, "Khush")
        "thaka" -> Triple("😴", com.example.ui.theme.Palette.Warning, "Thaka")
        "stressed" -> Triple("🤯", com.example.ui.theme.Palette.Danger, "Stressed")
        "sad" -> Triple("😢", com.example.ui.theme.Palette.Category.Padhai, "Sad")
        else -> Triple("😐", com.example.ui.theme.Palette.Category.Sad, "Normal")
    }
}

fun generateShareImageAndShare(
    context: android.content.Context,
    displayName: String,
    monthName: String,
    kamaai: Double,
    kharch: Double,
    bachat: Double,
    bachatPercent: Int,
    isIncomeVisible: Boolean
) {
    val width = 600
    val height = 700
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Background Gradient Slate 900 -> Indigo 950
    val bgPaint = android.graphics.Paint()
    val bgShader = android.graphics.LinearGradient(
        0f, 0f, 0f, height.toFloat(),
        android.graphics.Color.parseColor("#0F172A"),
        android.graphics.Color.parseColor("#1E1B4B"),
        android.graphics.Shader.TileMode.CLAMP
    )
    bgPaint.shader = bgShader
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // Glow accents
    val glowPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
        color = android.graphics.Color.parseColor("#8B5CF6")
        alpha = 30
        maskFilter = android.graphics.BlurMaskFilter(150f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawCircle(0f, 0f, 300f, glowPaint)
    glowPaint.color = android.graphics.Color.parseColor("#25D366")
    canvas.drawCircle(width.toFloat(), height.toFloat(), 250f, glowPaint)

    // Reset mask
    glowPaint.maskFilter = null

    // Draw WhatsApp themed outer border
    val borderPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 4f
        color = android.graphics.Color.parseColor("#25D366")
        alpha = 150
    }
    canvas.drawRoundRect(20f, 20f, width - 20f, height - 20f, 30f, 30f, borderPaint)

    // Title
    val titlePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = 36f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    canvas.drawText("Mera Hisaab", (width / 2).toFloat(), 80f, titlePaint)

    // Subtitle
    val subPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#A5F3FC")
        textSize = 24f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
    }
    canvas.drawText("$displayName ki $monthName ki Kahani", (width / 2).toFloat(), 125f, subPaint)

    // White Card container
    val cardPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#1E293B")
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRoundRect(50f, 170f, (width - 50).toFloat(), 550f, 20f, 20f, cardPaint)

    // Inner Card Border
    val cardBorderPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
        color = android.graphics.Color.parseColor("#475569")
    }
    canvas.drawRoundRect(50f, 170f, (width - 50).toFloat(), 550f, 20f, 20f, cardBorderPaint)

    // Stats
    val textPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = 28f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    }

    val valuePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 30f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    // Kamaai
    canvas.drawText("Kamaai:", 80f, 240f, textPaint)
    val kamaaiStr = if (isIncomeVisible) "₹${kamaai.toInt()}" else "••••"
    valuePaint.color = android.graphics.Color.parseColor("#22C55E")
    canvas.drawText(kamaaiStr, 380f, 240f, valuePaint)

    // Line
    val divPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#334155")
        strokeWidth = 2f
    }
    canvas.drawLine(80f, 280f, (width - 80).toFloat(), 280f, divPaint)

    // Kharch
    canvas.drawText("Kharch:", 80f, 340f, textPaint)
    valuePaint.color = android.graphics.Color.parseColor("#EF4444")
    canvas.drawText("₹${kharch.toInt()}", 380f, 340f, valuePaint)

    // Line
    canvas.drawLine(80f, 380f, (width - 80).toFloat(), 380f, divPaint)

    // Bachat
    canvas.drawText("🏦 Bachat:", 80f, 440f, textPaint)
    val bachatStr = if (isIncomeVisible) "₹${bachat.toInt()}" else "••••"
    val bachatColorHex = when {
        bachatPercent > 30 -> "#22C55E"
        bachatPercent in 10..30 -> "#EAB308"
        else -> "#EF4444"
    }
    valuePaint.color = android.graphics.Color.parseColor(if (isIncomeVisible) bachatColorHex else "#94A3B8")
    canvas.drawText(bachatStr, 380f, 440f, valuePaint)

    if (isIncomeVisible) {
        val feedbackText = when {
            bachatPercent > 30 -> "Wah! 🎉"
            bachatPercent in 10..30 -> "Theek hai"
            else -> "Sambhalo!"
        }
        val pillPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor(bachatColorHex)
            textSize = 22f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText("($bachatPercent% - $feedbackText)", 380f, 490f, pillPaint)
    } else {
        val pillPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.GRAY
            textSize = 22f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText("(Locked 🔒)", 380f, 490f, pillPaint)
    }

    // Footer branding
    val footerPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#94A3B8")
        textSize = 20f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    canvas.drawText("Apna Hisaab Se Banaya Gaya 📱", (width / 2).toFloat(), 610f, footerPaint)

    // Write file & trigger share
    try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = java.io.File(cachePath, "apna_hisaab_share.png")
        val stream = java.io.FileOutputStream(file)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "com.aistudio.apnahisaab.xjqprl.fileprovider",
            file
        )

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TEXT, "Dekho dosto, mera is mahine ka hisaab! 📊")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "WhatsApp Share"))
    } catch (e: Exception) {
        com.example.utils.PremiumToast.show(context, "Image share nahi ho paayi, dobara try karein.")
        e.printStackTrace()
    }
}
