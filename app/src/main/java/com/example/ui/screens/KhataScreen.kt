package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.KhataParty
import com.example.data.KhataTransaction
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.SectionHeader
import com.example.ui.components.brandBrush
import com.example.ui.theme.Dimens
import com.example.ui.theme.Palette
import com.example.ui.theme.Shapes
import com.example.viewmodel.ExpenseViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun KhataScreen(viewModel: ExpenseViewModel) {
    val context = LocalContext.current
    val parties by viewModel.khataParties.collectAsState()
    val transactions by viewModel.khataTransactions.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "TO_RECEIVE", "TO_PAY", "SETTLED"

    var showAddPartyDialog by remember { mutableStateOf(false) }
    var selectedPartyForDetail by remember { mutableStateOf<KhataParty?>(null) }
    var showAddTxnDialogForParty by remember { mutableStateOf<Pair<KhataParty, String>?>(null) } // Party and Type ("GAVE" or "GOT")

    // Keep selectedParty up to date with latest data from flow
    val activeParty = selectedPartyForDetail?.let { current ->
        parties.find { it.id == current.id } ?: current
    }

    // Calculations
    val totalLeneHain = parties.filter { it.netBalance > 0 }.sumOf { it.netBalance }
    val totalDeneHain = parties.filter { it.netBalance < 0 }.sumOf { abs(it.netBalance) }
    val netOverall = totalLeneHain - totalDeneHain

    val filteredParties = remember(parties, searchQuery, selectedFilter) {
        parties.filter { party ->
            val matchesSearch = party.name.contains(searchQuery, ignoreCase = true) ||
                    party.phone.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "TO_RECEIVE" -> party.netBalance > 0
                "TO_PAY" -> party.netBalance < 0
                "SETTLED" -> party.netBalance == 0.0
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.xxl),
            verticalArrangement = Arrangement.spacedBy(Dimens.lg),
            contentPadding = PaddingValues(top = Dimens.md, bottom = 120.dp)
        ) {
            // Screen Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Apna Khata",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Palette.TextPrimary
                            )
                        )
                        Text(
                            text = "Udhaar & Lena-Dena ka pakka hisaab",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Palette.TextSecondary
                            )
                        )
                    }

                    // Add Customer / Party button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.md))
                            .background(brandBrush)
                            .clickable { showAddPartyDialog = true }
                            .padding(horizontal = Dimens.md, vertical = Dimens.sm)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Khata",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Naya Khata",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Hero Summary GlassCard
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    highlight = true,
                    contentPadding = PaddingValues(Dimens.lg)
                ) {
                    // Overall status banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "KUL HISAAB (NET BALANCE)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.TextTertiary,
                                    letterSpacing = 1.sp
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = when {
                                    netOverall > 0 -> "Aapko ${currencyFormat.format(netOverall)} lene hain"
                                    netOverall < 0 -> "Aapko ${currencyFormat.format(abs(netOverall))} dene hain"
                                    else -> "Sabhi hisaab barabar hain"
                                },
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        netOverall > 0 -> Palette.Teal
                                        netOverall < 0 -> Palette.Danger
                                        else -> Palette.TextPrimary
                                    }
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Palette.Purple,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.lg))
                    HorizontalDivider(color = Palette.BorderSoft, thickness = Dimens.hairline)
                    Spacer(modifier = Modifier.height(Dimens.md))

                    // 2 Metric Columns: Lene Hain vs Dene Hain
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Lene Hain (You will get)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Palette.Teal, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Mujhe Lene Hain",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Palette.TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currencyFormat.format(totalLeneHain),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Teal
                                )
                            )
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(Palette.BorderSoft)
                        )

                        // Dene Hain (You will give)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = Dimens.md),
                            horizontalAlignment = Alignment.End
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Palette.Danger, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Mujhe Dene Hain",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Palette.TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currencyFormat.format(totalDeneHain),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Danger
                                )
                            )
                        }
                    }
                }
            }

            // Search Bar & Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    // Search text field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Shapes.md)
                            .background(Palette.SurfaceInset),
                        placeholder = {
                            Text(
                                text = "Naam ya number se search karein...",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Palette.TextTertiary)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Palette.TextSecondary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Palette.TextSecondary
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Palette.Purple,
                            unfocusedBorderColor = Palette.BorderSoft,
                            focusedTextColor = Palette.TextPrimary,
                            unfocusedTextColor = Palette.TextPrimary
                        ),
                        singleLine = true,
                        shape = Shapes.md
                    )

                    // Filter chips row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                    ) {
                        listOf(
                            "ALL" to "Sabhi (${parties.size})",
                            "TO_RECEIVE" to "Lene Hain",
                            "TO_PAY" to "Dene Hain",
                            "SETTLED" to "Barabar"
                        ).forEach { (key, label) ->
                            val isSelected = selectedFilter == key
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Dimens.sm))
                                    .background(
                                        if (isSelected) Palette.Purple.copy(alpha = 0.2f) else Palette.SurfaceInset
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Palette.Purple else Palette.BorderSoft,
                                        RoundedCornerShape(Dimens.sm)
                                    )
                                    .clickable { selectedFilter = key }
                                    .padding(horizontal = Dimens.sm, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) Palette.Purple else Palette.TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Party List Section Header
            item {
                SectionHeader(
                    title = "Khatabook Grahak / Dost (${filteredParties.size})"
                )
            }

            // Empty state
            if (filteredParties.isEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.xl),
                        contentPadding = PaddingValues(Dimens.xxl)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                        ) {
                            Text(text = "📖", fontSize = 36.sp)
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Koi khata nahi mila" else "Abhi koi khata nahi hai",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.TextPrimary
                                )
                            )
                            Text(
                                text = "Naya khata jodne ke liye upar 'Naya Khata' dabayein.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Palette.TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }
            } else {
                items(filteredParties, key = { it.id }) { party ->
                    KhataPartyItem(
                        party = party,
                        currencyFormat = currencyFormat,
                        onClick = { selectedPartyForDetail = party },
                        onQuickGave = { showAddTxnDialogForParty = party to "GAVE" },
                        onQuickGot = { showAddTxnDialogForParty = party to "GOT" }
                    )
                }
            }
        }

        // Dialog: Add New Party
        if (showAddPartyDialog) {
            AddPartyDialog(
                onDismiss = { showAddPartyDialog = false },
                onAdd = { name, phone, openingBalance, openingType ->
                    viewModel.addKhataParty(name, phone, openingBalance, openingType)
                    showAddPartyDialog = false
                }
            )
        }

        // Dialog: Add Transaction for Party (from quick buttons or detail view)
        showAddTxnDialogForParty?.let { (party, type) ->
            AddTransactionDialog(
                party = party,
                type = type,
                onDismiss = { showAddTxnDialogForParty = null },
                onConfirm = { amount, note ->
                    viewModel.addKhataTransaction(party.id, party.name, amount, type, note)
                    showAddTxnDialogForParty = null
                }
            )
        }

        // Full Party Ledger Details Dialog / BottomSheet
        activeParty?.let { party ->
            val partyTxns = transactions.filter { it.partyId == party.id }
            PartyLedgerDialog(
                party = party,
                transactions = partyTxns,
                currencyFormat = currencyFormat,
                onDismiss = { selectedPartyForDetail = null },
                onAddGave = { showAddTxnDialogForParty = party to "GAVE" },
                onAddGot = { showAddTxnDialogForParty = party to "GOT" },
                onSettle = {
                    viewModel.settleKhataParty(party.id, party.name)
                },
                onDeleteTxn = { txn ->
                    viewModel.deleteKhataTransaction(txn.id, txn.partyId, txn.amount, txn.type)
                },
                onDeleteParty = {
                    viewModel.deleteKhataParty(party.id)
                    selectedPartyForDetail = null
                }
            )
        }
    }
}

@Composable
fun KhataPartyItem(
    party: KhataParty,
    currencyFormat: NumberFormat,
    onClick: () -> Unit,
    onQuickGave: () -> Unit,
    onQuickGot: () -> Unit
) {
    val initial = party.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val balance = party.netBalance

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(Dimens.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Avatar + Details
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                // Initial Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Palette.Purple.copy(alpha = 0.4f), Palette.Teal.copy(alpha = 0.4f))
                            )
                        )
                        .border(1.dp, Palette.BorderSoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = party.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Palette.TextPrimary
                        ),
                        maxLines = 1
                    )
                    if (party.phone.isNotEmpty()) {
                        Text(
                            text = party.phone,
                            style = MaterialTheme.typography.bodySmall.copy(color = Palette.TextTertiary)
                        )
                    } else if (party.lastEntryNote.isNotEmpty()) {
                        Text(
                            text = party.lastEntryNote,
                            style = MaterialTheme.typography.bodySmall.copy(color = Palette.TextTertiary),
                            maxLines = 1
                        )
                    }
                }
            }

            // Right: Amount and Status Tag
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currencyFormat.format(abs(balance)),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            balance > 0 -> Palette.Teal
                            balance < 0 -> Palette.Danger
                            else -> Palette.TextSecondary
                        }
                    )
                )
                Text(
                    text = when {
                        balance > 0 -> "Lene hain"
                        balance < 0 -> "Dene hain"
                        else -> "Barabar"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = when {
                            balance > 0 -> Palette.Teal
                            balance < 0 -> Palette.Danger
                            else -> Palette.TextTertiary
                        }
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.sm))
        HorizontalDivider(color = Palette.BorderSoft, thickness = Dimens.hairline)
        Spacer(modifier = Modifier.height(Dimens.xs))

        // Quick action buttons for fast logging
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onQuickGave,
                colors = ButtonDefaults.textButtonColors(contentColor = Palette.Danger)
            ) {
                Icon(Icons.Default.ArrowOutward, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Maine Diye (-)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }

            Spacer(Modifier.width(4.dp))

            TextButton(
                onClick = onQuickGot,
                colors = ButtonDefaults.textButtonColors(contentColor = Palette.Teal)
            ) {
                Icon(Icons.Default.CallReceived, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Mujhe Mile (+)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
fun AddPartyDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, phone: String, openingBalance: Double, openingType: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var openingBalanceText by remember { mutableStateOf("") }
    var openingType by remember { mutableStateOf("GAVE") } // "GAVE" or "GOT"
    var errorMessage by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            highlight = true,
            contentPadding = PaddingValues(Dimens.xl)
        ) {
            Text(
                text = "Naya Khata Kholein",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Palette.TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(Dimens.md))

            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = ""
                },
                label = { Text("Grahak / Dost ka Naam *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Purple,
                    unfocusedBorderColor = Palette.BorderSoft,
                    focusedTextColor = Palette.TextPrimary,
                    unfocusedTextColor = Palette.TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(Dimens.sm))

            // Phone field
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Mobile Number (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Purple,
                    unfocusedBorderColor = Palette.BorderSoft,
                    focusedTextColor = Palette.TextPrimary,
                    unfocusedTextColor = Palette.TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(Dimens.sm))

            // Opening Balance
            OutlinedTextField(
                value = openingBalanceText,
                onValueChange = { openingBalanceText = it },
                label = { Text("Purana Udhaar / Hisaab (₹ Optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Purple,
                    unfocusedBorderColor = Palette.BorderSoft,
                    focusedTextColor = Palette.TextPrimary,
                    unfocusedTextColor = Palette.TextPrimary
                )
            )

            if (openingBalanceText.toDoubleOrNull() ?: 0.0 > 0.0) {
                Spacer(modifier = Modifier.height(Dimens.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(Shapes.sm)
                            .background(if (openingType == "GAVE") Palette.Danger.copy(alpha = 0.2f) else Palette.SurfaceInset)
                            .border(1.dp, if (openingType == "GAVE") Palette.Danger else Palette.BorderSoft, Shapes.sm)
                            .clickable { openingType = "GAVE" }
                            .padding(vertical = Dimens.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Maine Diye (Lene)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (openingType == "GAVE") Palette.Danger else Palette.TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(Shapes.sm)
                            .background(if (openingType == "GOT") Palette.Teal.copy(alpha = 0.2f) else Palette.SurfaceInset)
                            .border(1.dp, if (openingType == "GOT") Palette.Teal else Palette.BorderSoft, Shapes.sm)
                            .clickable { openingType = "GOT" }
                            .padding(vertical = Dimens.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Mujhe Mile (Dene)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (openingType == "GOT") Palette.Teal else Palette.TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.xs))
                Text(text = errorMessage, color = Palette.Danger, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(Dimens.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Palette.TextSecondary)
                }
                Spacer(modifier = Modifier.width(Dimens.sm))
                Button(
                    onClick = {
                        if (name.trim().isEmpty()) {
                            errorMessage = "Naam likhna zaruri hai!"
                        } else {
                            val balance = openingBalanceText.toDoubleOrNull() ?: 0.0
                            onAdd(name.trim(), phone.trim(), balance, openingType)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Purple)
                ) {
                    Text("Khata Kholein", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddTransactionDialog(
    party: KhataParty,
    type: String, // "GAVE" or "GOT"
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, note: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    val isGave = type == "GAVE"
    val accentColor = if (isGave) Palette.Danger else Palette.Teal
    val titleText = if (isGave) "Maine Diye (Udhaar)" else "Mujhe Mile (Jama)"

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            highlight = true,
            contentPadding = PaddingValues(Dimens.xl)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(accentColor, CircleShape)
                )
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Palette.TextPrimary
                    )
                )
            }
            Text(
                text = "${party.name} ke hisaab mein",
                style = MaterialTheme.typography.bodyMedium.copy(color = Palette.TextSecondary)
            )

            Spacer(modifier = Modifier.height(Dimens.md))

            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    errorMsg = ""
                },
                label = { Text("Rupaye (₹) *") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Palette.BorderSoft,
                    focusedTextColor = Palette.TextPrimary,
                    unfocusedTextColor = Palette.TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(Dimens.sm))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Kharche ka kaaran / Note (e.g. Chai, Bill)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Palette.BorderSoft,
                    focusedTextColor = Palette.TextPrimary,
                    unfocusedTextColor = Palette.TextPrimary
                )
            )

            if (errorMsg.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.xs))
                Text(text = errorMsg, color = Palette.Danger, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(Dimens.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Palette.TextSecondary)
                }
                Spacer(modifier = Modifier.width(Dimens.sm))
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (amt <= 0.0) {
                            errorMsg = "Sahi amount daalein!"
                        } else {
                            onConfirm(amt, note)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Save Karein", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PartyLedgerDialog(
    party: KhataParty,
    transactions: List<KhataTransaction>,
    currencyFormat: NumberFormat,
    onDismiss: () -> Unit,
    onAddGave: () -> Unit,
    onAddGot: () -> Unit,
    onSettle: () -> Unit,
    onDeleteTxn: (KhataTransaction) -> Unit,
    onDeleteParty: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("d MMM, hh:mm a", Locale("en", "IN")) }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            highlight = true,
            contentPadding = PaddingValues(Dimens.lg)
        ) {
            // Top Header: Name, Actions & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = party.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = Palette.TextPrimary
                        )
                    )
                    if (party.phone.isNotEmpty()) {
                        Text(
                            text = party.phone,
                            style = MaterialTheme.typography.bodySmall.copy(color = Palette.TextSecondary)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // WhatsApp Reminder Button
                    if (party.phone.isNotEmpty() || party.netBalance != 0.0) {
                        IconButton(onClick = {
                            val msg = if (party.netBalance > 0) {
                                "Namaste ${party.name}, aapke paas Apna Khata ka ${currencyFormat.format(party.netBalance)} udhaar baki hai. Kripya samay milte hi hisaab kar dein."
                            } else {
                                "Namaste ${party.name}, aapka Apna Khata hisaab update ho gaya hai."
                            }
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(msg) + (if (party.phone.isNotEmpty()) "&phone=${party.phone}" else ""))
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // fallback generic share
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, msg)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Khata Reminder"))
                            }
                        }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share", tint = Palette.Teal)
                        }
                    }

                    // Delete party button
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Palette.Danger)
                    }

                    // Close button
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Palette.TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.sm))

            // Current Balance Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Shapes.md)
                    .background(Palette.SurfaceInset)
                    .border(1.dp, Palette.BorderSoft, Shapes.md)
                    .padding(Dimens.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "NET HISAAB",
                            style = MaterialTheme.typography.labelSmall.copy(color = Palette.TextTertiary, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = currencyFormat.format(abs(party.netBalance)),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    party.netBalance > 0 -> Palette.Teal
                                    party.netBalance < 0 -> Palette.Danger
                                    else -> Palette.TextPrimary
                                }
                            )
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = when {
                                party.netBalance > 0 -> "Aapko Lene Hain"
                                party.netBalance < 0 -> "Aapko Dene Hain"
                                else -> "Hisaab Barabar"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    party.netBalance > 0 -> Palette.Teal
                                    party.netBalance < 0 -> Palette.Danger
                                    else -> Palette.TextSecondary
                                }
                            )
                        )

                        if (party.netBalance != 0.0) {
                            TextButton(
                                onClick = onSettle,
                                colors = ButtonDefaults.textButtonColors(contentColor = Palette.Purple),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Barabar Karein", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.md))
            Text(
                text = "Transaction History (${transactions.size})",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Palette.TextSecondary
                )
            )
            Spacer(modifier = Modifier.height(Dimens.xs))

            // Transactions LazyList
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.xs)
            ) {
                if (transactions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Dimens.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Abhi koi transaction nahi hai.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Palette.TextTertiary)
                            )
                        }
                    }
                } else {
                    items(transactions, key = { it.id }) { txn ->
                        val isGave = txn.type == "GAVE"
                        val tagColor = if (isGave) Palette.Danger else Palette.Teal

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(Shapes.sm)
                                .background(Palette.SurfaceInset)
                                .border(Dimens.hairline, Palette.BorderSoft, Shapes.sm)
                                .padding(horizontal = Dimens.md, vertical = Dimens.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = txn.note.ifEmpty { if (isGave) "Maine diye" else "Mujhe mile" },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Palette.TextPrimary
                                    )
                                )
                                Text(
                                    text = dateFormat.format(Date(txn.timestamp)),
                                    style = MaterialTheme.typography.labelSmall.copy(color = Palette.TextTertiary)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = (if (isGave) "- " else "+ ") + currencyFormat.format(txn.amount),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = tagColor
                                    )
                                )
                                IconButton(
                                    onClick = { onDeleteTxn(txn) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete",
                                        tint = Palette.TextTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.md))

            // Action Buttons at Bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                Button(
                    onClick = onAddGave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Danger),
                    shape = Shapes.md
                ) {
                    Text("Maine Diye (-)", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = onAddGot,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Teal),
                    shape = Shapes.md
                ) {
                    Text("Mujhe Mile (+)", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Khata Delete Karein?", fontWeight = FontWeight.Bold) },
            text = { Text("${party.name} ka khata aur saari transactions delete ho jayengi.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Danger),
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteParty()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
