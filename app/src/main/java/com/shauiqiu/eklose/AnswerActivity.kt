package com.shauiqiu.eklose

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.os.Bundle
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shauiqiu.eklose.ui.theme.EkloseTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class AnswerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val paperTitle = intent.getStringExtra(EXTRA_PAPER_TITLE).orEmpty()
        val paperSummary = intent.getStringExtra(EXTRA_PAPER_SUMMARY).orEmpty()
        val paperKey = intent.getStringExtra(EXTRA_PAPER_KEY).orEmpty()
        val source = intent.getStringExtra(EXTRA_SOURCE)
            ?.let { raw -> runCatching { EkwingAnswerSource.valueOf(raw) }.getOrNull() }
            ?: EkwingAnswerSource.Current

        enableEdgeToEdge()
        setContent {
            EkloseTheme {
                AnswerScreen(
                    paperTitle = paperTitle,
                    paperSummary = paperSummary,
                    paperKey = paperKey,
                    source = source,
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PAPER_TITLE = "paper_title"
        private const val EXTRA_PAPER_SUMMARY = "paper_summary"
        private const val EXTRA_PAPER_KEY = "paper_key"
        private const val EXTRA_SOURCE = "source"

        fun createIntent(
            context: Context,
            paperTitle: String,
            paperSummary: String,
            paperKey: String = "",
            source: EkwingAnswerSource = EkwingAnswerSource.Current,
        ): Intent {
            return Intent(context, AnswerActivity::class.java)
                .putExtra(EXTRA_PAPER_TITLE, paperTitle)
                .putExtra(EXTRA_PAPER_SUMMARY, paperSummary)
                .putExtra(EXTRA_PAPER_KEY, paperKey)
                .putExtra(EXTRA_SOURCE, source.name)
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

private const val SHARE_FOOTER_TEXT = "翼课校长守护你的答案喵~ 官网:lastudio.cc"

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
    source: EkwingAnswerSource,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var selectedAnswer by remember { mutableStateOf<SelectedAnswer?>(null) }
    val states by EkwingAnswerState.states.collectAsState()
    val sourceState = states[source] ?: EkwingAnswerSourceState()
    val parseState = sourceState.parseStateByPaperKey[paperKey]
    val loadedSections = sourceState.sectionsByPaperKey[paperKey]?.toUiAnswerSections()
    val answerSections = loadedSections ?: answerPlaceholderSections(paperKey, parseState)
    val shareableSections = loadedSections.orEmpty().filter(AnswerSection::isShareable)
    val canShareAnswers = shareableSections.isNotEmpty()
    val questionCount = answerSections.sumOf { it.questions.size }
    val topBarTitle = paperTitle.ifBlank { "答案" }
    val scrollBehavior = MiuixScrollBehavior()
    val shareEntry = remember(paperTitle, answerSections, canShareAnswers) {
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = "复制答案",
                    enabled = canShareAnswers,
                    onClick = {
                        copyAnswerText(
                            context = context,
                            paperTitle = topBarTitle,
                            sections = shareableSections,
                        )
                    },
                ),
                DropdownItem(
                    text = "分享图片",
                    enabled = canShareAnswers,
                    onClick = {
                        shareAnswerImage(
                            context = context,
                            paperTitle = topBarTitle,
                            sections = shareableSections,
                        )
                    },
                ),
            ),
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = topBarTitle,
                largeTitle = topBarTitle,
                subtitle = "$questionCount 道题目",
                color = MiuixTheme.colorScheme.surface,
                titleColor = MiuixTheme.colorScheme.onSurface,
                largeTitleColor = MiuixTheme.colorScheme.onSurface,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = "返回",
                            modifier = Modifier.graphicsLayer(scaleX = -1f),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    OverlayIconDropdownMenu(entry = shareEntry) {
                        Icon(
                            imageVector = MiuixIcons.Share,
                            contentDescription = "分享",
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
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
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
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

private fun answerPlaceholderSections(
    paperKey: String,
    parseState: EkwingAnswerParseState?,
): List<AnswerSection> {
    if (paperKey.isBlank()) {
        return listOf(
            EkwingAnswerAssembler.readFailureSection(
                RuntimeException("未找到考试，请先读取考试列表")
            )
        ).toUiAnswerSections()
    }
    if (parseState?.status == EkwingAnswerParseStatus.Failed) {
        return listOf(
            EkwingAnswerAssembler.readFailureSection(
                RuntimeException(parseState.errorMessage ?: "答案解析失败")
            )
        ).toUiAnswerSections()
    }
    return loadingAnswerSections
}

private fun AnswerSection.isShareable(): Boolean {
    return questions.isNotEmpty() && category != "loading" && category != "error"
}

private fun copyAnswerText(
    context: Context,
    paperTitle: String,
    sections: List<AnswerSection>,
) {
    val text = formatAnswerAsText(paperTitle, sections)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(paperTitle, text))
    Toast.makeText(context, "已复制答案到剪贴板", Toast.LENGTH_SHORT).show()
}

private fun formatAnswerAsText(
    paperTitle: String,
    sections: List<AnswerSection>,
): String {
    val builder = StringBuilder()
    builder.appendLine(paperTitle)
    builder.appendLine()
    sections.forEach { section ->
        builder.appendLine(section.title)
        builder.appendLine()
        section.questions.forEach { question ->
            builder.append(question.order).append(". ")
            builder.appendLine(formatQuestionShareAnswer(section, question))
        }
        builder.appendLine()
    }
    builder.appendLine(SHARE_FOOTER_TEXT)
    return builder.toString().trimEnd()
}

private fun shareAnswerImage(
    context: Context,
    paperTitle: String,
    sections: List<AnswerSection>,
) {
    try {
        val bitmap = renderAnswerImage(paperTitle, sections)
        val fileName = "YikePrincipal_Answer_${System.currentTimeMillis()}.png"
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/翼课校长")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri == null) {
            bitmap.recycle()
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
            return
        }
        resolver.openOutputStream(uri).use { output ->
            if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                bitmap.recycle()
                Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        }
        bitmap.recycle()
        Toast.makeText(context, "已保存到图片/翼课校长", Toast.LENGTH_SHORT).show()
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, paperTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(shareIntent, "分享图片"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "没有可用的分享应用", Toast.LENGTH_SHORT).show()
        }
    } catch (error: Exception) {
        Log.e("EkloseShareImage", "Share image failed", error)
        Toast.makeText(context, "分享图片失败", Toast.LENGTH_SHORT).show()
    }
}

private fun renderAnswerImage(
    paperTitle: String,
    sections: List<AnswerSection>,
): Bitmap {
    val width = 1080
    val padding = 60
    val contentWidth = width - padding * 2
    val lineSpacing = 16f
    val sectionGap = 32f
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
        color = AndroidColor.parseColor("#222222")
    }
    val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
        color = AndroidColor.parseColor("#007AFF")
    }
    val answerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 30f
        color = AndroidColor.parseColor("#333333")
    }
    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = AndroidColor.parseColor("#999999")
    }

    fun layoutHeight(text: String, paint: TextPaint): Int {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(lineSpacing, 1f)
            .build()
            .height
    }

    var totalHeight = padding
    totalHeight += layoutHeight(paperTitle, titlePaint) + sectionGap.toInt()
    sections.forEach { section ->
        totalHeight += layoutHeight(section.title, sectionPaint) + lineSpacing.toInt()
        section.questions.forEach { question ->
            totalHeight += layoutHeight("${question.order}. ${formatQuestionShareAnswer(section, question)}", answerPaint) +
                lineSpacing.toInt()
        }
        totalHeight += sectionGap.toInt()
    }
    totalHeight += layoutHeight(SHARE_FOOTER_TEXT, footerPaint) + padding

    val bitmap = Bitmap.createBitmap(width, totalHeight.coerceAtLeast(width), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)
    var y = padding.toFloat()

    fun drawTextLayout(
        text: String,
        paint: TextPaint,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    ) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
            .setAlignment(alignment)
            .setLineSpacing(lineSpacing, 1f)
            .build()
        canvas.save()
        canvas.translate(padding.toFloat(), y)
        layout.draw(canvas)
        canvas.restore()
        y += layout.height
    }

    drawTextLayout(paperTitle, titlePaint, Layout.Alignment.ALIGN_CENTER)
    y += sectionGap
    sections.forEach { section ->
        drawTextLayout(section.title, sectionPaint)
        y += lineSpacing
        section.questions.forEach { question ->
            drawTextLayout("${question.order}. ${formatQuestionShareAnswer(section, question)}", answerPaint)
            y += lineSpacing
        }
        y += sectionGap - lineSpacing
    }
    drawTextLayout(SHARE_FOOTER_TEXT, footerPaint, Layout.Alignment.ALIGN_CENTER)
    return bitmap
}

private fun cleanShareText(value: String): String {
    return value.lines()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("\n")
}

private fun formatQuestionShareAnswer(
    section: AnswerSection,
    question: AnswerQuestion,
): String {
    val answers = splitAnswerCandidates(question.answer)
    val takeCount = if (section.isLongSentenceShareSection()) 1 else 2
    return answers.take(takeCount).joinToString("\n").ifBlank { cleanShareText(question.answer) }
}

private fun splitAnswerCandidates(value: String): List<String> {
    val cleaned = cleanShareText(value)
    if (cleaned.isBlank()) return emptyList()
    return cleaned.lines()
        .flatMap { line ->
            line.split(Regex("""\s*(?:[;；]|(?:\s*/\s*)|(?:\s*\|\s*))\s*"""))
        }
        .map { item ->
            item.replace(Regex("""^(?:标准答案|参考答案|答案|Answer)\s*[:：]\s*"""), "")
                .trim()
        }
        .filter(String::isNotEmpty)
}

private fun AnswerSection.isLongSentenceShareSection(): Boolean {
    val marker = listOf(title, category, originalText).joinToString(" ")
    return marker.contains("转述") ||
        marker.contains("复述") ||
        marker.contains("retell", ignoreCase = true) ||
        marker.contains("retelling", ignoreCase = true) ||
        marker.contains("topic", ignoreCase = true)
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
