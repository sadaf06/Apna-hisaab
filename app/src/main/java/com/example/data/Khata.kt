package com.example.data

data class KhataParty(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val netBalance: Double = 0.0, // > 0: Aapko lene hain (Green/Teal), < 0: Aapko dene hain (Red/Rose), 0: Barabar
    val lastEntryNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class KhataTransaction(
    val id: String = "",
    val partyId: String = "",
    val partyName: String = "",
    val amount: Double = 0.0,
    val type: String = "GAVE", // "GAVE" = Maine Diye (Udhaar), "GOT" = Mujhe Mile (Wapas)
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
