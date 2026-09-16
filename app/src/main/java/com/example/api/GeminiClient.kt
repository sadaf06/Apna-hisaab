package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import com.example.BuildConfig
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

// Request classes for Gemini API
data class Part(val text: String)
data class Content(val parts: List<Part>)
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)

// Response classes for Gemini API
data class CandidateContent(val parts: List<Part>)
data class Candidate(val content: CandidateContent)
data class GenerateContentResponse(val candidates: List<Candidate>?)

// Parsed structures for our UI
data class GeminiExpenseItem(
    val item: String,
    val amount: Double,
    val category: String
)

data class GeminiAction(
    val intent: String,
    val amount: Double = 0.0,
    val goalKeyword: String = "",
    val appAction: String = "",
    val expenses: List<GeminiExpenseItem> = emptyList(),
    val mood: String? = null
)

data class GeminiAnalysisResult(
    val actions: List<GeminiAction> = emptyList(),
    val ai_insight: String = "",
    val toastMessage: String? = null
)

interface GeminiService {
    @POST("v1beta/models/{modelName}:generateContent")
    suspend fun generateContent(
        @Path("modelName") modelName: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val service: GeminiService = retrofit.create(GeminiService::class.java)

    fun serializeExpenses(expenses: List<GeminiExpenseItem>): String {
        return try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, GeminiExpenseItem::class.java)
            val adapter = moshi.adapter<List<GeminiExpenseItem>>(listType)
            adapter.toJson(expenses)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun deserializeExpenses(json: String): List<GeminiExpenseItem> {
        return try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, GeminiExpenseItem::class.java)
            val adapter = moshi.adapter<List<GeminiExpenseItem>>(listType)
            val items = adapter.fromJson(json) ?: emptyList()
            items.map { item ->
                val normCat = com.example.data.CategoryConfig.normalizeCategory(item.category, item.item)
                item.copy(category = normCat)
            }
        } catch (e: Exception) {
            // Fallback for legacy string amounts (e.g., "amount":"100" vs 100) — parse manually
            try {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.getJSONObject(i)
                    val item = obj.optString("item", "")
                    val amountStr = obj.optString("amount", obj.opt("amount")?.toString() ?: "0")
                    val amount = amountStr.toDoubleOrNull() ?: obj.optDouble("amount", 0.0)
                    val category = obj.optString("category", "Other")
                    if (item.isEmpty() && amount == 0.0) null else GeminiExpenseItem(item, amount, com.example.data.CategoryConfig.normalizeCategory(category, item))
                }
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }

    suspend fun generateGreeting(todayTotal: Double, monthlyBudget: Double, userName: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Aaj ka hisaab dekhein!"
        }

        val prompt = """
            Write exactly ONE short, witty, casual Hinglish sentence (max 3-4 words) about the user's spending today. 
            User's name is $userName. 
            Today's total spend: ₹${todayTotal}. 
            Monthly budget: ₹${monthlyBudget}.
            Time of day: ${java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)} o'clock.
            
            IMPORTANT RULES:
            - NEVER use greetings (no Namaste, Salam, Hello, Hi, Hey, Good morning, Good evening).
            - Must be short, max 4 words.
            - Just a punchy comment on their spending.
            - Use the user's name if it fits naturally, but not required.
            
            Examples of good output (DO NOT copy these, make a new one):
            - $userName, chai pe ud gaya?
            - Kharcha control mein hai!
            - Aaj ka hisaab lajawaab!
            - Paisa bacha, $userName?
            - Kharcha limit cross?
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(listOf(Part(prompt))))
        )

        return try {
            val response = callWithRetry { service.generateContent("gemini-1.5-flash", apiKey, request) }
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()?.removeSurrounding("\"")
            if (!text.isNullOrEmpty()) text else "Aaj ka hisaab dekhein!"
        } catch (e: Exception) {
            "Aaj ka hisaab dekhein!"
        }
    }

    private suspend fun <T> callWithRetry(
        maxRetries: Int = 3,
        initialDelay: Long = 2000L,
        block: suspend () -> T
    ): T {
        var retries = 0
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if ((e is HttpException && e.code() == 429) || e is IOException) {
                    if (retries < maxRetries) {
                        retries++
                        delay(initialDelay * retries)
                    } else {
                        if (e is HttpException && e.code() == 429) {
                            throw IllegalStateException("Bhai, server thak gaya hai (Busy/429 Error). Thodi der ruko aur phir se retry maro!")
                        }
                        throw e
                    }
                } else {
                    throw e
                }
            }
        }
    }

    suspend fun parseStory(userInput: String, userMood: String? = null): GeminiAnalysisResult {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API key missing. Please add GEMINI_API_KEY to AI Studio Secrets panel.")
        }

        val fallbackMood = when (userMood?.lowercase()?.trim()) {
            "khush" -> "Khush"
            "normal" -> "Normal"
            "sad" -> "Sad"
            "thaka" -> "Thaka"
            "stressed" -> "Stressed"
            else -> "Normal"
        }

        val systemPrompt = """
            You are "Apna Hisaab" AI Assistant, a smart, friendly Hinglish/Hindi budget parsing helper and Intent Router.
            User ne Hinglish mein ek ya zyada sentences likhe hain. Aapko use parse karke identify karna hai ki user kitne actions (intents) perform karna chahta hai.
            
            Aapko ye JSON return karna hai:
            {
              "actions": [
                {
                  "intent": "EXPENSE" | "SAVING" | "GOAL_COMPLETE" | "APP_ACTION" | "UNKNOWN",
                  "amount": number,
                  "goalKeyword": string,
                  "appAction": "RESET_PASSWORD" | "EDIT_NAME" | "LOGOUT" | "",
                  "expenses": [{"item": string, "amount": number, "category": string}],
                  "mood": "Khush" | "Thaka" | "Normal" | "Stressed" | "Sad" | null
                }
              ],
              "ai_insight": string,
              "toastMessage": string
            }
            
            IMPORTANT: Identify ALL distinct actions. If a user says "100 ki chai pi aur 500 bachaye", you must return TWO actions: one EXPENSE and one SAVING.
            
            Strict Routing Rules (apply to each action):
            1. SAVING: If user talks about saving money (e.g., "500 bacha liye", "1000 save kiye"), set intent = "SAVING".
            2. GOAL_COMPLETE: If user talks about completing a goal (e.g., "Kashmir ghum aaya", "Bike le li"), set intent = "GOAL_COMPLETE".
            3. APP_ACTION: If user requests an app action (e.g., "logout", "password reset"), set intent = "APP_ACTION".
            4. EXPENSE: If user reports a normal spending / expense (e.g., "100 ki chai pi"), set intent = "EXPENSE".
            
            Mood Rule: Determine "mood" for the overall input and include it in at least the first action. Values: "Khush", "Thaka", "Normal", "Stressed", "Sad".
            
            Constraint:
            - categories: "Khana", "Ghar Kharch", "Rent", "EMI", "Petrol", "Safar", "Masti", "Shopping", "Health", "Padhai", "Personal", "Gift", "Savings", "Pooja", "Recharge", "Other".
            - ai_insight: A warm summary line.
            - toastMessage: A short, combined fun summary of all actions (e.g., "₹100 kharch aur ₹500 bachaye!").
            
            Respond ONLY with valid raw JSON.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(userInput)))),
            systemInstruction = Content(parts = listOf(Part(systemPrompt)))
        )

        val models = listOf(
            "gemini-3.1-flash-lite-preview", // 1. Fastest: Lite/Preview compressed model for ultra-low latency
            "gemini-2.5-flash",              // 2. Extremely fast, optimized modern Flash model
            "gemini-3.5-flash",              // 3. High-capability fast Flash model
            "gemini-flash-latest",           // 4. Standard Flash fallback (Gemini 1.5 Flash)
            "gemini-pro-latest"              // 5. Slowest: Pro reasoning model (fallback if Flash is busy/quota hit)
        )
        
        for (model in models) {
            var retries = 0
            while (retries < 2) {
                try {
                    val response = service.generateContent(model, apiKey, request)
                    val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                        ?: throw IllegalStateException("Empty response")

                    // Strip markdown
                    val cleanedJson = if (rawJson.startsWith("```")) {
                        rawJson.lines().let { if (it.size > 2) it.subList(1, it.size - 1).joinToString("\n").trim() else rawJson }
                    } else {
                        rawJson
                    }

                    return moshi.adapter(GeminiAnalysisResult::class.java).fromJson(cleanedJson) ?: throw IllegalStateException("Parsing failure")
                    
                } catch (e: Exception) {
                    if (e is IllegalStateException) {
                        throw e
                    }
                    val code = (e as? HttpException)?.code()
                    
                    if (code == 404) {
                        // Switch immediately
                        break 
                    } else if (code == 503) {
                        if (retries == 0) {
                            delay(3000L)
                            retries++
                            continue
                        } else {
                            break // Switch
                        }
                    } else if (code == 429) {
                        if (retries == 0) {
                            delay(10000L)
                            retries++
                            continue
                        } else {
                            break // Switch
                        }
                    } else {
                        // Other error, switch immediately
                        break
                    }
                }
            }
        }
        
        // If we reach here, all models tried and failed
        throw Exception("All model attempts failed")

    }

    suspend fun getMonthlySummary(entriesText: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API key missing. Please add GEMINI_API_KEY to AI Studio Secrets panel.")
        }

        val systemPrompt = """
            You are "Apna Hisaab" AI Assistant, a smart, warm, and highly empathetic Hinglish budget and lifestyle companion.
            I will provide you with the user's spending logs, story snippets, and mood ratings for this month.
            Analyze their monthly story patterns, moods, and expenditures.
            Write a wonderfully warm, friendly, positive, and insightful summary of their month in Hinglish.
            
            Strict constraints:
            1. Write exactly 3-4 sentences in a single paragraph.
            2. Start the paragraph directly with: "Yaar, is mahine tumne...".
            3. Do NOT use bullet points, bold lists, titles, headings, or markdown formats. Just a single paragraph of plain text.
            4. Use conversational Hinglish.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(entriesText)))),
            systemInstruction = Content(parts = listOf(Part(systemPrompt)))
        )

        // For summary, maybe we should also use fallback logic?
        // User didn't specify summary should have fallback, but for consistency I should probably add it or keep it simple.
        // Let's use the first model for now to keep it simple, as requested only for story parsing.
        
        val response = callWithRetry { service.generateContent("gemini-3.5-flash", apiKey, request) }
        return response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: "Is mahine ki koi dastaan abhi shuru nahi hui!"
    }
}
