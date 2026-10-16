package com.jizhang.assistant.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.assistant.ui.Routes
import com.jizhang.assistant.ui.theme.Expense
import com.jizhang.assistant.ui.theme.Primary

private data class Feature(
    val route: String,
    val label: String,
    val desc: String,
    val icon: ImageVector,
    val color: Color,
)

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    val features = listOf(
        Feature(Routes.BOOKS, "多账本", "生活/工作分开记", Icons.Filled.MenuBook, Primary),
        Feature(Routes.ASSETS, "资产账户", "余额·转账·信用卡", Icons.Filled.Wallet, Color(0xFF5B8DEF)),
        Feature(Routes.TEMPLATES, "模板记账", "常用账一键记", Icons.Filled.Star, Color(0xFFF5A623)),
        Feature(Routes.CYCLE_BILLS, "周期账", "房租工资自动记", Icons.Filled.EventRepeat, Color(0xFF9B59B6)),
        Feature(Routes.CALENDAR, "日历视图", "每日收支一览", Icons.Filled.CalendarMonth, Color(0xFFE74C3C)),
        Feature(Routes.SEARCH, "搜索", "找流水so easy", Icons.Filled.Search, Color(0xFF16A085)),
        Feature(Routes.CATEGORY_MANAGE, "分类管理", "自定义分类", Icons.Filled.Category, Color(0xFFE67E22)),
        Feature(Routes.REMINDERS, "记账提醒", "定时叫你记账", Icons.Filled.Notifications, Color(0xFF2980B9)),
        Feature(Routes.BACKUP, "备份恢复", "数据不丢", Icons.Filled.CloudDownload, Color(0xFF27AE60)),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            "功能中心",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 12.dp, vertical = 4.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(features) { f ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .height(110.dp)
                        .clickable { onOpen(f.route) },
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(f.color.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(f.icon, contentDescription = f.label, tint = f.color, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(f.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            f.desc,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
