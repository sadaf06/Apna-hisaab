package com.example.utils

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.isAutoDarkTheme
import kotlinx.coroutines.delay

// ---- Refined Glass palette — via Palette (single source) ----
private val PMint = com.example.ui.theme.Palette.Teal
private val PCoral = com.example.ui.theme.Palette.Danger
private val PText = com.example.ui.theme.Palette.TextPrimary
private val PText2 = com.example.ui.theme.Palette.TextSecondary

/** The premium slate-glass container, rounded at the top — same vocabulary as Aaj's unlock sheet. */
@androidx.compose.runtime.Composable
private fun PinSheetContainer(content: @androidx.compose.runtime.Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .clip(shape)
            .background(com.example.ui.theme.Palette.BaseTop.copy(alpha = 0.97f))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.12f), shape = shape)
            .drawBehind {
                // Luxury diagonal sheen top-left
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.4f, size.height * 0.4f)
                    )
                )
            }
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .size(40.dp, 4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f))
        )
        content()
    }
}

/** 4 dot indicators — mint when filled, glass when empty. */
@androidx.compose.runtime.Composable
private fun PinDotsRow(length: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        for (i in 0 until 4) {
            val isFilled = i < length
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isFilled) PMint else Color.White.copy(alpha = 0.08f))
                    .border(
                        width = 1.5.dp,
                        color = if (isFilled) PMint else Color.White.copy(alpha = 0.15f),
                        shape = CircleShape
                    )
            )
        }
    }
}

/** The glass keypad: 1-9, ✕ (backspace), 0, ✓ (confirm). */
@androidx.compose.runtime.Composable
private fun GlassKeypad(
    enabled: Boolean = true,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("✕", "0", "✓")
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(width = 1.dp, color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp))
                            .clickable(enabled = enabled) {
                                when (key) {
                                    "✕" -> onBackspace()
                                    "✓" -> onConfirm()
                                    else -> onDigit(key)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = when (key) {
                                    "✓" -> PMint
                                    "✕" -> PCoral
                                    else -> PText
                                },
                                fontSize = 20.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

object PinDialogHelper {

    private var wrongAttempts = 0
    private var lockoutEndTime = 0L

    private fun findActivity(context: Context): ComponentActivity? {
        var cur = context
        while (cur is ContextWrapper) {
            if (cur is ComponentActivity) {
                return cur
            }
            cur = cur.baseContext
        }
        return null
    }

    private fun setupComposeViewTreeOwners(composeView: ComposeView, context: Context) {
        val activity = findActivity(context)
        if (activity != null) {
            composeView.setViewTreeLifecycleOwner(activity)
            composeView.setViewTreeViewModelStoreOwner(activity)
            composeView.setViewTreeSavedStateRegistryOwner(activity)
        }
    }

    /** Position the dialog as a bottom sheet: full width, anchored to the bottom, dimmed scrim behind. */
    private fun presentAsSheet(dialog: Dialog) {
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.6f)
        }
    }

    fun isLockedOut(): Boolean {
        return System.currentTimeMillis() < lockoutEndTime
    }

    fun getRemainingLockoutTime(): Int {
        val diff = lockoutEndTime - System.currentTimeMillis()
        return if (diff > 0) (diff / 1000).toInt() else 0
    }

    fun showSetPinDialog(context: Context, userId: String, onSuccess: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val composeView = ComposeView(context).apply {
            setContent {
                MyApplicationTheme(mood = "khush", darkTheme = isAutoDarkTheme()) {
                    // Two-step: first enter a PIN, then confirm it.
                    var stage by remember { mutableStateOf("enter") } // "enter" | "confirm"
                    var firstPin by remember { mutableStateOf("") }
                    var pin by remember { mutableStateOf("") }
                    var errorMessage by remember { mutableStateOf<String?>(null) }

                    fun reset() {
                        stage = "enter"; firstPin = ""; pin = ""; errorMessage = null
                    }

                    fun submit() {
                        if (pin.length != 4) {
                            errorMessage = "Poora 4-digit PIN enter karein!"
                            return
                        }
                        if (stage == "enter") {
                            firstPin = pin
                            pin = ""
                            errorMessage = null
                            stage = "confirm"
                        } else {
                            if (pin == firstPin) {
                                IncomeVisibilityManager.setPin(userId, pin)
                                PremiumToast.show(context, "PIN set ho gaya! 🔒")
                                dialog.dismiss()
                                onSuccess()
                            } else {
                                errorMessage = "Dono PIN match nahi kar rahe! Firse try karein."
                                reset()
                            }
                        }
                    }

                    PinSheetContainer {
                        Text(
                            text = "🔒 Naya PIN Set Karein",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                            color = PText,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (stage == "enter")
                                "Apni kamaai chupaane ke liye 4-digit PIN banao."
                            else
                                "Confirm karne ke liye wahi PIN dobara daalein.",
                            style = MaterialTheme.typography.bodySmall.copy(color = PText2, textAlign = TextAlign.Center),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        PinDotsRow(length = pin.length)

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = PCoral,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        GlassKeypad(
                            onDigit = { d ->
                                if (pin.length < 4) {
                                    pin += d
                                    errorMessage = null
                                    if (pin.length == 4) submit()
                                }
                            },
                            onBackspace = {
                                if (pin.isNotEmpty()) { pin = pin.dropLast(1); errorMessage = null }
                            },
                            onConfirm = { submit() }
                        )

                        TextButton(onClick = { dialog.dismiss() }) {
                            Text("Cancel", color = PText2, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        setupComposeViewTreeOwners(composeView, context)
        dialog.setContentView(composeView)
        presentAsSheet(dialog)
        dialog.show()
    }

    fun showEnterPinDialog(context: Context, onSuccess: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val composeView = ComposeView(context).apply {
            setContent {
                MyApplicationTheme(mood = "khush", darkTheme = isAutoDarkTheme()) {
                    var pin by remember { mutableStateOf("") }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    var remainingSeconds by remember { mutableStateOf(getRemainingLockoutTime()) }

                    val isLocked = remainingSeconds > 0

                    if (isLocked) {
                        LaunchedEffect(Unit) {
                            while (remainingSeconds > 0) {
                                delay(1000)
                                remainingSeconds = getRemainingLockoutTime()
                            }
                        }
                    }

                    fun attempt() {
                        if (pin.length != 4) {
                            errorMessage = "Poora 4-digit PIN enter karein!"
                            return
                        }
                        if (IncomeVisibilityManager.verifyPin(pin)) {
                            wrongAttempts = 0
                            dialog.dismiss()
                            onSuccess()
                        } else {
                            wrongAttempts++
                            val left = 3 - wrongAttempts
                            if (left <= 0) {
                                lockoutEndTime = System.currentTimeMillis() + 30000
                                remainingSeconds = 30
                                pin = ""
                                errorMessage = "Lockout ho gaya! 30 seconds wait karein."
                            } else {
                                errorMessage = "Galat PIN! $left koshishein bachi hain."
                                pin = ""
                            }
                        }
                    }

                    PinSheetContainer {
                        Text(
                            text = if (isLocked) "🔒 Locked Out" else "🔓 PIN Enter Karein",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                            color = if (isLocked) PCoral else PText,
                            textAlign = TextAlign.Center
                        )

                        if (isLocked) {
                            Text(
                                text = "Bahut zyada galat PIN attempts! Aap $remainingSeconds seconds ke liye locked out hain.",
                                style = MaterialTheme.typography.bodySmall.copy(color = PCoral, fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        } else {
                            Text(
                                text = "Apni kamaai dekhne ke liye 4-digit PIN daalo.",
                                style = MaterialTheme.typography.bodySmall.copy(color = PText2),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            PinDotsRow(length = pin.length)

                            if (errorMessage != null) {
                                Text(
                                    text = errorMessage!!,
                                    color = PCoral,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            GlassKeypad(
                                enabled = !isLocked,
                                onDigit = { d ->
                                    if (pin.length < 4) {
                                        pin += d
                                        errorMessage = null
                                        if (pin.length == 4) attempt()
                                    }
                                },
                                onBackspace = {
                                    if (pin.isNotEmpty()) { pin = pin.dropLast(1); errorMessage = null }
                                },
                                onConfirm = { attempt() }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { dialog.dismiss() }) {
                                Text("Cancel", color = PText2, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = {
                                    dialog.dismiss()
                                    showResetConfirmDialog(context)
                                }
                            ) {
                                Text(
                                    text = "PIN bhool gaye? 🔄",
                                    color = PMint,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        setupComposeViewTreeOwners(composeView, context)
        dialog.setContentView(composeView)
        presentAsSheet(dialog)
        dialog.show()
    }

    private fun showResetConfirmDialog(context: Context) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val composeView = ComposeView(context).apply {
            setContent {
                MyApplicationTheme(mood = "khush", darkTheme = isAutoDarkTheme()) {
                    var confirmationInput by remember { mutableStateOf("") }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    var isLoading by remember { mutableStateOf(false) }
                    val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    val isPasswordLogin = user?.providerData?.any { it.providerId == "password" } == true

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = com.example.ui.theme.Palette.OnAccent,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "🔄 Reset PIN?",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                color = PCoral,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Kya aap apna Income PIN reset karna chahte hain? Suraksha ke liye, apni pehchan confirm karein.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PText2,
                                textAlign = TextAlign.Center
                            )

                            OutlinedTextField(
                                value = confirmationInput,
                                onValueChange = {
                                    confirmationInput = it
                                    errorMessage = null
                                },
                                label = {
                                    Text(
                                        if (isPasswordLogin) "Apna Account Password Daalein"
                                        else "Confirm karne ke liye apna email daalein"
                                    )
                                },
                                placeholder = {
                                    Text(
                                        if (isPasswordLogin) "Password"
                                        else user?.email ?: "email@example.com"
                                    )
                                },
                                visualTransformation = if (isPasswordLogin) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = if (isPasswordLogin) androidx.compose.ui.text.input.KeyboardType.Password else androidx.compose.ui.text.input.KeyboardType.Email
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = errorMessage != null,
                                enabled = !isLoading
                            )

                            if (errorMessage != null) {
                                Text(
                                    text = errorMessage!!,
                                    color = PCoral,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextButton(
                                    onClick = { dialog.dismiss() },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isLoading
                                ) {
                                    Text("Cancel", fontWeight = FontWeight.Bold, color = PText2)
                                }

                                Button(
                                    onClick = {
                                        if (user == null) {
                                            PremiumToast.show(context, "Log in karna zaroori hai!")
                                            return@Button
                                        }

                                        if (isPasswordLogin) {
                                            if (confirmationInput.isEmpty()) {
                                                errorMessage = "Password daalna zaroori hai!"
                                                return@Button
                                            }
                                            isLoading = true
                                            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(user.email!!, confirmationInput)
                                            user.reauthenticate(credential).addOnCompleteListener { task ->
                                                isLoading = false
                                                if (task.isSuccessful) {
                                                    IncomeVisibilityManager.clearPin(user.uid)
                                                    PremiumToast.show(context, "PIN reset ho gaya! Naya PIN set karein.", longDuration = true)
                                                    dialog.dismiss()
                                                } else {
                                                    errorMessage = "Galat Password! Koshish karein firse."
                                                }
                                            }
                                        } else {
                                            val inputEmail = confirmationInput.trim().lowercase()
                                            val userEmail = user.email?.trim()?.lowercase() ?: ""
                                            if (inputEmail != userEmail || inputEmail.isEmpty()) {
                                                errorMessage = "Galat Email! Apna registered email daalein."
                                                return@Button
                                            }
                                            IncomeVisibilityManager.clearPin(user.uid)
                                            PremiumToast.show(context, "PIN reset ho gaya! Naya PIN set karein.", longDuration = true)
                                            dialog.dismiss()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PCoral),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.5f),
                                    enabled = !isLoading
                                ) {
                                    if (isLoading) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = com.example.ui.theme.Palette.OnAccent,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Reset Karein ➔", fontWeight = FontWeight.Bold, color = com.example.ui.theme.Palette.OnAccent)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        setupComposeViewTreeOwners(composeView, context)
        dialog.setContentView(composeView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}
