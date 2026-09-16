package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Expense
import com.example.data.DiaryEntry
import com.example.data.ExpenseRepository
import com.example.api.GeminiAnalysisResult
import com.example.api.GeminiExpenseItem
import com.example.utils.ConnectivityObserver
import com.example.utils.ConnectivityState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ExpenseRepository
    private val sharedPrefs = application.getSharedPreferences("apna_hisaab_prefs", android.content.Context.MODE_PRIVATE)

    private val syncEntriesMutex = kotlinx.coroutines.sync.Mutex()
    private val fetchEntriesMutex = kotlinx.coroutines.sync.Mutex()
    private val syncingEntryIds = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val _currentUserId = MutableStateFlow(firebaseAuth.currentUser?.uid ?: "anonymous")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    enum class ParsingState { IDLE, PARSING, FAILED_MANUAL_FALLBACK_REQUIRED, SUCCESS_AUTO_SAVED }
    
    private val _parsingState = MutableStateFlow(ParsingState.IDLE)
    val parsingState: StateFlow<ParsingState> = _parsingState.asStateFlow()

    private val _aiParsingError = MutableStateFlow<String?>(null)
    val aiParsingError: StateFlow<String?> = _aiParsingError.asStateFlow()

    private val _monthlySummaries = MutableStateFlow<List<MonthSummary>>(emptyList())
    val monthlySummaries: StateFlow<List<MonthSummary>> = _monthlySummaries.asStateFlow()

    private val _monthDetails = MutableStateFlow<Map<String, MonthDetail>>(emptyMap()) // key "year-month"
    val monthDetails: StateFlow<Map<String, MonthDetail>> = _monthDetails.asStateFlow()

    private val _loadingMonthKey = MutableStateFlow<String?>(null)
    val loadingMonthKey: StateFlow<String?> = _loadingMonthKey.asStateFlow()

    private var hasLoadedMonthlySummaries = false
    // raw docs per "year-month" cached from the summaries query so tapping a month builds detail WITHOUT a second Firestore read
    private var monthDocsCache: Map<String, List<com.google.firebase.firestore.DocumentSnapshot>> = emptyMap()

    sealed class AiActionEvent {
        data class ShowToast(val message: String) : AiActionEvent()
        data class NavigateToSettings(val actionType: String) : AiActionEvent()
        data class ShowGoalCelebration(val goal: com.example.data.Goal) : AiActionEvent()
    }

    private val _aiActionEvents = kotlinx.coroutines.flow.MutableSharedFlow<AiActionEvent>()
    val aiActionEvents = _aiActionEvents.asSharedFlow()

    val showEditNameDialog = MutableStateFlow(false)
    val showPasswordResetConfirmDialog = MutableStateFlow(false)
    val showLogoutConfirmDialog = MutableStateFlow(false)

    fun clearAiParsingError() {
        _aiParsingError.value = null
    }

    fun resetParsingState() {
        _parsingState.value = ParsingState.IDLE
        _aiParsingError.value = null
    }

    // ... (rest of methods)

    private val connectivityObserver = ConnectivityObserver(application)
    val isOnline: StateFlow<ConnectivityState> = connectivityObserver.observe().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ConnectivityState.UNAVAILABLE
    )

    val allDiaryEntries: StateFlow<List<DiaryEntry>> = _currentUserId.flatMapLatest { uid ->
        repository.getAllDiaryEntries(uid)
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val allExpenses: StateFlow<List<Expense>> = allDiaryEntries.map { entries ->
        entries.flatMap { entry ->
            com.example.api.GeminiClient.deserializeExpenses(entry.parsedExpensesJson).map { geminiExp ->
                com.example.data.Expense(
                    amount = geminiExp.amount,
                    description = geminiExp.item,
                    category = geminiExp.category,
                    timestamp = entry.timestamp,
                    isSynced = true,
                    firebaseId = ""
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _currentMood = MutableStateFlow(sharedPrefs.getString("current_mood", "khush") ?: "khush")
    val currentMood: StateFlow<String> = _currentMood.asStateFlow()

    private val _themeMode = MutableStateFlow(sharedPrefs.getString("theme_mode", "dark") ?: "dark")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    val isBottomBarVisible = MutableStateFlow(true)

    fun updateThemeMode(mode: String) {
        val cleanMode = mode.lowercase().trim()
        _themeMode.value = cleanMode
        sharedPrefs.edit().putString("theme_mode", cleanMode).apply()
    }

    fun updateMood(mood: String) {
        val cleanMood = mood.lowercase().trim()
        _currentMood.value = cleanMood
        sharedPrefs.edit().putString("current_mood", cleanMood).apply()
    }

    private val _draftStoryInput = MutableStateFlow(sharedPrefs.getString("draft_story_input", "") ?: "")
    val draftStoryInput: StateFlow<String> = _draftStoryInput.asStateFlow()

    fun updateDraftStoryInput(text: String) {
        _draftStoryInput.value = text
        sharedPrefs.edit().putString("draft_story_input", text).apply()
    }

    private val _draftAmount = MutableStateFlow(sharedPrefs.getString("draft_amount", "") ?: "")
    val draftAmount: StateFlow<String> = _draftAmount.asStateFlow()

    fun updateDraftAmount(text: String) {
        _draftAmount.value = text
        sharedPrefs.edit().putString("draft_amount", text).apply()
    }

    private val _draftDescription = MutableStateFlow(sharedPrefs.getString("draft_description", "") ?: "")
    val draftDescription: StateFlow<String> = _draftDescription.asStateFlow()

    fun updateDraftDescription(text: String) {
        _draftDescription.value = text
        sharedPrefs.edit().putString("draft_description", text).apply()
    }

    private val _draftCategory = MutableStateFlow(sharedPrefs.getString("draft_category", "") ?: "")
    val draftCategory: StateFlow<String> = _draftCategory.asStateFlow()

    fun updateDraftCategory(text: String) {
        _draftCategory.value = text
        sharedPrefs.edit().putString("draft_category", text).apply()
    }

    private val _monthlySummary = MutableStateFlow<String?>(sharedPrefs.getString("monthly_summary", null))
    val monthlySummary: StateFlow<String?> = _monthlySummary.asStateFlow()

    private val _isSummaryLoading = MutableStateFlow(false)
    val isSummaryLoading: StateFlow<Boolean> = _isSummaryLoading.asStateFlow()

    private val _summaryError = MutableStateFlow<String?>(null)
    val summaryError: StateFlow<String?> = _summaryError.asStateFlow()

    private val _monthStories = MutableStateFlow<Map<String, String>>(emptyMap()) // key "year-month" -> AI story
    val monthStories: StateFlow<Map<String, String>> = _monthStories.asStateFlow()

    private val _storyLoadingKey = MutableStateFlow<String?>(null)
    val storyLoadingKey: StateFlow<String?> = _storyLoadingKey.asStateFlow()

    private val _monthlyBudget = MutableStateFlow(sharedPrefs.getFloat("monthly_budget", 0f).toDouble())
    val monthlyBudget: StateFlow<Double> = _monthlyBudget.asStateFlow()

    private val _income = MutableStateFlow<List<com.example.data.Income>>(emptyList())
    val income: StateFlow<List<com.example.data.Income>> = _income.asStateFlow()

    private val _categoryBudgets = MutableStateFlow<Map<String, Double>>(emptyMap())
    val categoryBudgets: StateFlow<Map<String, Double>> = _categoryBudgets.asStateFlow()

    val totalIncome = _income.map { incomeList -> 
        incomeList.filter { it.type == "monthly" }.sumOf { it.amount } 
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _userName = MutableStateFlow(sharedPrefs.getString("user_name", "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow(firebaseAuth.currentUser?.email ?: "")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _loginMethod = MutableStateFlow("")
    val loginMethod: StateFlow<String> = _loginMethod.asStateFlow()

    private val isSyncingFromFirestore = MutableStateFlow(false)

    val syncStatus: StateFlow<String> = kotlinx.coroutines.flow.combine(
        isOnline,
        isSyncingFromFirestore
    ) { online, syncing ->
        if (online != ConnectivityState.AVAILABLE) {
            "🔴"
        } else if (syncing) {
            "🔄"
        } else {
            "🟢"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "🔴")

    private var entriesListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var profileListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var incomeListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var categoryBudgetsListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var goalsListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    private var isAppInForeground = false

    fun resumeSync() {
        if (isAppInForeground && entriesListenerRegistration != null) return
        isAppInForeground = true
        val uid = _currentUserId.value
        if (uid.isNotEmpty() && uid != "anonymous") {
            startRealTimeSync(uid)
        }
    }

    fun pauseSync() {
        isAppInForeground = false
        stopRealTimeSync()
    }

    private val _goals = MutableStateFlow<List<com.example.data.Goal>>(emptyList())
    val goals: StateFlow<List<com.example.data.Goal>> = _goals.asStateFlow()

    private fun getFirebaseAuthLoginMethod(): String {
        val user = firebaseAuth.currentUser ?: return "anonymous"
        for (userInfo in user.providerData) {
            when (userInfo.providerId) {
                "google.com" -> return "Google"
                "password" -> return "Email"
                "phone" -> return "Phone"
            }
        }
        return "Email"
    }

    fun updateUserName(newName: String, onComplete: (Boolean) -> Unit = {}) {
        val cleanName = newName.trim()
        _userName.value = cleanName
        sharedPrefs.edit().putString("user_name", cleanName).apply()
        
        val uid = _currentUserId.value
        if (uid.isNotEmpty() && uid != "anonymous") {
            val db = FirebaseFirestore.getInstance()
            val profile = hashMapOf(
                "name" to cleanName,
                "email" to (_userEmail.value.ifEmpty { firebaseAuth.currentUser?.email ?: "" }),
                "loginMethod" to (_loginMethod.value.ifEmpty { getFirebaseAuthLoginMethod() })
            )
            db.collection("users").document(uid).set(
                hashMapOf(
                    "profile" to profile,
                    "lastActive" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
            
            val profileSubDoc = hashMapOf(
                "name" to cleanName,
                "email" to (_userEmail.value.ifEmpty { firebaseAuth.currentUser?.email ?: "" }),
                "loginMethod" to (_loginMethod.value.ifEmpty { getFirebaseAuthLoginMethod() }),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).collection("profile").document("info").set(
                profileSubDoc,
                com.google.firebase.firestore.SetOptions.merge()
            )
            
            val nameSubDoc = hashMapOf(
                "name" to cleanName,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).collection("profile").document("name").set(
                nameSubDoc,
                com.google.firebase.firestore.SetOptions.merge()
            )
        } else {
            onComplete(true)
        }
    }

    fun fetchProfileFromFirestore(uid: String) {
        val db = FirebaseFirestore.getInstance()
        
        // Also update lastActive timestamp to mark current activity
        if (uid.isNotEmpty() && uid != "anonymous") {
            db.collection("users").document(uid).set(
                hashMapOf("lastActive" to com.google.firebase.firestore.FieldValue.serverTimestamp()),
                com.google.firebase.firestore.SetOptions.merge()
            )
        }
        
        // 1. Fetch root user doc
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && snapshot.exists()) {
                    val budget = snapshot.getDouble("monthlyBudget") ?: snapshot.getLong("monthlyBudget")?.toDouble()
                    if (budget != null) {
                        _monthlyBudget.value = budget
                        sharedPrefs.edit().putFloat("monthly_budget", budget.toFloat()).apply()
                    }
                    val profileMap = snapshot.get("profile") as? Map<String, Any>
                    if (profileMap != null) {
                        val name = profileMap["name"] as? String ?: ""
                        if (name.isNotEmpty()) {
                            _userName.value = name
                            sharedPrefs.edit().putString("user_name", name).apply()
                        }
                        val email = profileMap["email"] as? String ?: ""
                        if (email.isNotEmpty()) {
                            _userEmail.value = email
                        }
                        val method = profileMap["loginMethod"] as? String ?: ""
                        if (method.isNotEmpty()) {
                            _loginMethod.value = method
                        }
                    }
                }
            }

        // 2. Fetch users/{userId}/profile/info
        db.collection("users").document(uid).collection("profile").document("info").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && snapshot.exists()) {
                    val name = snapshot.getString("name") ?: ""
                    if (name.isNotEmpty()) {
                        _userName.value = name
                        sharedPrefs.edit().putString("user_name", name).apply()
                    }
                    val email = snapshot.getString("email") ?: ""
                    if (email.isNotEmpty()) {
                        _userEmail.value = email
                    }
                }
            }

        // 3. Fetch users/{userId}/profile/name
        db.collection("users").document(uid).collection("profile").document("name").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && snapshot.exists()) {
                    val name = snapshot.getString("name") ?: snapshot.getString("value") ?: ""
                    if (name.isNotEmpty()) {
                        _userName.value = name
                        sharedPrefs.edit().putString("user_name", name).apply()
                    }
                }
            }
    }

    fun fetchIncomeFromFirestore(uid: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).collection("income").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null) {
                    val list = snapshot.documents.map { doc ->
                        com.example.data.Income(
                            id = doc.id,
                            sourceName = doc.getString("sourceName") ?: doc.getString("source") ?: "",
                            sourceType = doc.getString("sourceType") ?: "Other",
                            amount = doc.getDouble("amount") ?: doc.getLong("amount")?.toDouble() ?: 0.0,
                            type = doc.getString("type") ?: "monthly",
                            createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )
                    }
                    _income.value = list
                    val totalAmt = list.filter { it.type == "monthly" }.sumOf { it.amount }
                    _monthlyBudget.value = totalAmt
                    sharedPrefs.edit().putFloat("monthly_budget", totalAmt.toFloat()).apply()
                    writeCurrentMonthStatSnapshot()
                }
            }
    }

    fun fetchCategoryBudgetsFromFirestore(uid: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).collection("profile").document("categoryBudgets").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && snapshot.exists()) {
                    _categoryBudgets.value = snapshot.data?.mapValues { (it.value as? Number)?.toDouble() ?: 0.0 } ?: emptyMap()
                }
            }
    }

    fun fetchGoalsFromFirestore(uid: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).collection("goals").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null) {
                    val list = snapshot.documents.map { doc ->
                        com.example.data.Goal(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            targetAmount = doc.getDouble("targetAmount") ?: doc.getLong("targetAmount")?.toDouble() ?: 0.0,
                            savedAmount = doc.getDouble("savedAmount") ?: doc.getLong("savedAmount")?.toDouble() ?: 0.0,
                            category = doc.getString("category") ?: "",
                            emoji = doc.getString("emoji") ?: "",
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            isCompleted = doc.getBoolean("isCompleted") ?: false
                        )
                    }
                    _goals.value = list.sortedByDescending { it.createdAt }
                }
            }
    }

    private fun startRealTimeSync(uid: String) {
        if (!isAppInForeground) return
        stopRealTimeSync()
        com.example.utils.IncomeVisibilityManager.loadPin(uid)
        
        fetchProfileFromFirestore(uid)
        fetchIncomeFromFirestore(uid)
        fetchCategoryBudgetsFromFirestore(uid)
        fetchGoalsFromFirestore(uid)

        val db = FirebaseFirestore.getInstance()
        isSyncingFromFirestore.value = true
        entriesListenerRegistration = db.collection("users").document(uid).collection("entries")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                isSyncingFromFirestore.value = false
                if (error != null) {
                    error.printStackTrace()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    viewModelScope.launch {
                        val remoteIds = snapshot.documents.map { it.id }.toSet()
                        
                        val currentLocal = allDiaryEntries.value
                        for (localEntry in currentLocal) {
                            if (localEntry.firebaseId.isNotEmpty() && !remoteIds.contains(localEntry.firebaseId)) {
                                repository.deleteDiaryEntryByFirebaseId(localEntry.firebaseId)
                            }
                        }

                        for (doc in snapshot.documents) {
                            val firebaseId = doc.id
                            val dateStamp = doc.getTimestamp("date")?.toDate()?.time ?: System.currentTimeMillis()
                            val originalText = doc.getString("originalText") ?: ""
                            val mood = doc.getString("mood") ?: "Normal"
                            val aiInsight = doc.getString("aiInsight") ?: ""
                            val totalAmount = doc.getDouble("totalAmount") ?: doc.getLong("totalAmount")?.toDouble() ?: 0.0
                            val expensesRaw = doc.get("expenses") as? List<Map<String, Any>> ?: emptyList()
                            val expensesList = expensesRaw.map {
                                val item = it["item"] as? String ?: ""
                                val amountStr = it["amount"]?.toString() ?: "0"
                                val amount = amountStr.toDoubleOrNull() ?: 0.0
                                val category = it["category"] as? String ?: "Baaki"
                                com.example.api.GeminiExpenseItem(item, amount, category)
                            }
                            val parsedExpensesJson = com.example.api.GeminiClient.serializeExpenses(expensesList)

                            val existing = repository.getEntryByFirebaseId(firebaseId)
                            if (existing != null) {
                                val changed = existing.originalText != originalText ||
                                              existing.parsedExpensesJson != parsedExpensesJson ||
                                              existing.totalAmount != totalAmount ||
                                              existing.mood != mood
                                if (changed) {
                                    repository.updateDiaryEntry(
                                        existing.copy(
                                            originalText = originalText,
                                            parsedExpensesJson = parsedExpensesJson,
                                            totalAmount = totalAmount,
                                            mood = mood,
                                            aiInsight = aiInsight,
                                            isSynced = true
                                        )
                                    )
                                }
                                continue
                            }

                            val duplicateByContent = repository.getEntryByContent(dateStamp, originalText)
                            if (duplicateByContent != null) {
                                if (duplicateByContent.firebaseId.isEmpty()) {
                                    repository.markSynced(duplicateByContent.id, firebaseId)
                                }
                                continue
                            }
                            repository.insertDiaryEntry(
                                DiaryEntry(
                                    timestamp = dateStamp,
                                    originalText = originalText,
                                    parsedExpensesJson = parsedExpensesJson,
                                    mood = mood,
                                    aiInsight = aiInsight,
                                    userId = uid,
                                    totalAmount = totalAmount,
                                    isSynced = true,
                                    firebaseId = firebaseId,
                                    isParsed = true
                                )
                            )
                        }
                    }
                }
            }
    }

    private fun stopRealTimeSync() {
        entriesListenerRegistration?.remove()
        entriesListenerRegistration = null
        profileListenerRegistration?.remove()
        profileListenerRegistration = null
        incomeListenerRegistration?.remove()
        incomeListenerRegistration = null
        categoryBudgetsListenerRegistration?.remove()
        categoryBudgetsListenerRegistration = null
        goalsListenerRegistration?.remove()
        goalsListenerRegistration = null
    }

    override fun onCleared() {
        super.onCleared()
        stopRealTimeSync()
    }

    fun updateMonthlyBudget(newBudget: Double) {
        _monthlyBudget.value = newBudget
        sharedPrefs.edit().putFloat("monthly_budget", newBudget.toFloat()).apply()
        writeCurrentMonthStatSnapshot()
        
        val uid = _currentUserId.value
        if (uid.isNotEmpty() && uid != "anonymous") {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(uid).set(
                hashMapOf("monthlyBudget" to newBudget),
                com.google.firebase.firestore.SetOptions.merge()
            )
        }
    }

    fun writeCurrentMonthStatSnapshot() {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return
        // ALL income however earned (monthly + one-time), not just the monthly budget
        val income = _income.value.sumOf { it.amount }.let { if (it > 0.0) it else _monthlyBudget.value }
        if (income <= 0.0) return
        val cal = java.util.Calendar.getInstance()
        val key = "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH)}"
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("monthlyStats").document(key)
            .set(hashMapOf(
                "income" to income,
                "year" to cal.get(java.util.Calendar.YEAR),
                "month" to cal.get(java.util.Calendar.MONTH),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ), com.google.firebase.firestore.SetOptions.merge())
    }

    fun addIncome(sourceName: String, sourceType: String, amount: Double, type: String) {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return
        
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).collection("income").add(
            hashMapOf(
                "sourceName" to sourceName,
                "sourceType" to sourceType,
                "amount" to amount,
                "type" to type,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener {
            fetchIncomeFromFirestore(uid)
        }
    }

    fun deleteIncome(incomeId: String) {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return
        
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).collection("income").document(incomeId).delete()
            .addOnSuccessListener {
                fetchIncomeFromFirestore(uid)
            }
    }

    fun updateCategoryBudget(category: String, budget: Double) {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return
        
        val current = _categoryBudgets.value.toMutableMap()
        current[category] = budget
        _categoryBudgets.value = current
        
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).collection("profile").document("categoryBudgets").set(
            hashMapOf(category to budget),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    private fun fetchUserProfileSettingsFromFirestore() {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return
        
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val budget = doc.getDouble("monthlyBudget") ?: doc.getLong("monthlyBudget")?.toDouble()
                        if (budget != null) {
                            _monthlyBudget.value = budget
                            sharedPrefs.edit().putFloat("monthly_budget", budget.toFloat()).apply()
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkBudgetAlert(entries: List<com.example.data.DiaryEntry>, budget: Double) {
        if (budget <= 0.0) return

        val currentCal = java.util.Calendar.getInstance()
        val thisYear = currentCal.get(java.util.Calendar.YEAR)
        val thisMonth = currentCal.get(java.util.Calendar.MONTH)
        val monthYearKey = "$thisYear-$thisMonth"

        val entryCal = java.util.Calendar.getInstance()
        
        val totalSpent = entries.filter { entry ->
            entryCal.timeInMillis = entry.timestamp
            entryCal.get(java.util.Calendar.YEAR) == thisYear && entryCal.get(java.util.Calendar.MONTH) == thisMonth
        }.flatMap { entry ->
            com.example.api.GeminiClient.deserializeExpenses(entry.parsedExpensesJson)
        }.sumOf { it.amount }

        if (totalSpent >= budget * 0.8) {
            val lastNotified = sharedPrefs.getString("last_notified_budget_month", "")
            if (lastNotified != monthYearKey) {
                com.example.DailyReminderReceiver.showBudgetAlertNotification(getApplication(), totalSpent, budget)
                sharedPrefs.edit().putString("last_notified_budget_month", monthYearKey).apply()
            }
        } else {
            val lastNotified = sharedPrefs.getString("last_notified_budget_month", "")
            if (lastNotified == monthYearKey) {
                sharedPrefs.edit().remove("last_notified_budget_month").apply()
            }
        }
    }

    init {
        // Enable Firestore offline persistence
        try {
            val db = FirebaseFirestore.getInstance()
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            db.firestoreSettings = settings
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val database = AppDatabase.getDatabase(application)
        val expenseDao = database.expenseDao()
        val diaryEntryDao = database.diaryEntryDao()
        repository = ExpenseRepository(expenseDao, diaryEntryDao)

        firebaseAuth.addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid ?: "anonymous"
            _userEmail.value = auth.currentUser?.email ?: ""
            _loginMethod.value = getFirebaseAuthLoginMethod()
            if (uid != _currentUserId.value) {
                _currentUserId.value = uid
                if (uid != "anonymous" && isAppInForeground) {
                    startRealTimeSync(uid)
                } else {
                    stopRealTimeSync()
                }
            } else if (uid != "anonymous" && entriesListenerRegistration == null && isAppInForeground) {
                startRealTimeSync(uid)
            }
        }
        
    // Observe both diary entries and budget for budget limit notification
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(allDiaryEntries, monthlyBudget) { entries, budget ->
                Pair(entries, budget)
            }.collect { (entries, budget) ->
                checkBudgetAlert(entries, budget)
            }
        }

        // Observe internet available to trigger automated syncing of local entries
        viewModelScope.launch {
            isOnline.collect { state ->
                if (state == ConnectivityState.AVAILABLE) {
                    syncEntriesToFirestore()
                }
            }
        }
    }

    private fun syncAllData() {
        viewModelScope.launch {
            syncEntriesToFirestore()
            kotlinx.coroutines.delay(1000)
            if (entriesListenerRegistration == null) {
                fetchEntriesFromFirestore()
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        stopRealTimeSync()
        firebaseAuth.signOut()
        _currentUserId.value = "anonymous"
        _userName.value = ""
        _userEmail.value = ""
        _loginMethod.value = ""
        onComplete()
    }

    fun deleteUserAccount(onComplete: (Boolean, String?) -> Unit) {
        val user = firebaseAuth.currentUser
        val uid = _currentUserId.value
        if (user == null || uid.isEmpty() || uid == "anonymous") {
            onComplete(false, "Khel khatam! Active user nahi mila.")
            return
        }
        
        val db = FirebaseFirestore.getInstance()
        stopRealTimeSync()
        
        db.collection("users").document(uid).collection("entries").get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
                
                batch.delete(db.collection("users").document(uid).collection("profile").document("info"))
                batch.delete(db.collection("users").document(uid))
                
                batch.commit().addOnCompleteListener { commitTask ->
                    viewModelScope.launch {
                        repository.deleteAllDiaryEntries(uid)
                    }
                    
                    user.delete().addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            _currentUserId.value = "anonymous"
                            _userName.value = ""
                            _userEmail.value = ""
                            _loginMethod.value = ""
                            sharedPrefs.edit().clear().apply()
                            onComplete(true, null)
                        } else {
                            onComplete(false, authTask.exception?.localizedMessage ?: "Security protocol: Please delete account immediately after logging in again.")
                        }
                    }
                }
            }
            .addOnFailureListener { error ->
                onComplete(false, error.localizedMessage ?: "Firestore deletion failed.")
            }
    }

    fun syncEntriesToFirestore() {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return
        if (isOnline.value != ConnectivityState.AVAILABLE) return

        viewModelScope.launch {
            if (syncEntriesMutex.isLocked) return@launch
            syncEntriesMutex.withLock {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val unsyncedList = repository.getUnsyncedDiaryEntries(uid)
                    for (entry in unsyncedList) {
                        // Skip if addDiaryEntry is already uploading this one
                        if (syncingEntryIds.contains(entry.id)) continue
                        
                        syncingEntryIds.add(entry.id)
                        try {
                            val expensesList = com.example.api.GeminiClient.deserializeExpenses(entry.parsedExpensesJson)
                            
                            val data = hashMapOf(
                                "date" to com.google.firebase.Timestamp(java.util.Date(entry.timestamp)),
                                "originalText" to entry.originalText,
                                "expenses" to expensesList.map { mapOf("item" to it.item, "amount" to it.amount, "category" to it.category) },
                                "mood" to entry.mood,
                                "aiInsight" to entry.aiInsight,
                                "totalAmount" to entry.totalAmount,
                                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                            
                            val documentRef = if (entry.firebaseId.isNotEmpty()) {
                                db.collection("users").document(uid).collection("entries").document(entry.firebaseId)
                            } else {
                                db.collection("users").document(uid).collection("entries").document()
                            }
                            
                            val docId = documentRef.id
                            documentRef.set(data).addOnSuccessListener {
                                viewModelScope.launch {
                                    repository.markSynced(entry.id, docId)
                                    syncingEntryIds.remove(entry.id)
                                }
                            }.addOnFailureListener {
                                it.printStackTrace()
                                syncingEntryIds.remove(entry.id)
                            }
                        } catch (e: Exception) {
                            syncingEntryIds.remove(entry.id)
                            throw e
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun fetchEntriesFromFirestore() {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return
        
        android.util.Log.d("KahaniFix", "fetchEntriesFromFirestore called for user: $uid")

        viewModelScope.launch {
            if (fetchEntriesMutex.isLocked) return@launch
            fetchEntriesMutex.withLock {
                android.util.Log.d("KahaniFix", "Entering fetchEntriesFromFirestore lock")
                try {
                    val db = FirebaseFirestore.getInstance()
                    db.collection("users").document(uid).collection("entries")
                        .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit(30)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            android.util.Log.d("KahaniFix", "Snapshot received with ${snapshot.size()} documents")
                            viewModelScope.launch {
                                for (doc in snapshot.documents) {
                                    val firebaseId = doc.id
                                    val dateStamp = doc.getTimestamp("date")?.toDate()?.time ?: System.currentTimeMillis()
                                    val originalText = doc.getString("originalText") ?: ""
                                    val mood = doc.getString("mood") ?: "Normal"
                                    val aiInsight = doc.getString("aiInsight") ?: ""
                                    val totalAmount = doc.getDouble("totalAmount") ?: doc.getLong("totalAmount")?.toDouble() ?: 0.0
                                    
                                    val expensesRaw = doc.get("expenses") as? List<Map<String, Any>> ?: emptyList()
                                    val expensesList = expensesRaw.map {
                                        val item = it["item"] as? String ?: ""
                                        val amountStr = it["amount"]?.toString() ?: "0"
                                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                                        val category = it["category"] as? String ?: "Baaki"
                                        
                                        com.example.api.GeminiExpenseItem(item, amount, category)
                                    }
                                    val parsedExpensesJson = com.example.api.GeminiClient.serializeExpenses(expensesList)

                                    val existing = repository.getEntryByFirebaseId(firebaseId)
                                    if (existing != null) {
                                        val changed = existing.originalText != originalText ||
                                                      existing.parsedExpensesJson != parsedExpensesJson ||
                                                      existing.totalAmount != totalAmount ||
                                                      existing.mood != mood
                                        if (changed) {
                                            repository.updateDiaryEntry(
                                                existing.copy(
                                                    originalText = originalText,
                                                    parsedExpensesJson = parsedExpensesJson,
                                                    totalAmount = totalAmount,
                                                    mood = mood,
                                                    aiInsight = aiInsight,
                                                    isSynced = true
                                                )
                                            )
                                        }
                                        continue
                                    }
                                    
                                    val duplicateByContent = repository.getEntryByContent(dateStamp, originalText)
                                    if (duplicateByContent != null) {
                                        if (duplicateByContent.firebaseId.isEmpty()) {
                                            repository.markSynced(duplicateByContent.id, firebaseId)
                                        }
                                        continue
                                    }
                                    
                                    repository.insertDiaryEntry(
                                        DiaryEntry(
                                            timestamp = dateStamp,
                                            originalText = originalText,
                                            parsedExpensesJson = parsedExpensesJson,
                                            mood = mood,
                                            aiInsight = aiInsight,
                                            userId = uid,
                                            totalAmount = totalAmount,
                                            isSynced = true,
                                            firebaseId = firebaseId
                                        )
                                    )
                                }
                            }
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }


    var pendingOriginalText = ""
    var pendingMood = ""

    fun startParsingEntry(originalText: String, mood: String) {
        viewModelScope.launch {
            pendingOriginalText = originalText
            pendingMood = mood
            
            try {
                _parsingState.value = ParsingState.PARSING
                
                val result = com.example.api.GeminiClient.parseStory(originalText, mood)
                
                if (result.actions.isEmpty()) {
                    _parsingState.value = ParsingState.IDLE
                    _aiActionEvents.emit(AiActionEvent.ShowToast(result.toastMessage ?: "Samajh nahi aaya, dobara likho 🤷‍♂️"))
                    return@launch
                }

                var moodForEntry = mood
                val allExpenses = mutableListOf<com.example.api.GeminiExpenseItem>()
                
                // First pass: collect all expenses and find the overall mood
                result.actions.forEach { action ->
                    if (action.mood != null) moodForEntry = action.mood
                    if (action.intent.uppercase() == "EXPENSE") {
                        allExpenses.addAll(action.expenses)
                    }
                }
                
                // Process each action
                result.actions.forEach { action ->
                    val intentStr = action.intent.uppercase().trim()
                    when (intentStr) {
                        "SAVING" -> {
                            val amount = action.amount
                            if (amount > 0.0) {
                                handleSavingAction(amount, action.goalKeyword)
                            }
                        }
                        "GOAL_COMPLETE" -> {
                            handleGoalCompleteAction(action.goalKeyword)
                        }
                        "APP_ACTION" -> {
                            handleAppAction(action.appAction)
                        }
                    }
                }

                // If there are expenses, save them as one combined entry
                if (allExpenses.isNotEmpty()) {
                    saveCombinedExpenseEntry(allExpenses, moodForEntry, result.ai_insight)
                } else {
                    _parsingState.value = ParsingState.IDLE
                }

                val summaryToast = result.toastMessage ?: "Actions complete!"
                _aiActionEvents.emit(AiActionEvent.ShowToast(summaryToast))
                
            } catch (e: Exception) {
                e.printStackTrace()
                _parsingState.value = ParsingState.IDLE
                _aiParsingError.value = "Kucch gadbad ho gayi: ${e.message}"
            }
        }
    }

    private suspend fun saveCombinedExpenseEntry(
        expenses: List<com.example.api.GeminiExpenseItem>,
        mood: String,
        aiInsight: String
    ) {
        val uid = _currentUserId.value
        val total = expenses.sumOf { it.amount }
        val serializedExpenses = com.example.api.GeminiClient.serializeExpenses(expenses)
        
        val localEntry = DiaryEntry(
            originalText = pendingOriginalText,
            parsedExpensesJson = serializedExpenses,
            mood = mood,
            aiInsight = aiInsight,
            userId = uid,
            isSynced = false,
            totalAmount = total,
            isParsed = true
        )
        
        val localId = repository.insertDiaryEntry(localEntry)
        
        if (uid.isNotEmpty() && uid != "anonymous" && isOnline.value == ConnectivityState.AVAILABLE) {
            try {
                val db = FirebaseFirestore.getInstance()
                val docRef = db.collection("users").document(uid).collection("entries").document()
                val docId = docRef.id
                
                val expensesList = expenses.map {
                    mapOf("item" to it.item, "amount" to it.amount, "category" to it.category)
                }

                val data = hashMapOf(
                    "date" to com.google.firebase.Timestamp(java.util.Date(localEntry.timestamp)),
                    "originalText" to pendingOriginalText,
                    "expenses" to expensesList,
                    "mood" to localEntry.mood,
                    "aiInsight" to localEntry.aiInsight,
                    "totalAmount" to total,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                
                syncingEntryIds.add(localId.toInt())
                docRef.set(data).addOnSuccessListener {
                    viewModelScope.launch {
                        repository.markSynced(localId.toInt(), docId)
                        syncingEntryIds.remove(localId.toInt())
                    }
                }.addOnFailureListener { syncingEntryIds.remove(localId.toInt()) }
            } catch (e: Exception) { e.printStackTrace() }
        }
        _parsingState.value = ParsingState.SUCCESS_AUTO_SAVED
    }

    private suspend fun handleSavingAction(amount: Double, keyword: String) {
        val activeGoals = _goals.value.filter { !it.isCompleted }
        val goalKey = keyword.trim().lowercase()
        
        val targetGoal = if (goalKey.isNotEmpty()) {
            activeGoals.find { it.name.lowercase().contains(goalKey) || it.category.lowercase().contains(goalKey) }
        } else null
        
        if (activeGoals.isEmpty()) {
            val uid = _currentUserId.value
            if (uid.isNotEmpty() && uid != "anonymous") {
                val db = FirebaseFirestore.getInstance()
                val newGoalRef = db.collection("users").document(uid).collection("goals").document()
                val goalData = hashMapOf(
                    "name" to "Other Saving",
                    "targetAmount" to 10000.0,
                    "savedAmount" to amount,
                    "category" to "Savings",
                    "emoji" to "💰",
                    "createdAt" to System.currentTimeMillis(),
                    "isCompleted" to (amount >= 10000.0)
                )
                newGoalRef.set(goalData).addOnSuccessListener { fetchGoalsFromFirestore(uid) }
            }
        } else {
            val finalGoal = targetGoal ?: activeGoals.first()
            val currentSaved = finalGoal.savedAmount
            val targetVal = finalGoal.targetAmount
            addSavingsToGoal(finalGoal.id, amount, currentSaved, targetVal)
            if ((currentSaved + amount) >= targetVal) {
                _aiActionEvents.emit(AiActionEvent.ShowGoalCelebration(finalGoal.copy(savedAmount = currentSaved + amount, isCompleted = true)))
            }
        }
    }

    private suspend fun handleGoalCompleteAction(keyword: String) {
        val activeGoals = _goals.value.filter { !it.isCompleted }
        val goalKey = keyword.trim().lowercase()
        val targetGoal = if (goalKey.isNotEmpty()) {
            activeGoals.find { it.name.lowercase().contains(goalKey) || it.category.lowercase().contains(goalKey) }
        } else null
        
        if (targetGoal != null) {
            completeGoal(targetGoal.id, targetGoal.targetAmount)
            _aiActionEvents.emit(AiActionEvent.ShowGoalCelebration(targetGoal.copy(savedAmount = targetGoal.targetAmount, isCompleted = true)))
        }
    }

    private suspend fun handleAppAction(appAction: String) {
        when (appAction.uppercase().trim()) {
            "RESET_PASSWORD" -> {
                showPasswordResetConfirmDialog.value = true
                _aiActionEvents.emit(AiActionEvent.NavigateToSettings("RESET_PASSWORD"))
            }
            "EDIT_NAME" -> {
                showEditNameDialog.value = true
                _aiActionEvents.emit(AiActionEvent.NavigateToSettings("EDIT_NAME"))
            }
            "LOGOUT" -> {
                showLogoutConfirmDialog.value = true
                _aiActionEvents.emit(AiActionEvent.NavigateToSettings("LOGOUT"))
            }
        }
    }
    
    fun addPreParsedDiaryEntry(originalText: String, parsedExpensesJson: String, mood: String, aiInsight: String) {
        viewModelScope.launch {
            val uid = _currentUserId.value
            val expensesList = com.example.api.GeminiClient.deserializeExpenses(parsedExpensesJson)
            val total = expensesList.sumOf { it.amount }
            
            val localEntry = DiaryEntry(
                originalText = originalText,
                parsedExpensesJson = parsedExpensesJson,
                mood = mood,
                aiInsight = aiInsight,
                userId = uid,
                isSynced = false,
                totalAmount = total,
                isParsed = true
            )
            
            repository.insertDiaryEntry(localEntry)
        }
    }

    fun deleteDiaryEntry(id: Int, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            // First locate firebaseId of deleted entry if any to delete from firestore
            // Let's get entries list from allDiaryEntries
            val entryToDelete = allDiaryEntries.value.find { it.id == id }
            if (entryToDelete == null) {
                onComplete(false)
                return@launch
            }
            
            val uid = _currentUserId.value
            if (uid.isNotEmpty() && uid != "anonymous" && entryToDelete.firebaseId.isNotEmpty()) {
                val db = FirebaseFirestore.getInstance()
                db.collection("users")
                    .document(uid)
                    .collection("entries")
                    .document(entryToDelete.firebaseId)
                    .delete()
                    .addOnSuccessListener {
                        viewModelScope.launch {
                            repository.deleteDiaryEntry(id)
                            onComplete(true)
                        }
                    }
                    .addOnFailureListener {
                        onComplete(false)
                    }
            } else {
                repository.deleteDiaryEntry(id)
                onComplete(true)
            }
        }
    }

    fun fetchMonthlySummary(entriesText: String) {
        viewModelScope.launch {
            _isSummaryLoading.value = true
            _summaryError.value = null
            try {
                val result = com.example.api.GeminiClient.getMonthlySummary(entriesText)
                _monthlySummary.value = result
                sharedPrefs.edit().putString("monthly_summary", result).apply()
            } catch (e: Exception) {
                _summaryError.value = e.localizedMessage ?: "Summary load nahi ho payi, baad me try karein."
            } finally {
                _isSummaryLoading.value = false
            }
        }
    }

    fun generateMonthStory(year: Int, month: Int, entriesText: String) {
        val key = "$year-$month"
        if (_monthStories.value.containsKey(key)) return   // already generated, cached
        if (entriesText.isBlank()) return
        _storyLoadingKey.value = key
        viewModelScope.launch {
            try {
                val result = com.example.api.GeminiClient.getMonthlySummary(entriesText)
                _monthStories.value = _monthStories.value.toMutableMap().apply { put(key, result) }
            } catch (e: Exception) {
                e.printStackTrace()
                com.example.utils.PremiumToast.show(getApplication(), "Kahani nahi ban paayi, dobara try karein.")
            } finally {
                _storyLoadingKey.value = null
            }
        }
    }

    fun addGoal(name: String, targetAmount: Double, category: String, emoji: String) {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return

        val db = FirebaseFirestore.getInstance()
        val newGoalRef = db.collection("users").document(uid).collection("goals").document()
        val goalData = hashMapOf(
            "name" to name,
            "targetAmount" to targetAmount,
            "savedAmount" to 0.0,
            "category" to category,
            "emoji" to emoji,
            "createdAt" to System.currentTimeMillis(),
            "isCompleted" to false
        )
        newGoalRef.set(goalData).addOnSuccessListener {
            fetchGoalsFromFirestore(uid)
        }
    }

    fun updateGoal(goalId: String, name: String, targetAmount: Double, emoji: String, savedAmount: Double? = null, isCompleted: Boolean? = null) {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return

        val db = FirebaseFirestore.getInstance()
        val updates = hashMapOf<String, Any>(
            "name" to name,
            "targetAmount" to targetAmount,
            "emoji" to emoji
        )
        if (savedAmount != null) {
            updates["savedAmount"] = savedAmount
        }
        if (isCompleted != null) {
            updates["isCompleted"] = isCompleted
        }

        db.collection("users").document(uid).collection("goals").document(goalId).update(updates)
            .addOnSuccessListener {
                fetchGoalsFromFirestore(uid)
            }
    }

    fun completeGoal(goalId: String, targetAmount: Double) {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return

        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).collection("goals").document(goalId).update(
            "savedAmount", targetAmount,
            "isCompleted", true
        ).addOnSuccessListener {
            fetchGoalsFromFirestore(uid)
        }
    }

    fun addSavingsToGoal(goalId: String, amountToAdd: Double, currentSaved: Double, targetAmount: Double) {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return

        val newSavedAmount = currentSaved + amountToAdd
        val isCompleted = newSavedAmount >= targetAmount

        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).collection("goals").document(goalId).update(
            "savedAmount", newSavedAmount,
            "isCompleted", isCompleted
        ).addOnSuccessListener {
            fetchGoalsFromFirestore(uid)
        }
    }

    fun deleteGoal(goalId: String) {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return

        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).collection("goals").document(goalId).delete()
            .addOnSuccessListener {
                fetchGoalsFromFirestore(uid)
            }
    }

    fun updateExpenseItem(
        entryId: Int,
        itemIndex: Int,
        newItem: String,
        newAmount: Double,
        newCategory: String,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val entry = allDiaryEntries.value.find { it.id == entryId }
                ?: return@launch onComplete(false)
            val parsed = com.example.api.GeminiClient
                .deserializeExpenses(entry.parsedExpensesJson).toMutableList()
            if (itemIndex !in parsed.indices) return@launch onComplete(false)
            parsed[itemIndex] = parsed[itemIndex].copy(
                item = newItem, amount = newAmount, category = newCategory
            )
            val newJson = com.example.api.GeminiClient.serializeExpenses(parsed)
            val newTotal = parsed.sumOf { it.amount }
            // Room (REPLACE upsert by same id)
            repository.insertDiaryEntry(
                entry.copy(parsedExpensesJson = newJson, totalAmount = newTotal, isSynced = false)
            )
            // Firestore
            val uid = _currentUserId.value
            if (uid.isNotEmpty() && uid != "anonymous" && entry.firebaseId.isNotEmpty()) {
                val expensesList = parsed.map {
                    mapOf("item" to it.item, "amount" to it.amount, "category" to it.category)
                }
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("entries").document(entry.firebaseId)
                    .update(mapOf("expenses" to expensesList, "totalAmount" to newTotal))
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { onComplete(false) }
            } else {
                onComplete(true)
            }
        }
    }

    fun updateDiaryEntry(
        id: Int,
        newOriginalText: String,
        newExpenses: List<com.example.api.GeminiExpenseItem>,
        newMood: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val entry = allDiaryEntries.value.find { it.id == id } ?: return@launch onComplete(false)
            val parsedExpensesJson = com.example.api.GeminiClient.serializeExpenses(newExpenses)
            val totalAmount = newExpenses.sumOf { it.amount }
            
            val updated = entry.copy(
                id = entry.id,
                firebaseId = entry.firebaseId,
                originalText = newOriginalText,
                parsedExpensesJson = parsedExpensesJson,
                totalAmount = totalAmount,
                mood = newMood,
                isSynced = false
            )
            
            repository.updateDiaryEntry(updated)
            
            val uid = _currentUserId.value
            val firebaseId = entry.firebaseId
            val online = isOnline.value == ConnectivityState.AVAILABLE
            
            if (uid.isNotEmpty() && uid != "anonymous" && firebaseId.isNotEmpty() && online) {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val data = hashMapOf(
                        "date" to com.google.firebase.Timestamp(java.util.Date(entry.timestamp)),
                        "originalText" to newOriginalText,
                        "expenses" to newExpenses.map { mapOf("item" to it.item, "amount" to it.amount, "category" to it.category) },
                        "mood" to newMood,
                        "aiInsight" to entry.aiInsight,
                        "totalAmount" to totalAmount,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                    db.collection("users").document(uid).collection("entries").document(firebaseId)
                        .set(data)
                        .addOnSuccessListener {
                            viewModelScope.launch {
                                repository.markSynced(id, firebaseId)
                                onComplete(true)
                            }
                        }
                        .addOnFailureListener {
                            it.printStackTrace()
                            onComplete(false)
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                    onComplete(false)
                }
            } else {
                onComplete(true)
            }
        }
    }

    fun updateDiaryEntryWithAi(
        id: Int,
        editedText: String,
        selectedMood: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val existing = allDiaryEntries.value.find { it.id == id }
            if (existing == null) {
                onComplete(false)
                return@launch
            }
            try {
                val result = com.example.api.GeminiClient.parseStory(editedText, selectedMood)
                val expenseAction = result.actions.find { it.intent.uppercase() == "EXPENSE" }
                
                if (expenseAction == null) {
                    onComplete(false)
                    return@launch
                }

                val newJson = com.example.api.GeminiClient.serializeExpenses(expenseAction.expenses)
                val newTotal = expenseAction.expenses.sumOf { it.amount }
                val moodValue = expenseAction.mood ?: selectedMood
                val aiInsightValue = result.ai_insight ?: ""

                val updated = existing.copy(
                    originalText = editedText,
                    mood = moodValue,
                    parsedExpensesJson = newJson,
                    totalAmount = newTotal,
                    aiInsight = aiInsightValue,
                    isSynced = false
                )

                repository.updateDiaryEntry(updated)

                val uid = _currentUserId.value
                val firebaseId = existing.firebaseId
                val online = isOnline.value == ConnectivityState.AVAILABLE

                if (uid.isNotEmpty() && uid != "anonymous" && firebaseId.isNotEmpty() && online) {
                    try {
                        val db = FirebaseFirestore.getInstance()
                        val data = hashMapOf(
                            "date" to com.google.firebase.Timestamp(java.util.Date(existing.timestamp)),
                            "originalText" to editedText,
                            "expenses" to expenseAction.expenses.map { mapOf("item" to it.item, "amount" to it.amount, "category" to it.category) },
                            "mood" to moodValue,
                            "aiInsight" to aiInsightValue,
                            "totalAmount" to newTotal,
                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        db.collection("users").document(uid).collection("entries").document(firebaseId)
                            .set(data)
                            .addOnSuccessListener {
                                viewModelScope.launch {
                                    repository.markSynced(id, firebaseId)
                                    onComplete(true)
                                }
                            }
                            .addOnFailureListener { e ->
                                e.printStackTrace()
                                onComplete(true)
                            }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onComplete(true)
                    }
                } else {
                    onComplete(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }

    fun loadMonthlySummaries() {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return
        if (hasLoadedMonthlySummaries) return
        hasLoadedMonthlySummaries = true
        
        viewModelScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.MONTH, -12)
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val twelveMonthsAgoTs = com.google.firebase.Timestamp(cal.time)
                
                db.collection("users").document(uid).collection("entries")
                    .whereGreaterThanOrEqualTo("date", twelveMonthsAgoTs)
                    .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot == null) return@addOnSuccessListener
                        
                        val currentCal = java.util.Calendar.getInstance()
                        val currentYear = currentCal.get(java.util.Calendar.YEAR)
                        val currentMonth = currentCal.get(java.util.Calendar.MONTH)
                        
                        val docCal = java.util.Calendar.getInstance()
                        
                        data class YearMonthKey(val year: Int, val month: Int)
                        
                        val grouped = snapshot.documents.groupBy { doc ->
                            val date = doc.getTimestamp("date")?.toDate() ?: java.util.Date()
                            docCal.time = date
                            YearMonthKey(
                                docCal.get(java.util.Calendar.YEAR),
                                docCal.get(java.util.Calendar.MONTH)
                            )
                        }
                        
                        // cache raw docs per month so a tap builds detail with zero extra reads
                        monthDocsCache = grouped.mapKeys { (k, _) -> "${k.year}-${k.month}" }

                        val summaries = mutableListOf<MonthSummary>()

                        grouped.forEach { (key, docs) ->
                            if (key.year == currentYear && key.month == currentMonth) {
                                return@forEach
                            }
                            
                            var totalSpent = 0.0
                            val moods = mutableListOf<String>()
                            
                            docs.forEach { doc ->
                                val totalAmount = doc.getDouble("totalAmount") ?: doc.getLong("totalAmount")?.toDouble() ?: 0.0
                                totalSpent += totalAmount
                                
                                val mood = doc.getString("mood") ?: "Normal"
                                moods.add(mood)
                            }
                            
                            val topMood = moods.groupBy { it }
                                .maxByOrNull { it.value.size }?.key ?: "Normal"
                            
                            val tempCal = java.util.Calendar.getInstance()
                            tempCal.set(key.year, key.month, 1, 0, 0, 0)   // set Y/M/D atomically so day-of-month can't roll the month over
                            val label = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(tempCal.time)
                            
                            summaries.add(
                                MonthSummary(
                                    year = key.year,
                                    month = key.month,
                                    label = label,
                                    totalSpent = totalSpent,
                                    entryCount = docs.size,
                                    topMood = topMood
                                )
                            )
                        }
                        
                        summaries.sortWith(compareByDescending<MonthSummary> { it.year }.thenByDescending { it.month })
                        
                        db.collection("users").document(uid).collection("monthlyStats").get()
                            .addOnSuccessListener { snap ->
                                val incomeByKey = snap.documents.associate { it.id to (it.getDouble("income") ?: it.getLong("income")?.toDouble()) }
                                _monthlySummaries.value = summaries.map { s ->
                                    s.copy(income = incomeByKey["${s.year}-${s.month}"])
                                }.sortedWith(compareByDescending<MonthSummary> { it.year }.thenByDescending { it.month })
                            }
                            .addOnFailureListener {
                                _monthlySummaries.value = summaries
                            }
                    }
                    .addOnFailureListener {
                        hasLoadedMonthlySummaries = false
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                hasLoadedMonthlySummaries = false
            }
        }
    }

    fun fetchMonthDetail(year: Int, month: Int) {
        val uid = _currentUserId.value
        if (uid.isEmpty() || uid == "anonymous") return
        val key = "$year-$month"
        if (_monthDetails.value.containsKey(key)) return

        // cache-first: build from docs already fetched by loadMonthlySummaries — zero extra Firestore reads
        monthDocsCache[key]?.let { cachedDocs ->
            val detail = buildMonthDetail(year, month, uid, cachedDocs)
            _monthDetails.value = _monthDetails.value.toMutableMap().apply { put(key, detail) }
            return
        }

        _loadingMonthKey.value = key

        viewModelScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                
                val calStart = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startOfMonthTs = com.google.firebase.Timestamp(calStart.time)
                
                val calNext = java.util.Calendar.getInstance().apply {
                    time = calStart.time
                    add(java.util.Calendar.MONTH, 1)
                }
                val startOfNextMonthTs = com.google.firebase.Timestamp(calNext.time)
                
                db.collection("users").document(uid).collection("entries")
                    .whereGreaterThanOrEqualTo("date", startOfMonthTs)
                    .whereLessThan("date", startOfNextMonthTs)
                    .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot == null) {
                            _loadingMonthKey.value = null
                            return@addOnSuccessListener
                        }
                        
                        val monthDetail = buildMonthDetail(year, month, uid, snapshot.documents)

                        val currentMap = _monthDetails.value.toMutableMap()
                        currentMap[key] = monthDetail
                        _monthDetails.value = currentMap
                        _loadingMonthKey.value = null
                    }
                    .addOnFailureListener {
                        _loadingMonthKey.value = null
                        com.example.utils.PremiumToast.show(getApplication(), "Poora hisaab load nahi ho paaya, dobara try karein.")
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                _loadingMonthKey.value = null
            }
        }
    }

    // Pure builder: maps Firestore entry docs → MonthDetail. Shared by the cache-hit path and the network fallback.
    private fun buildMonthDetail(
        year: Int,
        month: Int,
        uid: String,
        docs: List<com.google.firebase.firestore.DocumentSnapshot>
    ): MonthDetail {
        val entries = docs.map { doc ->
            val dateStamp = doc.getTimestamp("date")?.toDate()?.time ?: System.currentTimeMillis()
            val originalText = doc.getString("originalText") ?: ""
            val mood = doc.getString("mood") ?: "Normal"
            val aiInsight = doc.getString("aiInsight") ?: ""
            val totalAmount = doc.getDouble("totalAmount") ?: doc.getLong("totalAmount")?.toDouble() ?: 0.0

            val expensesRaw = doc.get("expenses") as? List<Map<String, Any>> ?: emptyList()
            val expensesList = expensesRaw.map {
                val item = it["item"] as? String ?: ""
                val amountStr = it["amount"]?.toString() ?: "0"
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                val category = it["category"] as? String ?: "Baaki"
                com.example.api.GeminiExpenseItem(item, amount, category)
            }
            val parsedExpensesJson = com.example.api.GeminiClient.serializeExpenses(expensesList)

            DiaryEntry(
                timestamp = dateStamp,
                originalText = originalText,
                parsedExpensesJson = parsedExpensesJson,
                mood = mood,
                aiInsight = aiInsight,
                userId = uid,
                isSynced = true,
                totalAmount = totalAmount,
                firebaseId = doc.id,
                isParsed = true
            )
        }

        val totalSpent = entries.sumOf { it.totalAmount }

        val categoryTotals = mutableMapOf<String, Double>()
        entries.forEach { entry ->
            val expList = com.example.api.GeminiClient.deserializeExpenses(entry.parsedExpensesJson)
            expList.forEach { exp ->
                val standardized = com.example.data.CategoryConfig.getCategoryByName(exp.category).name
                categoryTotals[standardized] = (categoryTotals[standardized] ?: 0.0) + exp.amount
            }
        }

        val moodCounts = entries.groupBy { it.mood }.mapValues { it.value.size }
        val incomeVal = _monthlySummaries.value.find { it.year == year && it.month == month }?.income

        return MonthDetail(
            year = year,
            month = month,
            entries = entries,
            totalSpent = totalSpent,
            categoryTotals = categoryTotals,
            moodCounts = moodCounts,
            income = incomeVal
        )
    }
}

data class MonthSummary(
    val year: Int, val month: Int,      // month 0-based (Calendar.MONTH)
    val label: String,                  // e.g. "June 2026"
    val totalSpent: Double,
    val entryCount: Int,
    val topMood: String,                // most frequent mood that month
    val income: Double? = null
)

data class MonthDetail(
    val year: Int, val month: Int,
    val entries: List<DiaryEntry>,      // reuse existing DiaryEntry model
    val totalSpent: Double,
    val categoryTotals: Map<String, Double>,   // standardized category -> sum
    val moodCounts: Map<String, Int>,
    val income: Double? = null
)
