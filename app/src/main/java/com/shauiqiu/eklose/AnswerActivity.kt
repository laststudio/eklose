package com.shauiqiu.eklose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shauiqiu.eklose.ui.theme.EkloseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class AnswerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val paperTitle = intent.getStringExtra(EXTRA_PAPER_TITLE).orEmpty()
        val paperSummary = intent.getStringExtra(EXTRA_PAPER_SUMMARY).orEmpty()
        val paperKey = intent.getStringExtra(EXTRA_PAPER_KEY).orEmpty()

        enableEdgeToEdge()
        setContent {
            EkloseTheme {
                AnswerScreen(
                    paperTitle = paperTitle,
                    paperSummary = paperSummary,
                    paperKey = paperKey,
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PAPER_TITLE = "paper_title"
        private const val EXTRA_PAPER_SUMMARY = "paper_summary"
        private const val EXTRA_PAPER_KEY = "paper_key"

        fun createIntent(
            context: Context,
            paperTitle: String,
            paperSummary: String,
            paperKey: String = "",
        ): Intent {
            return Intent(context, AnswerActivity::class.java)
                .putExtra(EXTRA_PAPER_TITLE, paperTitle)
                .putExtra(EXTRA_PAPER_SUMMARY, paperSummary)
                .putExtra(EXTRA_PAPER_KEY, paperKey)
        }
    }
}

private data class AnswerSection(
    val title: String,
    val category: String,
    val originalText: String,
    val questions: List<AnswerQuestion>,
)

private data class AnswerQuestion(
    val order: String,
    val question: String,
    val answer: String,
)

private val loadingAnswerSections = listOf(
    AnswerSection(
        title = "正在读取答案",
        category = "loading",
        originalText = "正在加载当前考试的题目和答案",
        questions = listOf(
            AnswerQuestion(
                order = "Q1",
                question = "请稍候",
                answer = "答案读取完成后会显示在这里",
            )
        ),
    )
)

private fun List<EkwingAnswerSection>.toUiAnswerSections(): List<AnswerSection> {
    return map { section ->
        AnswerSection(
            title = section.title,
            category = section.category,
            originalText = section.originalText,
            questions = section.questions.map { question ->
                AnswerQuestion(
                    order = question.order,
                    question = question.question,
                    answer = question.answer,
                )
            },
        )
    }
}

@Composable
private fun AnswerScreen(
    paperTitle: String,
    paperSummary: String,
    paperKey: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var selectedAnswer by remember { mutableStateOf<SelectedAnswer?>(null) }
    var answerSections by remember(paperKey) {
        mutableStateOf(
            EkwingAnswerState.sectionsByPaperKey[paperKey]?.toUiAnswerSections()
                ?: loadingAnswerSections,
        )
    }

    LaunchedEffect(paperKey) {
        if (paperKey.isBlank()) {
            answerSections = listOf(
                EkwingAnswerAssembler.readFailureSection(
                    RuntimeException("未找到考试，请先读取考试列表")
                )
            ).toUiAnswerSections()
            return@LaunchedEffect
        }
        if (EkwingAnswerState.sectionsByPaperKey.containsKey(paperKey)) return@LaunchedEffect
        val loadedSections = withContext(Dispatchers.IO) {
            runCatching {
                EkwingAnswerReader(context).readAnswerSections(paperKey)
            }.getOrElse { error ->
                listOf(EkwingAnswerAssembler.readFailureSection(error))
            }
        }
        answerSections = loadedSections.toUiAnswerSections()
    }
    val questionCount = answerSections.sumOf { it.questions.size }

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
                        title = paperTitle.ifBlank { "答案" },
                        summary = "$questionCount 道题目 · ${paperSummary.ifBlank { "等待读取答案" }}",
                    )
                }
                item {
                    GroupSection(title = "试卷概览") {
                        GroupItem(
                            title = paperTitle.ifBlank { "未知试卷" },
                            summary = paperSummary.ifBlank { "暂无试卷信息" },
                            value = "${answerSections.size} 个分区",
                            showArrow = false,
                        )
                    }
                }
                answerSections.forEach { section ->
                    item {
                        AnswerSectionBlock(
                            section = section,
                            onShowAnswer = { question ->
                                selectedAnswer = SelectedAnswer(
                                    sectionTitle = section.title,
                                    question = question,
                                )
                            },
                        )
                    }
                }
            }
            AnswerBottomSheet(
                selectedAnswer = selectedAnswer,
                onDismiss = { selectedAnswer = null },
            )
        }
    }
}

private data class SelectedAnswer(
    val sectionTitle: String,
    val question: AnswerQuestion,
)

@Composable
private fun AnswerSectionBlock(
    section: AnswerSection,
    onShowAnswer: (AnswerQuestion) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallTitle(text = section.title)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            border = BorderStroke(0.6.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.16f)),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionHeader(section = section)
                InfoPanel(
                    title = "原文",
                    content = section.originalText,
                )
                section.questions.forEachIndexed { index, question ->
                    QuestionBlock(
                        sectionTitle = section.title,
                        question = question,
                        onShowAnswer = { onShowAnswer(question) },
                    )
                    if (index != section.questions.lastIndex) {
                        GroupDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: AnswerSection) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 28.dp)
                .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.title,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
            )
            Text(
                text = "${section.questions.size} 道题目",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Surface(
            shape = CircleShape,
            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MiuixTheme.colorScheme.primary,
        ) {
            Text(
                text = section.category,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun QuestionBlock(
    sectionTitle: String,
    question: AnswerQuestion,
    onShowAnswer: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            title = question.order,
            titleColor = BasicComponentDefaults.titleColor(
                color = MiuixTheme.colorScheme.primary,
            ),
            summary = sectionTitle,
            summaryColor = BasicComponentDefaults.summaryColor(
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            ),
            insideMargin = PaddingValues(0.dp),
        )
        InfoPanel(
            title = "题目",
            content = question.question,
        )
        ArrowPreference(
            title = "答案",
            summary = "点击查看标准答案",
            onClick = onShowAnswer,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AnswerBottomSheet(
    selectedAnswer: SelectedAnswer?,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = selectedAnswer != null,
        title = selectedAnswer?.question?.order?.let { "$it 答案" },
        onDismissRequest = onDismiss,
    ) {
        if (selectedAnswer != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GroupItem(
                    title = selectedAnswer.sectionTitle,
                    summary = selectedAnswer.question.question,
                    showArrow = false,
                )
                InfoPanel(
                    title = "标准答案",
                    content = selectedAnswer.question.answer,
                    emphasized = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun InfoPanel(
    title: String,
    content: String,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (emphasized) {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MiuixTheme.colorScheme.surface
        },
        contentColor = MiuixTheme.colorScheme.onSurface,
        border = BorderStroke(0.6.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.12f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
                color = if (emphasized) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
            Text(
                text = content,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}
