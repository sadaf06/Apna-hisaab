# 💜 Apna Hisaab

**Ek line likho, baaki hum samjhe.**

🔗 **[Live Website](https://apnahisaab-delta.vercel.app/)**

Apna Hisaab ek Hinglish-first Android expense tracker hai jo natural language se kharcha samajhta hai — bas type karo *"200 ki chai piya"* ya *"1000 bachaye is mahine"*, aur AI khud parse karke sahi jagah entry daal deta hai. Sirf paise ka hisaab nahi — apna mood bhi track karo aur har mahine ki apni kahani padho.

---

## ✨ Features

- **🗣️ Natural Language Parsing** — Hinglish/Hindi/English mix mein likho, AI (Gemini) khud amount, category, aur intent nikal leta hai.
- **🎯 Multi-Intent Detection** — Ek hi sentence mein multiple actions samajhta hai: *"100 ki chai pi aur 500 bachaye"* → expense + saving, dono alag se log honge.
- **😊 Mood Tracking** — Har entry ke saath apna mood bhi capture hota hai — Khush, Normal, Thaka, Stressed, Sad.
- **📖 Month ki Kahani** — Mahine ke end mein AI-generated warm, personal Hinglish summary — tumhare spending patterns aur moods ki kahani.
- **📖 Khata (Khatabook)** — Grahakon aur dosto ke sath udhaar aur lena-dena track karo (Maine diye / Mujhe mile).
- **🔔 Daily Reminders** — Roz raat 9 baje gentle nudge, taaki koi din hisaab likhna na bhoolo.
- **🌙 Dark, Glassmorphic UI** — Modern liquid-glass navigation bar aur purple-teal themed dark interface.
- **🔐 Secure & Synced** — Firebase Auth + Firestore ke saath data safe aur sync rehta hai.

---

## 📱 Screens

| Screen | Kaam |
|---|---|
| **Aaj** | Aaj ka kharcha/mood ek line mein likho |
| **Hisaab** | Saare entries ki list, running total ke saath |
| **Kahani** | Monthly AI-generated story + spending insights |
| **Khata** | Udhaar, lena-dena aur customer ledger hisaab |
| **Settings** | Profile, preferences, logout |

---

## 🛠️ Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Gemini API** — natural language parsing aur monthly summaries ke liye
- **Firebase** — Auth, Firestore, App Check
- **Retrofit + Moshi** — networking aur JSON parsing
- **Coroutines** — async operations
- **Haze** — glassmorphic blur effects

---

## 🚀 Run Locally (Google AI Studio)

1. Repo clone/import karo Google AI Studio mein.
2. AI Studio ke **Secrets panel** mein apni `GEMINI_API_KEY` add karo.
3. Firebase project setup karo aur `google-services.json` add karo (`app/` folder mein).
4. Build se pehle `build.gradle.kts` mein signing config check karo — production ke liye apna release keystore use karo, debug config nahi.
5. Run karo — pehli baar app khulte hi onboarding flow milega.

---

## 🧠 How Parsing Works

```
User Input (Hinglish) 
      ↓
Gemini AI (Intent Router)
      ↓
JSON: { actions: [...], mood, ai_insight }
      ↓
App: Expense / Saving / Goal / Mood entry created
```

App multiple fallback Gemini models try karta hai (fast → capable → pro) taaki rate limits ya downtime mein bhi parsing na ruke.

---

## 📌 Roadmap

- [ ] Voice input for hands-free logging
- [ ] Budget alerts aur limits
- [ ] Export monthly reports as PDF
- [ ] Multi-language support (pure Hindi, regional languages)

---

## 🙏 Made With

Made with ❤️ by **Sadaf Siddiqui**

Agar pasand aaya to ⭐ star zaroor karo!
