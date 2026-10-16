package com.jizhang.assistant.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.BedroomBaby
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Carpenter
import androidx.compose.material.icons.outlined.Cases
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CleanHands
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Elderly
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.LocalMall
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.ui.graphics.vector.ImageVector

/** 分类ID → Material 图标映射（默认分类体系） */
object CategoryIcons {

    private val map: Map<String, ImageVector> = mapOf(
        "e_catering" to Icons.Outlined.Restaurant,
        "e_vegetable" to Icons.Outlined.LocalGroceryStore,
        "e_fruit" to Icons.Outlined.Cake,
        "e_snack" to Icons.Outlined.BakeryDining,
        "e_smoke" to Icons.Outlined.WineBar,
        "e_beauty" to Icons.Outlined.Favorite,
        "e_dress" to Icons.Outlined.Storefront,
        "e_home" to Icons.Outlined.CleanHands,
        "e_commodity" to Icons.Outlined.LocalMall,
        "e_communicate" to Icons.Outlined.Smartphone,
        "e_traffic" to Icons.Outlined.DirectionsBus,
        "e_car" to Icons.Outlined.DirectionsCar,
        "e_house" to Icons.Outlined.Home,
        "e_medical" to Icons.Outlined.LocalPharmacy,
        "e_elder" to Icons.Outlined.Elderly,
        "e_child" to Icons.Outlined.ChildCare,
        "e_friend" to Icons.Outlined.Celebration,
        "e_money" to Icons.Outlined.Redeem,
        "e_gift" to Icons.Outlined.CardGiftcard,
        "e_donate" to Icons.Outlined.VolunteerActivism,
        "e_lottery" to Icons.Outlined.EmojiEvents,
        "e_shopping" to Icons.Outlined.ShoppingBag,
        "e_digital" to Icons.Outlined.PhoneIphone,
        "e_entertainment" to Icons.Outlined.SportsEsports,
        "e_sport" to Icons.Outlined.FitnessCenter,
        "e_travel" to Icons.Outlined.Flight,
        "e_pet" to Icons.Outlined.Pets,
        "e_study" to Icons.Outlined.School,
        "e_books" to Icons.Outlined.LocalLibrary,
        "e_office" to Icons.Outlined.Cases,
        "e_express" to Icons.Outlined.LocalShipping,
        "e_repair" to Icons.Outlined.Carpenter,
        "e_social" to Icons.Outlined.Groups,
        "i_wage" to Icons.Outlined.Payments,
        "i_parttime" to Icons.Outlined.Work,
        "i_finance" to Icons.Outlined.Savings,
        "i_bonus" to Icons.Outlined.LocalAtm,
        "i_other" to Icons.Outlined.Wallet,
    )

    /** 兜底图标（自定义分类等） */
    private val fallbackExpense = Icons.Outlined.ShoppingCart
    private val fallbackIncome = Icons.Outlined.AccountBalance
    private val fallbackOther = Icons.Outlined.Category

    fun iconOf(categoryId: String, type: Int = -1): ImageVector =
        map[categoryId] ?: when {
            type == 1 -> fallbackExpense
            type == 3 -> fallbackIncome
            else -> fallbackOther
        }
}
