package com.jizhang.assistant.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jizhang.assistant.ui.aichat.AiChatScreen
import com.jizhang.assistant.ui.add.AddRecordScreen
import com.jizhang.assistant.ui.features.AssetsScreen
import com.jizhang.assistant.ui.features.BackupScreen
import com.jizhang.assistant.ui.features.BooksScreen
import com.jizhang.assistant.ui.features.CalendarScreen
import com.jizhang.assistant.ui.features.CategoryManageScreen
import com.jizhang.assistant.ui.features.CycleBillsScreen
import com.jizhang.assistant.ui.features.MoreScreen
import com.jizhang.assistant.ui.features.RemindersScreen
import com.jizhang.assistant.ui.features.SearchScreen
import com.jizhang.assistant.ui.features.TemplatesScreen
import com.jizhang.assistant.ui.home.HomeScreen
import com.jizhang.assistant.ui.settings.AiSettingsScreen
import com.jizhang.assistant.ui.settings.SettingsScreen
import com.jizhang.assistant.ui.stats.StatsScreen
import com.jizhang.assistant.ui.theme.Primary

object Routes {
    const val HOME = "home"
    const val STATS = "stats"
    const val AI = "ai"
    const val SETTINGS = "settings"
    const val ADD = "add"
    const val EDIT = "edit/{recordId}"
    fun edit(recordId: Long) = "edit/$recordId"

    // 功能页
    const val MORE = "more"
    const val BOOKS = "books"
    const val ASSETS = "assets"
    const val TEMPLATES = "templates"
    const val CYCLE_BILLS = "cycleBills"
    const val CALENDAR = "calendar"
    const val SEARCH = "search"
    const val CATEGORY_MANAGE = "categoryManage"
    const val REMINDERS = "reminders"
    const val BACKUP = "backup"
    const val AI_SETTINGS = "aiSettings"
    const val THEME_PROFILE = "themeProfile"
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector,
)

private val tabs = listOf(
    Tab(Routes.HOME, "明细", Icons.AutoMirrored.Outlined.ListAlt, Icons.AutoMirrored.Filled.ListAlt),
    Tab(Routes.STATS, "图表", Icons.Outlined.BarChart, Icons.Filled.BarChart),
    Tab(Routes.AI, "AI", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    Tab(Routes.SETTINGS, "我的", Icons.Outlined.Person, Icons.Filled.Person),
)

@Composable
fun JizhangAppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBars = currentRoute in listOf(
        Routes.HOME, Routes.STATS, Routes.AI, Routes.SETTINGS,
    )

    Scaffold(
        bottomBar = {
            if (showBars) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) tab.iconSelected else tab.icon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.HOME) {
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.ADD) },
                    containerColor = Primary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "记一笔")
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onAdd = { navController.navigate(Routes.ADD) },
                    onEdit = { id -> navController.navigate(Routes.edit(id)) },
                )
            }
            composable(Routes.STATS) {
                StatsScreen(onOpenCalendar = { navController.navigate(Routes.CALENDAR) })
            }
            composable(Routes.AI) { AiChatScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpen = { route -> navController.navigate(route) },
                    onLoggedOut = {
                        // 退出登录：回到登录页
                        navController.navigate("login_gate") { popUpTo(0) }
                    },
                )
            }
            composable("login_gate") {
                com.jizhang.assistant.ui.login.LoginScreen(onFinished = {
                    navController.navigate(Routes.HOME) { popUpTo(0) }
                })
            }
            composable(Routes.ADD) {
                AddRecordScreen(onDone = { navController.popBackStack() })
            }
            composable(Routes.EDIT) { entry ->
                val recordId = entry.arguments?.getString("recordId")?.toLongOrNull() ?: -1L
                AddRecordScreen(recordId = recordId, onDone = { navController.popBackStack() })
            }
            composable(Routes.MORE) {
                MoreScreen(onOpen = { route -> navController.navigate(route) })
            }
            composable(Routes.BOOKS) { BooksScreen() }
            composable(Routes.ASSETS) { AssetsScreen() }
            composable(Routes.TEMPLATES) {
                TemplatesScreen(
                    onUse = { navController.navigate(Routes.ADD) },
                )
            }
            composable(Routes.CYCLE_BILLS) { CycleBillsScreen() }
            composable(Routes.CALENDAR) { CalendarScreen() }
            composable(Routes.SEARCH) { SearchScreen() }
            composable(Routes.CATEGORY_MANAGE) { CategoryManageScreen() }
            composable(Routes.REMINDERS) { RemindersScreen() }
            composable(Routes.BACKUP) { BackupScreen() }
            composable(Routes.AI_SETTINGS) { AiSettingsScreen() }
            composable(Routes.THEME_PROFILE) { com.jizhang.assistant.ui.settings.ThemeProfileScreen() }
        }
    }
}
