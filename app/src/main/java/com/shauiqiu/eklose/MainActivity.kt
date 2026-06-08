package com.shauiqiu.eklose

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.shauiqiu.eklose.ui.theme.EkloseTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Settings
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
) {
    Current(
        label = "当前学习中心考试任务",
        readerSummary = "读取当前学习中心考试任务，进入考试后再读取答案",
    ),
    History(
        label = "历史学习中心考试任务",
        readerSummary = "读取历史学习中心考试任务，进入考试后再读取答案",
    ),
}

@Composable
private fun EkloseApp(openHomeRequest: Long = 0L) {
    var selectedPage by rememberSaveable { mutableStateOf(RootPage.Home) }
    var selectedHomeworkScopeIndex by rememberSaveable { mutableStateOf(0) }
    var readerSummary by rememberSaveable { mutableStateOf<String?>(null) }
    var papers by remember { mutableStateOf(EkwingAnswerState.papers) }
    var isReading by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedHomeworkScope = HomeworkScope.entries[selectedHomeworkScopeIndex]

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
                    RootPage.Home -> HomePage()
                    RootPage.Reader -> ReaderPage(
                        homeworkScope = selectedHomeworkScope,
                        readerSummary = readerSummary,
                        selectedHomeworkScopeIndex = selectedHomeworkScopeIndex,
                        onHomeworkScopeChange = { selectedHomeworkScopeIndex = it },
                        papers = papers,
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
                                    papers = result
                                    readerSummary = "已加载 ${result.size} 份${selectedHomeworkScope.label}，进入考试后读取答案"
                                }.onFailure { error ->
                                    readerSummary = "读取失败：${error.message}"
                                }
                                isReading = false
                            }
                        },
                        onDelete = {
                            EkwingAnswerState.clear()
                            papers = emptyList()
                            readerSummary = "已清空读取结果"
                        },
                    )
                }
            }
        }
    }
}

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
private fun HomePage() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var loginSession by remember { mutableStateOf(EkwingLoginStore.loadSession(context)) }

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
                    title = "公告",
                    summary = "云端读取前请确认没有正在进行考试、练习、录音或提交。",
                    onClick = { context.startActivity(Intent(context, AnnouncementActivity::class.java)) },
                    showArrow = false,
                )
                GroupDivider()
                GroupItem(
                    title = "更新日志",
                    summary = "新增读取页考试来源切换与试卷列表预览。",
                    onClick = { context.startActivity(Intent(context, AnnouncementActivity::class.java)) },
                )
            }
        }
    }
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
    papers: List<EkwingAnswerPaper>,
    isReading: Boolean,
    onRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
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
                        ArrowPreference(
                            title = paper.title,
                            summary = paper.summary,
                            onClick = {
                                context.startActivity(
                                    AnswerActivity.createIntent(
                                        context = context,
                                        paperTitle = paper.title,
                                        paperSummary = paper.summary,
                                        paperKey = paper.key,
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

@Preview(showBackground = true)
@Composable
private fun EklosePreview() {
    EkloseTheme {
        EkloseApp()
    }
}
