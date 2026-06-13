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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shauiqiu.eklose.ui.theme.EkloseTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EkloseTheme {
                SettingsScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var saveLogin by rememberSaveable { mutableStateOf(EkwingLoginStore.shouldSaveLogin(context)) }
    var basicApi by rememberSaveable { mutableStateOf(EkwingLoginStore.useBasicApi(context)) }
    var allPages by rememberSaveable { mutableStateOf(EkwingLoginStore.loadAllPages(context)) }

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
                        title = "设置",
                        summary = "管理登录缓存和考试列表",
                    )
                }
                item {
                    GroupSection(title = "账号与列表") {
                        GroupItem(
                            title = "保存登录状态",
                            summary = "保存 uid/token、登录信息和密码",
                            trailing = {
                                Switch(
                                    checked = saveLogin,
                                    onCheckedChange = {
                                        saveLogin = it
                                        EkwingLoginStore.setSaveLogin(context, it)
                                    },
                                )
                            },
                        )
                        GroupDivider()
                        GroupItem(
                            title = "Basic 成绩接口",
                            summary = "当列表 URL 指向 basic scoreinfo 时自动启用",
                            trailing = {
                                Switch(
                                    checked = basicApi,
                                    onCheckedChange = {
                                        basicApi = it
                                        EkwingLoginStore.setUseBasicApi(context, it)
                                    },
                                )
                            },
                        )
                        GroupDivider()
                        GroupItem(
                            title = "历史考试全分页",
                            summary = "读取 study-center-history 的所有分页",
                            trailing = {
                                Switch(
                                    checked = allPages,
                                    onCheckedChange = {
                                        allPages = it
                                        EkwingLoginStore.setLoadAllPages(context, it)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
