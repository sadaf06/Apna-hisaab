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
        return getCategoryByName(name, "")
    }

    fun getCategoryByName(name: String, description: String = ""): CategoryInfo {
        val cleanName = name.trim().lowercase()
        val cleanDesc = description.trim().lowercase()

        // 1. Handle legacy combined "Rent/EMI", "Rent & EMI", "Rent and EMI"
        if (cleanName == "rent/emi" || cleanName == "rent & emi" || (cleanName.contains("rent") && cleanName.contains("emi"))) {
            val isEmi = cleanDesc.contains("emi") || cleanDesc.contains("loan") || cleanDesc.contains("kist") || cleanDesc.contains("installment") || cleanDesc.contains("credit card")
            val targetName = if (isEmi) "EMI" else "Rent"
            return categories.first { it.name == targetName }
        }

        // 2. Direct exact match
        categories.firstOrNull { it.name.trim().lowercase() == cleanName }?.let { return it }

        // 3. Specific checks for standalone EMI or Rent in category name
        if (cleanName == "emi" || cleanName.contains("loan") || cleanName.contains("kist") || cleanName.contains("installment")) {
            return categories.first { it.name == "EMI" }
        }
        if (cleanName == "rent" || cleanName.contains("kiraya")) {
            return categories.first { it.name == "Rent" }
        }

        // 4. Partial match with defined categories
        categories.firstOrNull { cleanName.contains(it.name.lowercase()) }?.let { return it }

        // 5. Fallback check using description keywords if category is generic/unmapped
        if (cleanDesc.contains("emi") || cleanDesc.contains("loan") || cleanDesc.contains("kist") || cleanDesc.contains("installment")) {
            return categories.first { it.name == "EMI" }
        }
        if (cleanDesc.contains("rent") || cleanDesc.contains("kiraya")) {
            return categories.first { it.name == "Rent" }
        }

        return CategoryInfo("Other", "📦", Palette.Category.Other, "Jo upar fit na ho")
    }

    fun normalizeCategory(category: String, description: String = ""): String {
        return getCategoryByName(category, description).name
    }
}
