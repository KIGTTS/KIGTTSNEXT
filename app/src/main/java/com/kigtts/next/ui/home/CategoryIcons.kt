package com.kigtts.next.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.kigtts.next.state.CategoryIcon

internal fun CategoryIcon.vector(): ImageVector = when (this) {
    CategoryIcon.LIST -> Icons.Default.Menu
    CategoryIcon.INFO -> Icons.Default.Info
    CategoryIcon.PERSON -> Icons.Default.Person
    CategoryIcon.FAVORITE -> Icons.Default.FavoriteBorder
    CategoryIcon.HOME -> Icons.Default.Home
    CategoryIcon.WORK -> Icons.Default.Work
    CategoryIcon.SCHOOL -> Icons.Default.School
    CategoryIcon.TRAVEL -> Icons.Default.Flight
    CategoryIcon.FOOD -> Icons.Default.Restaurant
    CategoryIcon.HEALTH -> Icons.Default.MedicalServices
    CategoryIcon.SHOPPING -> Icons.Default.ShoppingBag
    CategoryIcon.MESSAGE -> Icons.AutoMirrored.Filled.Chat
    else -> com.kigtts.next.ui.components.MaterialSymbols.vector(symbol) ?: Icons.Default.Menu
}
