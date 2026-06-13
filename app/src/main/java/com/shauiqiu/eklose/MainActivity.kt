package com.shauiqiu.eklose

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.shauiqiu.eklose.ui.theme.EkloseTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    private var openHomeRequest by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openHomeRequest = intent.openHomeRequest()
        enableEdgeToEdge()
        setContent {
            EkloseTheme {
                EkloseApp(openHomeRequest = openHomeRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val request = intent.openHomeRequest()
        if (request != 0L) {
            openHomeRequest = request
        }
    }

    companion object {
        private const val EXTRA_OPEN_HOME = "com.shauiqiu.eklose.extra.OPEN_HOME"

        fun createHomeIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_HOME, System.currentTimeMillis())
            }
        }

        private fun Intent.openHomeRequest(): Long {
            return if (hasExtra(EXTRA_OPEN_HOME)) {
                getLongExtra(EXTRA_OPEN_HOME, 0L)
            } else {
                0L
            }
        }
    }
}

private enum class RootPage(
    val label: String,
    val icon: ImageVector
) {
    Home("主页", MiuixIcons.Basic.Check),
    Reader("读取", MiuixIcons.Basic.Search),
}

private enum class HomeworkScope(
    val label: String,
    val readerSummary: String,
    val answerSource: EkwingAnswerSource,
) {
    Current(
        label = "当前学习中心考试任务",
        readerSummary = "读取当前学习中心考试任务，进入考试后再读取答案",
        answerSource = EkwingAnswerSource.Current,
    ),
    History(
        label = "历史学习中心考试任务",
        readerSummary = "读取历史学习中心考试任务，进入考试后再读取答案",
        answerSource = EkwingAnswerSource.History,
    ),
}

private data class RemoteDialogState(
    val title: String,
    val summary: String,
    val updateUrl: String = "",
    val locked: Boolean = false,
    val primaryActionText: String = "确定",
)

@Composable
private fun EkloseApp(openHomeRequest: Long = 0L) {
    var selectedPage by rememberSaveable { mutableStateOf(RootPage.Home) }
    var selectedHomeworkScopeIndex by rememberSaveable { mutableStateOf(0) }
    var readerSummary by rememberSaveable { mutableStateOf<String?>(null) }
    var isReading by rememberSaveable { mutableStateOf(false) }
    var remoteStatus by remember { mutableStateOf(EkloseApplication.remoteStatus ?: EkloseRemoteConfigManager.cachedStatus) }
    var remoteDialog by remember { mutableStateOf<RemoteDialogState?>(null) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedHomeworkScope = HomeworkScope.entries[selectedHomeworkScopeIndex]
    val answerStates by EkwingAnswerState.states.collectAsState()
    val selectedSourceState = answerStates[selectedHomeworkScope.answerSource] ?: EkwingAnswerSourceState()

    LaunchedEffect(Unit) {
        EkwingAnswerCacheStore.restore(context)
    }

    LaunchedEffect(Unit) {
        EkloseApplication.remoteStatusFlow.collect { status ->
            remoteStatus = status
        }
    }

    LaunchedEffect(Unit) {
        EkloseApplication.updateStatusFlow.collect { status ->
            if (status != null && status.showUpdateDialog) {
                remoteDialog = status.toUpdateDialog()
            }
        }
    }

    LaunchedEffect(openHomeRequest) {
        if (openHomeRequest != 0L) {
            selectedPage = RootPage.Home
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            EkloseTopBar(
                actions = {
                    if (selectedPage == RootPage.Home) {
                        IconButton(
                            onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Settings,
                                contentDescription = "设置",
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                RootPage.entries.forEach { page ->
                    NavigationBarItem(
                        selected = selectedPage == page,
                        onClick = { selectedPage = page },
                        icon = page.icon,
                        label = page.label,
                    )
                }
            }
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
            AnimatedContent(
                targetState = selectedPage,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (
                        slideInHorizontally(
                            animationSpec = tween(durationMillis = 260),
                            initialOffsetX = { fullWidth -> direction * fullWidth },
                        ) + fadeIn(animationSpec = tween(durationMillis = 180))
                        ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(durationMillis = 260),
                            targetOffsetX = { fullWidth -> -direction * fullWidth },
                        ) + fadeOut(animationSpec = tween(durationMillis = 180))
                    )
                },
                label = "RootPageTransition",
            ) { page ->
                when (page) {
                    RootPage.Home -> HomePage(
                        remoteStatus = remoteStatus,
                        onShowAbout = { showAboutDialog = true },
                    )
                    RootPage.Reader -> ReaderPage(
                        homeworkScope = selectedHomeworkScope,
                        readerSummary = readerSummary,
                        selectedHomeworkScopeIndex = selectedHomeworkScopeIndex,
                        onHomeworkScopeChange = {
                            selectedHomeworkScopeIndex = it
                            readerSummary = null
                        },
                        sourceState = selectedSourceState,
                        isReading = isReading,
                        onRead = {
                            if (isReading) return@ReaderPage
                            isReading = true
                            readerSummary = "正在读取${selectedHomeworkScope.label}..."
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        EkwingAnswerReader(context).readPapers(
                                            history = selectedHomeworkScope == HomeworkScope.History,
                                        )
                                    }
                                }.onSuccess { result ->
                                    readerSummary = "已加载 ${result.size} 份${selectedHomeworkScope.label}，正在后台解析答案"
                                }.onFailure { error ->
                                    readerSummary = "读取失败：${error.message}"
                                }
                                isReading = false
                            }
                        },
                        onDelete = {
                            EkwingAnswerPrefetchManager.cancelAll()
                            EkwingAnswerCacheStore.clear(context)
                            readerSummary = "已清空读取结果"
                        },
                    )
                }
            }
        }
        RemoteManagementDialog(
            dialog = remoteDialog,
            onDismiss = { if (remoteDialog?.locked != true) remoteDialog = null },
            onOpenUrl = { url ->
                if (url.isNotBlank()) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
        )
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
private fun RemoteManagementDialog(
    dialog: RemoteDialogState?,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    OverlayDialog(
        show = dialog != null,
        title = dialog?.title,
        summary = dialog?.summary,
        onDismissRequest = if (dialog?.locked == true) null else onDismiss,
    ) {
        if (dialog != null) {
            if (dialog.updateUrl.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (!dialog.locked) {
                        TextButton(
                            text = "稍后",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                    }
                    TextButton(
                        text = dialog.primaryActionText,
                        onClick = {
                            onOpenUrl(dialog.updateUrl)
                            if (!dialog.locked) onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            color = MiuixTheme.colorScheme.primary,
                            textColor = MiuixTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            } else if (!dialog.locked) {
                TextButton(
                    text = dialog.primaryActionText,
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
}

private fun EkloseRemoteStatus.toStartupDialog(): RemoteDialogState? {
    return when {
        isKillSwitch -> RemoteDialogState(
            title = "远程管理",
            summary = message.ifBlank { "程序异常，请等待远程恢复。" },
            locked = true,
        )
        showUpdateDialog -> RemoteDialogState(
            title = if (isForce) "需要更新" else "发现新版本",
            summary = message.ifBlank { "发现新版本，请更新后继续使用。" },
            updateUrl = updateUrl,
            locked = isForce,
            primaryActionText = "立即更新",
        )
        else -> null
    }
}

private fun EkloseRemoteStatus.toUpdateDialog(): RemoteDialogState = toStartupDialog()
    ?: RemoteDialogState(
        title = if (isForce) "需要更新" else "发现新版本",
        summary = message.ifBlank { "发现新版本，请更新后继续使用。" },
        updateUrl = updateUrl,
        locked = isForce,
        primaryActionText = "立即更新",
    )

@Composable
private fun ReaderFloatingToolbar(
    onRead: () -> Unit,
    onDelete: () -> Unit,
) {
    FloatingToolbar {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onRead) {
                Icon(
                    imageVector = MiuixIcons.Basic.Search,
                    contentDescription = "读取",
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = "删除",
                )
            }
        }
    }
}

@Composable
private fun HomePage(
    remoteStatus: EkloseRemoteStatus?,
    onShowAbout: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var loginSession by remember { mutableStateOf(EkwingLoginStore.loadSession(context)) }
    val announcementTitle = remoteStatus?.announcementTitle?.takeIf { it.isNotBlank() } ?: "公告"
    val announcementSummary = remoteStatus?.announcementMessage?.takeIf { it.isNotBlank() }
        ?: "云端读取前请确认没有正在进行考试、练习、录音或提交。"
    val changelogTitle = remoteStatus?.changelogTitle?.takeIf { it.isNotBlank() } ?: "更新日志"
    val changelogSummary = remoteStatus?.changelogSummary?.takeIf { it.isNotBlank() }
        ?: "新增读取页考试来源切换与试卷列表预览。"

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loginSession = EkwingLoginStore.loadSession(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageLead(
                title = "主页",
                summary = "管理翼课账号和云端读取配置",
            )
        }
        item {
            GroupSection(title = "账号") {
                GroupItem(
                    title = "登录状态",
                    summary = loginSession?.let { "uid=${it.uid}" } ?: "点击登录翼课账号",
                    trailing = { LoginStatusIndicator(text = if (loginSession == null) "登录" else "已登录") },
                    onClick = {
                        if (loginSession == null) {
                            context.startActivity(Intent(context, LoginActivity::class.java))
                        } else {
                            EkwingLoginStore.clearSession(context)
                            loginSession = null
                        }
                    },
                )
            }
        }
        item {
            GroupSection(title = "公告与更新") {
                GroupItem(
                    title = announcementTitle,
                    summary = announcementSummary,
                    onClick = {
                        context.startActivity(AnnouncementActivity.createIntent(context, remoteStatus))
                    },
                )
                GroupDivider()
                GroupItem(
                    title = changelogTitle,
                    summary = changelogSummary,
                    onClick = {
                        context.startActivity(AnnouncementActivity.createIntent(context, remoteStatus))
                    },
                )
            }
        }
        item {
            GroupSection(title = "其他") {
                GroupItem(
                    title = "关于翼课校长",
                    summary = "应用信息、官网与致谢",
                    onClick = onShowAbout,
                )
            }
        }
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
        title = "关于翼课校长",
        summary = "翼课学习中心答案读取工具",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AboutInfoBlock(
                title = "软件信息",
                imageResId = R.drawable.app_icon,
                imageDescription = "应用图标",
                lines = listOf(
                    "翼课校长",
                    "版本: ${versionName.ifBlank { "未知" }}",
                    "开源协议: GPL 3.0",
                ),
            )
            AboutInfoBlock(
                title = "关于作者",
                imageResId = R.drawable.ic_author,
                imageDescription = "作者头像",
                lines = listOf(
                    "帅丘",
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
                    title = "访问官网",
                    summary = "lastudio.cc",
                    onClick = { onOpenUrl("https://lastudio.cc") },
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
    imageResId: Int,
    imageDescription: String,
    lines: List<String>,
) {
    GroupSection(title = title) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MiuixTheme.colorScheme.surface,
                shadowElevation = 0.dp,
            ) {
                Image(
                    painter = painterResource(id = imageResId),
                    contentDescription = imageDescription,
                    modifier = Modifier.size(52.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
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
}

private fun Context.currentVersionName(): String {
    return packageManager.getPackageInfo(packageName, 0).versionName ?: ""
}

@Composable
private fun LoginStatusIndicator(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(
                    color = MiuixTheme.colorScheme.primary,
                    shape = CircleShape,
                ),
        )
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun ReaderPage(
    homeworkScope: HomeworkScope,
    readerSummary: String?,
    selectedHomeworkScopeIndex: Int,
    onHomeworkScopeChange: (Int) -> Unit,
    sourceState: EkwingAnswerSourceState,
    isReading: Boolean,
    onRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val papers = sourceState.papers
    val pullToRefreshState = rememberPullToRefreshState()

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefresh(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = isReading,
            onRefresh = onRead,
            pullToRefreshState = pullToRefreshState,
            refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新完成"),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    PageLead(
                        title = "读取",
                        summary = readerSummary ?: homeworkScope.readerSummary,
                    )
                }
                item {
                    GroupSection {
                        OverlayDropdownPreference(
                            title = "读取考试",
                            summary = "选择读取页使用的考试来源",
                            items = HomeworkScope.entries.map { it.label },
                            selectedIndex = selectedHomeworkScopeIndex,
                            onSelectedIndexChange = onHomeworkScopeChange,
                        )
                    }
                }
                item {
                    GroupSection(title = "试卷列表") {
                        papers.forEachIndexed { index, paper ->
                            val parseState = sourceState.parseStateByPaperKey[paper.key]
                                ?: EkwingAnswerParseState(EkwingAnswerParseStatus.Pending)
                            PaperPreference(
                                paper = paper,
                                parseState = parseState,
                                onClick = {
                                    context.startActivity(
                                        AnswerActivity.createIntent(
                                            context = context,
                                            paperTitle = paper.title,
                                            paperSummary = paper.summary,
                                            paperKey = paper.key,
                                            source = HomeworkScope.entries[selectedHomeworkScopeIndex].answerSource,
                                        )
                                    )
                                },
                            )
                            if (index != papers.lastIndex) {
                                GroupDivider()
                            }
                        }
                        if (papers.isEmpty()) {
                            GroupItem(
                                title = if (isReading) "正在读取" else "暂无试卷",
                                summary = if (isReading) "请稍候" else "点击右下角读取按钮加载考试列表",
                                showArrow = false,
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            ReaderFloatingToolbar(
                onRead = onRead,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun PaperPreference(
    paper: EkwingAnswerPaper,
    parseState: EkwingAnswerParseState,
    onClick: () -> Unit,
) {
    val statusSummary = when (parseState.status) {
        EkwingAnswerParseStatus.Pending -> "${paper.summary} · 等待解析"
        EkwingAnswerParseStatus.Loading -> "${paper.summary} · 正在解析"
        EkwingAnswerParseStatus.Ready -> paper.summary
        EkwingAnswerParseStatus.Failed -> "${paper.summary} · 解析失败：${parseState.errorMessage ?: "未知错误"}"
    }
    if (canOpenPaper(parseState)) {
        ArrowPreference(
            title = paper.title,
            summary = statusSummary,
            onClick = onClick,
        )
    } else {
        GroupItem(
            title = paper.title,
            summary = statusSummary,
            trailing = {
                if (parseState.status == EkwingAnswerParseStatus.Pending || parseState.status == EkwingAnswerParseStatus.Loading) {
                    InfiniteProgressIndicator(
                        color = MiuixTheme.colorScheme.primary,
                        size = 20.dp,
                    )
                }
            },
            showArrow = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EklosePreview() {
    EkloseTheme {
        EkloseApp()
    }
}
