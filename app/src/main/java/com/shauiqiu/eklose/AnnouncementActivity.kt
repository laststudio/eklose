package com.shauiqiu.eklose

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shauiqiu.eklose.ui.theme.EkloseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URL
import java.net.URLConnection

class AnnouncementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = RemoteContent.fromIntent(intent)
        enableEdgeToEdge()
        setContent {
            EkloseTheme {
                AnnouncementScreen(
                    initialContent = content,
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_ANNOUNCEMENT_TITLE = "announcement_title"
        private const val EXTRA_ANNOUNCEMENT_MESSAGE = "announcement_message"
        private const val EXTRA_ANNOUNCEMENT_UPDATED_AT = "announcement_updated_at"
        private const val EXTRA_ANNOUNCEMENT_URL = "announcement_url"
        private const val EXTRA_CHANGELOG_TITLE = "changelog_title"
        private const val EXTRA_CHANGELOG_SUMMARY = "changelog_summary"
        private const val EXTRA_CHANGELOG_URL = "changelog_url"

        fun createIntent(
            context: Context,
            status: EkloseRemoteStatus?,
        ): Intent {
            return Intent(context, AnnouncementActivity::class.java).apply {
                putExtra(EXTRA_ANNOUNCEMENT_TITLE, status?.announcementTitle.orEmpty())
                putExtra(EXTRA_ANNOUNCEMENT_MESSAGE, status?.announcementMessage.orEmpty())
                putExtra(EXTRA_ANNOUNCEMENT_UPDATED_AT, status?.announcementUpdatedAt.orEmpty())
                putExtra(EXTRA_ANNOUNCEMENT_URL, status?.announcementUrl.orEmpty())
                putExtra(EXTRA_CHANGELOG_TITLE, status?.changelogTitle.orEmpty())
                putExtra(EXTRA_CHANGELOG_SUMMARY, status?.changelogSummary.orEmpty())
                putExtra(EXTRA_CHANGELOG_URL, status?.changelogUrl.orEmpty())
            }
        }

        private fun RemoteContent.Companion.fromIntent(intent: Intent): RemoteContent {
            return RemoteContent(
                announcementTitle = intent.getStringExtra(EXTRA_ANNOUNCEMENT_TITLE).orEmpty(),
                announcementMessage = intent.getStringExtra(EXTRA_ANNOUNCEMENT_MESSAGE).orEmpty(),
                announcementUpdatedAt = intent.getStringExtra(EXTRA_ANNOUNCEMENT_UPDATED_AT).orEmpty(),
                announcementUrl = intent.getStringExtra(EXTRA_ANNOUNCEMENT_URL).orEmpty(),
                changelogTitle = intent.getStringExtra(EXTRA_CHANGELOG_TITLE).orEmpty(),
                changelogSummary = intent.getStringExtra(EXTRA_CHANGELOG_SUMMARY).orEmpty(),
                changelogUrl = intent.getStringExtra(EXTRA_CHANGELOG_URL).orEmpty(),
            )
        }
    }
}

@Composable
private fun AnnouncementScreen(
    initialContent: RemoteContent,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersionName = remember(context) { context.currentVersionName() }
    var remoteStatus by remember { mutableStateOf(EkloseRemoteConfigManager.cachedStatus) }
    var content by remember { mutableStateOf(initialContent.withStatus(remoteStatus)) }
    var updateDialogStatus by remember { mutableStateOf<EkloseRemoteStatus?>(null) }
    var announcementBody by remember {
        mutableStateOf(content.announcementMessage.ifBlank { "暂无公告" })
    }
    var changelogBody by remember {
        mutableStateOf(content.changelogSummary.ifBlank { "暂无更新内容" })
    }
    var fetchStatus by remember {
        mutableStateOf(
            if (content.announcementUrl.isNotBlank() || content.changelogUrl.isNotBlank()) {
                "正在同步远程内容..."
            } else {
                "暂无远程内容地址，显示缓存内容"
            },
        )
    }

    LaunchedEffect(Unit) {
        if (remoteStatus == null) {
            runCatching {
                EkloseRemoteConfigManager.checkStatus(context)
            }.onSuccess {
                remoteStatus = it
                content = content.withStatus(it)
                announcementBody = content.announcementMessage.ifBlank { "暂无公告" }
                changelogBody = content.changelogSummary.ifBlank { "暂无更新内容" }
            }
        }
    }

    LaunchedEffect(content.announcementUrl, content.changelogUrl) {
        val announcementResult = fetchRemoteTextOrNull(content.announcementUrl)
        val changelogResult = fetchRemoteTextOrNull(content.changelogUrl)
        var successCount = 0
        if (!announcementResult.isNullOrBlank()) {
            announcementBody = parseRemoteContent(announcementResult)
            successCount++
        }
        if (!changelogResult.isNullOrBlank()) {
            changelogBody = parseCurrentVersionChangelog(changelogResult, currentVersionName)
                .ifBlank { parseRemoteContent(changelogResult) }
            successCount++
        }
        fetchStatus = when {
            content.announcementUrl.isBlank() && content.changelogUrl.isBlank() -> "暂无远程内容地址，显示缓存内容"
            successCount > 0 -> "获取成功，已显示当前版本 $currentVersionName 的更新日志"
            else -> "同步失败，已显示缓存内容"
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            EkloseTopBar(
                onBack = onBack,
                actions = {
                    TextButton(
                        text = "检测更新",
                        onClick = {
                            scope.launch {
                                runCatching { EkloseRemoteConfigManager.checkStatus(context) }
                                    .onSuccess { status ->
                                        if (status.showUpdateDialog) {
                                            updateDialogStatus = status
                                        } else {
                                            Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .onFailure {
                                        Toast.makeText(context, "检测更新失败", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        },
                    )
                },
            )
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
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MiuixTheme.colorScheme.surface,
                        contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    ) {
                        Text(
                            text = fetchStatus,
                            modifier = Modifier.padding(horizontal = 28.dp),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                item {
                    GroupSection(title = "公告") {
                        GroupItem(
                            title = content.announcementTitle.ifBlank { "公告" },
                            summary = announcementBody,
                            showArrow = false,
                        )
                        if (content.announcementUpdatedAt.isNotBlank()) {
                            GroupDivider()
                            GroupItem(
                                title = "更新时间",
                                summary = content.announcementUpdatedAt,
                                showArrow = false,
                            )
                        }
                    }
                }
                item {
                    GroupSection(title = "更新日志") {
                        GroupItem(
                            title = content.changelogTitle.ifBlank { "更新日志" },
                            summary = changelogBody,
                            showArrow = false,
                        )
                    }
                }
            }
        }
    }

    RemoteUpdateDialog(
        status = updateDialogStatus,
        onDismiss = { if (updateDialogStatus?.isForce != true) updateDialogStatus = null },
        onOpenUrl = { url ->
            if (url.isNotBlank()) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        },
    )
}

private data class RemoteContent(
    val announcementTitle: String,
    val announcementMessage: String,
    val announcementUpdatedAt: String,
    val announcementUrl: String,
    val changelogTitle: String,
    val changelogSummary: String,
    val changelogUrl: String,
) {
    companion object

    fun withStatus(status: EkloseRemoteStatus?): RemoteContent {
        if (status == null) return this
        return RemoteContent(
            announcementTitle = status.announcementTitle.ifBlank { announcementTitle },
            announcementMessage = status.announcementMessage.ifBlank { announcementMessage },
            announcementUpdatedAt = status.announcementUpdatedAt.ifBlank { announcementUpdatedAt },
            announcementUrl = status.announcementUrl.ifBlank { announcementUrl },
            changelogTitle = status.changelogTitle.ifBlank { changelogTitle },
            changelogSummary = status.changelogSummary.ifBlank { changelogSummary },
            changelogUrl = status.changelogUrl.ifBlank { changelogUrl },
        )
    }
}

private suspend fun fetchRemoteTextOrNull(url: String): String? {
    if (url.isBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as URLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            connection.getInputStream().bufferedReader().use { it.readText() }
        }.getOrNull()
    }
}

private fun parseRemoteContent(raw: String): String {
    return raw
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .lines()
        .map { line ->
            line.trim()
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^>\\s?"), "")
                .replace(Regex("^[-*+]\\s+"), "• ")
                .replace(Regex("\\[(.+?)]\\((.+?)\\)"), "$1")
                .replace(Regex("[*_`]+"), "")
        }
        .joinToString("\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun parseCurrentVersionChangelog(raw: String, versionName: String): String {
    val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
    val versionHeader = Regex("^##\\s+\\[?${Regex.escape(versionName)}]?\\b.*$", RegexOption.MULTILINE)
    val match = versionHeader.find(normalized) ?: return ""
    val nextHeader = Regex("^##\\s+", RegexOption.MULTILINE).find(normalized, match.range.last + 1)
    val sectionEnd = nextHeader?.range?.first ?: normalized.length
    return parseRemoteContent(normalized.substring(match.range.first, sectionEnd))
}

private fun Context.currentVersionName(): String {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    return packageInfo.versionName ?: ""
}

@Composable
private fun RemoteUpdateDialog(
    status: EkloseRemoteStatus?,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    OverlayDialog(
        show = status != null,
        title = if (status?.isForce == true) "需要更新" else "发现新版本",
        summary = status?.message,
        onDismissRequest = if (status?.isForce == true) null else onDismiss,
    ) {
        if (status != null) {
            TextButton(
                text = "立即更新",
                onClick = {
                    onOpenUrl(status.updateUrl)
                    if (!status.isForce) onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.primary,
                    textColor = MiuixTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
