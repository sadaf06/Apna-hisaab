package com.example.data

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.Palette

data class CategoryInfo(
    val name: String, 
    val icon: String, 
    val color: Color,
    val description: String = ""
)

object CategoryConfig {
    val categories = listOf(
        CategoryInfo("Khana", "🍽️", Palette.Category.Khana, "Bahar ka khana, restaurant"),
        CategoryInfo("Ghar Kharch", "🏠", Palette.Category.GharKharch, "Groceries, sabzi, milk"),
        CategoryInfo("Rent", "🏠", Palette.Category.Rent, "Ghar ka kiraya"),
        CategoryInfo("EMI", "💳", Palette.Category.EMI, "Loan, EMI, installment"),
        CategoryInfo("Petrol", "⛽", Palette.Category.Petrol, "Fuel for bike/car"),
        CategoryInfo("Safar", "🚗", Palette.Category.Safar, "Auto, bus, metro, taxi"),
        CategoryInfo("Masti", "🎮", Palette.Category.Masti, "Movies, games, outing"),
        CategoryInfo("Shopping", "🛍️", Palette.Category.Shopping, "Kapde, shoes, electronics"),
        CategoryInfo("Health", "💊", Palette.Category.Health, "Doctor, medicine, hospital"),
        CategoryInfo("Padhai", "📚", Palette.Category.Padhai, "Books, courses, fees"),
        CategoryInfo("Personal", "💇", Palette.Category.Personal, "Salon, gym, self care"),
        CategoryInfo("Gift", "🎁", Palette.Category.Gift, "Kisi ko diya"),
        CategoryInfo("Savings", "💰", Palette.Category.Savings, "FD, investment, piggy bank"),
        CategoryInfo("Pooja", "🙏", Palette.Category.Pooja, "Mandir, donations, festivals"),
        CategoryInfo("Recharge", "📱", Palette.Category.Recharge, "Mobile, internet, DTH"),
        CategoryInfo("Other", "📦", Palette.Category.Other, "Jo upar fit na ho")
    )

    fun getCategoryByName(name: String): CategoryInfo {
        return categories.firstOrNull { it.name.trim().lowercase() == name.trim().lowercase() }
            ?: categories.firstOrNull { name.trim().lowercase().contains(it.name.lowercase()) }
            ?: CategoryInfo("Other", "📦", Palette.Category.Other, "Jo upar fit na ho")
    }

    fun normalizeCategory(category: String, description: String = ""): String {
        return getCategoryByName(category).name
    }
}
