package com.shauiqiu.eklose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shauiqiu.eklose.ui.theme.EkloseTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme

class AnnouncementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EkloseTheme {
                AnnouncementScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun AnnouncementScreen(onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            EkloseTopBar(onBack = onBack)
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(innerPadding),
            color = MiuixTheme.colorScheme.surface,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    PageLead(
                        title = "公告与更新",
                        summary = "查看重要提示和最近变化",
                    )
                }
                item {
                    GroupSection(title = "公告") {
                        GroupItem(
                            title = "云端读取提醒",
                            summary = "云端读取前请确认没有正在进行考试、练习、录音或提交。",
                            showArrow = false,
                        )
                    }
                }
                item {
                    GroupSection(title = "更新日志") {
                        GroupItem(
                            title = "读取页",
                            summary = "新增作业范围切换，并加入试卷列表预览。",
                            showArrow = false,
                        )
                        GroupDivider()
                        GroupItem(
                            title = "主页",
                            summary = "新增公告与更新入口，便于集中查看重要信息。",
                            showArrow = false,
                        )
                    }
                }
            }
        }
    }
}
