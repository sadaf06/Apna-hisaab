package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.example.ui.screens.animateBottomNavBounce
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.material.icons.filled.Settings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.HazeMaterials
import androidx.compose.material.icons.filled.Logout
import com.example.ui.theme.Palette
import com.example.ui.theme.Dimens
import com.example.ui.theme.Shapes
import com.example.ui.components.brandBrush
import com.example.ui.components.accentBorderBrush
import com.example.ui.components.AppBackground
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AajScreen
import com.example.ui.screens.HisaabScreen
import com.example.ui.screens.KahaniScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.AppPurple
import com.example.ui.theme.AppText
import com.example.ui.theme.AppTextLight
import com.example.viewmodel.ExpenseViewModel
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.ui.screens.KhataScreen
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AdminScreen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ReceiptLong

class MainActivity : ComponentActivity() {
    override fun onStop() {
        super.onStop()
        com.example.utils.IncomeVisibilityManager.hideOnBackground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            androidx.emoji2.text.EmojiCompat.init(androidx.emoji2.bundled.BundledEmojiCompatConfig(applicationContext))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        )

        // Schedule daily reminders at 9PM
        DailyReminderReceiver.scheduleReminder(this)

        // Request POST_NOTIFICATIONS permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                val requestPermissionLauncher = registerForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean -> }
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val expenseViewModel: ExpenseViewModel = viewModel()
            val currentMood by expenseViewModel.currentMood.collectAsState()
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("apna_hisaab_prefs", android.content.Context.MODE_PRIVATE) }
            var showOnboarding by remember { mutableStateOf(sharedPrefs.getBoolean("onboarding_completed", false).not()) }

            val isDark = true

            MyApplicationTheme(mood = currentMood, darkTheme = isDark) {
                if (showOnboarding) {
                    OnboardingScreen(onFinished = { name ->
                        if (name.isNotEmpty()) {
                            expenseViewModel.updateUserName(name)
                        }
                        sharedPrefs.edit().putBoolean("onboarding_completed", true).apply()
                        showOnboarding = false
                    })
                } else {
                    var showSplash by remember { mutableStateOf(true) }
                    if (showSplash) {
                        SplashScreen(onSplashFinished = { showSplash = false })
                    } else {
                        ApnaHisaabApp(expenseViewModel)
                    }
                }
            }
        }
    }
}

enum class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    Aaj("aaj", "Aaj", { Icon(Icons.Default.AddCircle, contentDescription = "Aaj", modifier = Modifier.size(22.dp)) }),
    Hisaab("hisaab", "Hisaab", { Icon(Icons.Default.List, contentDescription = "Hisaab", modifier = Modifier.size(22.dp)) }),
    Kahani("kahani", "Kahani", { Icon(Icons.Default.DateRange, contentDescription = "Kahani", modifier = Modifier.size(22.dp)) }),
    Khata("khata", "Khata", { Icon(Icons.Default.ReceiptLong, contentDescription = "Khata", modifier = Modifier.size(22.dp)) }),
    Settings("settings", "Settings", { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(22.dp)) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(expenseViewModel: ExpenseViewModel) {
    val isDark = com.example.ui.theme.LocalThemeIsDark.current
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val userName by expenseViewModel.userName.collectAsState()
    val userEmail by expenseViewModel.userEmail.collectAsState()
    val syncStatus by expenseViewModel.syncStatus.collectAsState()

    val initials = remember(userName, userEmail) {
        val nameToUse = userName.ifEmpty { userEmail.substringBefore("@") }.trim()
        if (nameToUse.isNotEmpty()) {
            nameToUse.split(" ")
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .take(2)
                .joinToString("")
        } else {
            "U"
        }
    }

    val context = LocalContext.current

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Logout karna chahte ho?", fontWeight = FontWeight.Bold) },
            text = { Text("Aapka data safe rahega 😊") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showLogoutConfirm = false
                        expenseViewModel.logout {
                            val intent = Intent(context, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text("Haan Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Nahi Rukna Hai")
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // User initials as avatar in top bar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = if (isDark) {
                                listOf(Palette.Purple, Palette.Teal)
                            } else {
                                listOf(Palette.Purple, Palette.TealDeep)
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Apna Hisaab",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when (syncStatus) {
                            "🟢" -> "🟢"
                            "🔄" -> "🔄"
                            else -> "🔴"
                        },
                        fontSize = 11.sp
                    )
                    Text(
                        text = when (syncStatus) {
                            "🟢" -> "Online"
                            "🔄" -> "Syncing"
                            else -> "Offline"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stylish Sign Out Button (Same color, style and theme-matching design)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(elevation = 2.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape)
                    .clickable {
                        showLogoutConfirm = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = "Sign Out",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

fun Modifier.glassShadow(
    borderRadius: androidx.compose.ui.unit.Dp = 26.dp,
    blurRadius: androidx.compose.ui.unit.Dp = 30.dp,
    offsetY: androidx.compose.ui.unit.Dp = 10.dp,
    color: Color = Color.Black.copy(alpha = 0.40f)
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

@Composable
fun NavGlassItem(
      icon: ImageVector,
      label: String,
      selected: Boolean,
      modifier: Modifier = Modifier,
      onClick: () -> Unit
  ) {
      val tint by animateColorAsState(
          targetValue = if (selected) Color.White else Palette.TextSecondary,
          animationSpec = tween(220),
          label = "navTint"
      )
      val dotAlpha by animateFloatAsState(
          targetValue = if (selected) 1f else 0f,
          animationSpec = tween(220),
          label = "navDot"
      )
      Column(
          modifier = modifier
              .clip(RoundedCornerShape(16.dp))
              .clickable(onClick = onClick)
              .padding(horizontal = 10.dp, vertical = 6.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
      ) {
          // glowing mint dot ABOVE icon
          Box(
              modifier = Modifier
                  .size(6.dp)
                  .graphicsLayer { alpha = dotAlpha }
                  .drawBehind {
                      drawCircle(
                          brush = Brush.radialGradient(
                              colors = listOf(
                                  Palette.Teal.copy(alpha = 0.60f),
                                  Color.Transparent
                              ),
                              radius = 8.dp.toPx()
                          ),
                          radius = 8.dp.toPx()
                      )
                      drawCircle(color = Palette.Teal)
                  }
          )
          Spacer(Modifier.height(4.dp))
          Icon(
              imageVector = icon,
              contentDescription = label,
              tint = tint,
              modifier = Modifier.size(22.dp)
          )
          Spacer(Modifier.height(3.dp))
          Text(
              text = label,
              color = tint,
              style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                  fontSize = 10.sp
              )
          )
      }
  }

@Composable
fun LiquidGlassNavBar(
      selectedIndex: Int,
      hazeState: HazeState,
      onItemSelected: (Int) -> Unit
  ) {
      val barShape = RoundedCornerShape(28.dp)
      val items = listOf(
          Icons.Default.Add to "Aaj",
          Icons.Default.List to "Hisaab",
          Icons.Default.DateRange to "Kahani",
          Icons.Default.ReceiptLong to "Khata",
          Icons.Default.Settings to "Settings"
      )

      Box(
          modifier = Modifier
              .fillMaxWidth()
              .windowInsetsPadding(WindowInsets.navigationBars)
              .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
          contentAlignment = Alignment.Center
      ) {
          Box(
              modifier = Modifier
                  .widthIn(max = 480.dp)
                  .fillMaxWidth()
                  .height(64.dp)
                  .glassShadow(
                      borderRadius = 28.dp,
                      blurRadius = 18.dp,
                      offsetY = 8.dp,
                      color = Color.Black.copy(alpha = 0.30f)
                  )
                  .clip(barShape)
                  // real blur using Haze child with 12% white tint
                  .hazeChild(
                      state = hazeState,
                      shape = barShape,
                      style = HazeStyle(
                          blurRadius = 30.dp,
                          tint = Color.White.copy(alpha = 0.12f)
                      )
                  )
                  // Translucent white 12% background fill
                  .background(Color.White.copy(alpha = 0.12f), barShape)
                  // 1dp light white border
                  .border(1.dp, Color.White.copy(alpha = 0.18f), barShape)
                  .drawBehind {
                      // glossy top specular edge (gradient white .40 -> transparent over top edge)
                      drawRect(
                          brush = Brush.verticalGradient(
                              0f to Color.White.copy(alpha = 0.40f),
                              10.dp.toPx() / size.height to Color.Transparent
                          )
                      )
                  }
                  .padding(horizontal = 8.dp)
          ) {
              BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                  val totalWidth = maxWidth
                  val itemWidth = totalWidth / 5
                  val targetOffset = itemWidth * selectedIndex
                  val animatedOffset by animateDpAsState(
                      targetValue = targetOffset,
                      animationSpec = spring(
                          dampingRatio = Spring.DampingRatioLowBouncy,
                          stiffness = Spring.StiffnessLow
                      ),
                      label = "pillOffset"
                  )

                  // Sliding glass pill highlight behind selected item
                  Box(
                      modifier = Modifier
                          .offset(x = animatedOffset)
                          .width(itemWidth)
                          .fillMaxHeight()
                          .padding(horizontal = 4.dp, vertical = 6.dp)
                          .clip(CircleShape)
                          .background(Color.White.copy(alpha = 0.18f))
                          .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                  )

                  Row(
                      modifier = Modifier.fillMaxSize(),
                      horizontalArrangement = Arrangement.SpaceAround,
                      verticalAlignment = Alignment.CenterVertically
                  ) {
                      items.forEachIndexed { index, (icon, label) ->
                          NavGlassItem(
                              icon = icon,
                              label = label,
                              selected = selectedIndex == index,
                              modifier = Modifier.weight(1f)
                          ) { onItemSelected(index) }
                      }
                  }
              }
          }
      }
  }

@Composable
fun ApnaHisaabApp(expenseViewModel: ExpenseViewModel) {
    val hazeState = remember { HazeState() }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                expenseViewModel.resumeSync()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                expenseViewModel.pauseSync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Aaj.route

    val selectedIndex = when (currentRoute) {
        Screen.Aaj.route -> 0
        Screen.Hisaab.route -> 1
        Screen.Kahani.route -> 2
        Screen.Khata.route -> 3
        Screen.Settings.route, "about" -> 4
        else -> 0
    }

    // Keyboard visibility detection for global back handler
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

    androidx.activity.compose.BackHandler(enabled = (currentRoute != Screen.Aaj.route) && !isKeyboardVisible) {
        if (currentRoute == "about" || currentRoute == "admin") {
            navController.popBackStack()
        } else {
            navController.navigate(Screen.Aaj.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    com.example.ui.components.AppBackground {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = { TopBar(expenseViewModel) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .haze(state = hazeState)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Aaj.route,
                    modifier = Modifier.padding(top = innerPadding.calculateTopPadding(), bottom = 0.dp),
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Up,
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(250))
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Up,
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(250))
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(250))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(250))
                    }
                ) {
                    composable(Screen.Aaj.route) { AajScreen(expenseViewModel, navController) }
                    composable(Screen.Hisaab.route) { HisaabScreen(expenseViewModel) }
                    composable(Screen.Kahani.route) { KahaniScreen(expenseViewModel) }
                    composable(Screen.Khata.route) { KhataScreen(expenseViewModel) }
                    composable(Screen.Settings.route) { 
                        SettingsScreen(
                            viewModel = expenseViewModel,
                            onAboutClick = { navController.navigate("about") },
                            onAdminControlClick = { navController.navigate("admin") }
                        ) 
                    }
                    composable("about") {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                    composable("admin") {
                        AdminScreen(onBack = { navController.popBackStack() })
                    }
                }
            }

            val isBottomBarVisible by expenseViewModel.isBottomBarVisible.collectAsState()

            if (isBottomBarVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                ) {
                    LiquidGlassNavBar(
                        selectedIndex = selectedIndex,
                        hazeState = hazeState,
                        onItemSelected = { index ->
                            val route = when (index) {
                                0 -> Screen.Aaj.route
                                1 -> Screen.Hisaab.route
                                2 -> Screen.Kahani.route
                                3 -> Screen.Khata.route
                                4 -> Screen.Settings.route
                                else -> Screen.Aaj.route
                            }
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
    }
}

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onSplashFinished()
    }

    // Entering and breathing animations for the Logo Medallion
    val enterScale = remember { Animatable(0.8f) }
    val enterAlpha = remember { Animatable(0f) }
    var isEnterAnimationDone by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    LaunchedEffect(Unit) {
        launch {
            enterAlpha.animateTo(1f, tween(600))
        }
        enterScale.animateTo(1f, tween(600, easing = EaseOutBack))
        isEnterAnimationDone = true
    }

    val finalScale = if (isEnterAnimationDone) pulseScale else enterScale.value
    val finalAlpha = enterAlpha.value

    // Staggered entering animations for text and loading indicator
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(20f) }

    val taglineAlpha = remember { Animatable(0f) }
    val taglineOffsetY = remember { Animatable(20f) }

    val loadingAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(200)
        launch {
            titleAlpha.animateTo(1f, tween(700))
        }
        launch {
            titleOffsetY.animateTo(0f, tween(700, easing = EaseOutQuad))
        }

        delay(150)
        launch {
            taglineAlpha.animateTo(1f, tween(700))
        }
        launch {
            taglineOffsetY.animateTo(0f, tween(700, easing = EaseOutQuad))
        }

        delay(150)
        launch {
            loadingAlpha.animateTo(1f, tween(600))
        }
    }

    AppBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(Dimens.xxl)
            ) {
                // Logo Medallion with Glow Halo
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = finalScale
                            scaleY = finalScale
                            alpha = finalAlpha
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Soft purple glow halo behind the medallion
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Palette.Purple.copy(alpha = 0.20f), Color.Transparent),
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        radius = size.minDimension / 2f
                                    )
                                )
                            }
                    )

                    // Medallion Box
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Palette.SurfaceHigh, Palette.SurfaceLow)
                                )
                            )
                            .border(Dimens.border, accentBorderBrush, CircleShape)
                            .drawBehind {
                                // Subtle inner top specular highlight (from 25% white fading to transparent over the top half)
                                drawCircle(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                                        startY = 0f,
                                        endY = size.height * 0.4f
                                    ),
                                    radius = size.minDimension / 2f
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AH",
                            style = MaterialTheme.typography.displayMedium.copy(
                                color = Palette.TextPrimary,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1.5).sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.xxxl))

                // Title: Apna Hisaab
                Text(
                    text = "Apna Hisaab",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = Palette.TextPrimary
                    ),
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = titleAlpha.value
                            translationY = titleOffsetY.value
                        }
                )

                Spacer(modifier = Modifier.height(Dimens.sm))

                // Tagline: Ek line likho, baaki hum samjhe
                Text(
                    text = "Ek line likho, baaki hum samjhe",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Palette.TextSecondary
                    ),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = taglineAlpha.value
                            translationY = taglineOffsetY.value
                        }
                )

                Spacer(modifier = Modifier.height(Dimens.xxxl))

                // 3 Pulsing dots indicator
                LoadingDotsIndicator(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = loadingAlpha.value
                        }
                )
            }

            // Bottom Credit
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = "Made with ❤️ by Sadaf Siddiqui",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color = Palette.TextTertiary
                    )
                )
            }
        }
    }
}

@Composable
fun LoadingDotsIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.2f at 0
                1.0f at 300
                0.2f at 600
                0.2f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )

    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.2f at 0
                0.2f at 200
                1.0f at 500
                0.2f at 800
                0.2f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )

    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.2f at 0
                0.2f at 400
                1.0f at 700
                0.1000f at 1000 // wait, 0.1000f is same as 0.2f, let's keep it 0.2f to be consistent with the rest of keyframes
                0.2f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer { alpha = dot1Alpha }
                .background(Palette.Teal, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer { alpha = dot2Alpha }
                .background(Palette.Teal, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer { alpha = dot3Alpha }
                .background(Palette.Teal, CircleShape)
        )
    }
}
