package com.shauiqiu.eklose

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shauiqiu.eklose.ui.theme.EkloseTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
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
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

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
                item {
                    GroupSection(title = "其他") {
                        GroupItem(
                            title = "关于 eklose",
                            summary = "应用信息、开源与致谢",
                            onClick = { showAboutDialog = true },
                        )
                    }
                }
            }
        }
        AboutDialog(
            show = showAboutDialog,
            onDismiss = { showAboutDialog = false },
            onOpenUrl = { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            },
            versionName = context.currentVersionName(),
        )
    }
}

@Composable
private fun AboutDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    versionName: String,
) {
    OverlayDialog(
        show = show,
        title = "关于 eklose",
        summary = "翼课学习中心答案读取工具",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AboutInfoBlock(
                title = "软件信息",
                lines = listOf(
                    "eklose",
                    "版本: ${versionName.ifBlank { "未知" }}",
                    "开源协议: GPL 3.0",
                ),
            )
            AboutInfoBlock(
                title = "关于作者",
                lines = listOf(
                    "帅丘",
                    "感谢 Fe、ekwing_get_answer 相关实现和开源社区提供的参考。",
                ),
            )
            GroupSection {
                GroupItem(
                    title = "开源地址",
                    summary = "查看项目仓库",
                    onClick = { onOpenUrl("https://github.com/qiuqiqiuqid/eklose") },
                )
                GroupDivider()
                GroupItem(
                    title = "反馈问题",
                    summary = "通过 GitHub Issues 提交建议",
                    onClick = { onOpenUrl("https://github.com/qiuqiqiuqid/eklose/issues") },
                )
            }
            TextButton(
                text = "关闭",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.primary,
                    textColor = MiuixTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun AboutInfoBlock(
    title: String,
    lines: List<String>,
) {
    GroupSection(title = title) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            lines.forEachIndexed { index, line ->
                Text(
                    text = line,
                    style = if (index == 0) MiuixTheme.textStyles.body1 else MiuixTheme.textStyles.body2,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == 0) {
                        MiuixTheme.colorScheme.onSurfaceContainer
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
}

private fun Context.currentVersionName(): String {
    return packageManager.getPackageInfo(packageName, 0).versionName ?: ""
}
