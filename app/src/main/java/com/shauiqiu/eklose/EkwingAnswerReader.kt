package com.shauiqiu.eklose

import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

private const val EK_BASE_URL = "https://mapi.ekwing.com"
private const val HOMEWORK_LIST_PATH = "/student/Hw/getList"
private const val BASIC_HOMEWORK_LIST_PATH = "/student/Hw/getBasicList"
private const val STUDY_CENTER_PATH = "/student/Hw/getnewmainlist"
private const val BASIC_STUDY_CENTER_PATH = "/student/Hw/getbasicnewmainlist"
private const val HOMEWORK_ITEMS_PATH = "/student/Hw/getHwItems"
private const val BASIC_HOMEWORK_ITEMS_PATH = "/student/Hw/getBasicHwItems"
private const val SCORE_DETAIL_PATH = "/student/Hw/stuscoredetail"
private const val BASIC_SCORE_DETAIL_PATH = "/student/Hw/stubasicscoredetail"
private const val HW_DO_ITEM_PATH = "/student/Hw/hwdoitem"
private const val HW_ANSWER_PATH = "/student/Hw/getHwAns"
private const val HW_COUNT_PATH = "/student/Hw/gethwcnt"
private const val HW_HISTORY_SCORE_PATH = "/student/Hw/jshistoryitemScore"
private const val HW_RESULT_PATH = "/student/Hw/GetHwResult"
private const val TRAIN_ITEM_ANSWER_PATH = "/student/train/getitemans"
private const val TRAIN_JS_ITEM_ANSWER_PATH = "/student/train/getjsitemans"
private const val EXAM_HISTORY_PATH = "/student/exam/getstuexamlist"
private const val BASIC_EXAM_HISTORY_PATH = "/student/exam/getstubasicexamlist"
private const val EXAM_ITEM_PATH = "/student/exam/getstuexamitem"
private const val EXAM_SCORE_PATH = "/student/exam/getscoreinfo"
private const val BASIC_EXAM_SCORE_PATH = "/student/exam/getbasicscoreinfo"
private const val GET_MODEL_SCORE_PATH = "/student/exam/getmodelscoreinfo"

data class EkwingAnswerPaper(
    val key: String,
    val title: String,
    val summary: String,
)

data class EkwingAnswerSection(
    val title: String,
    val category: String,
    val originalText: String,
    val questions: List<EkwingAnswerQuestion>,
)

data class EkwingAnswerQuestion(
    val order: String,
    val question: String,
    val answer: String,
)

object EkwingAnswerState {
    var papers: List<EkwingAnswerPaper> = emptyList()
    var taskByPaperKey: Map<String, String> = emptyMap()
    var sectionsByPaperKey: Map<String, List<EkwingAnswerSection>> = emptyMap()

    fun clear() {
        papers = emptyList()
        taskByPaperKey = emptyMap()
        sectionsByPaperKey = emptyMap()
    }
}

class EkwingAnswerReader(private val context: Context) {
    fun readPapers(history: Boolean): List<EkwingAnswerPaper> {
        val session = EkwingLoginStore.loadSession(context)
            ?: reloginWithSavedIdentity()
            ?: throw RuntimeException("请先登录翼课账号")

        val useBasic = EkwingLoginStore.useBasicApi(context)
        val taskList = getExamList(
            session = session,
            useBasic = useBasic,
            history = history,
            allPages = EkwingLoginStore.loadAllPages(context),
        )
        val allItems = taskList.tasks.distinctBy { it.answerPaperKey() }
        val papers = allItems.mapIndexed { index, exam -> exam.toAnswerPaper(index) }

        EkwingAnswerState.papers = papers
        EkwingAnswerState.taskByPaperKey = allItems.associate { exam ->
            exam.answerPaperKey() to exam.toString()
        }
        EkwingAnswerState.sectionsByPaperKey = emptyMap()
        return papers
    }

    fun readAnswerSections(paperKey: String): List<EkwingAnswerSection> {
        val cachedSections = EkwingAnswerState.sectionsByPaperKey[paperKey]
        if (cachedSections != null) return cachedSections

        val examText = EkwingAnswerState.taskByPaperKey[paperKey]
            ?: throw RuntimeException("未找到考试，请先读取考试列表")
        val exam = runCatching { JSONObject(examText) }
            .getOrElse { throw RuntimeException("考试缓存无效，请重新读取列表") }
        val session = EkwingLoginStore.loadSession(context)
            ?: reloginWithSavedIdentity()
            ?: throw RuntimeException("请先登录翼课账号")
        val useBasic = EkwingLoginStore.useBasicApi(context)
        val sections = runCatching {
            readExamSections(session, exam, useBasic)
        }.getOrElse { error ->
            listOf(EkwingAnswerAssembler.readFailureSection(error))
        }
        EkwingAnswerState.sectionsByPaperKey = EkwingAnswerState.sectionsByPaperKey + (paperKey to sections)
        return sections
    }

    private fun getExamList(
        session: EkwingLoginSession,
        useBasic: Boolean,
        history: Boolean,
        allPages: Boolean,
    ): EkwingTaskList {
        var actualSession = session
        val data = runCatching {
            getExamListData(
                session = actualSession,
                useBasic = useBasic,
                history = history,
                allPages = allPages,
            )
        }.recoverCatching { error ->
            val refreshedSession = reloginWithSavedIdentity() ?: throw error
            actualSession = EkwingSessionRefreshPlanner.actualSession(
                current = actualSession,
                refreshed = refreshedSession,
            )
            getExamListData(
                session = refreshedSession,
                useBasic = useBasic,
                history = history,
                allPages = allPages,
            )
        }.getOrThrow()
        return EkwingTaskList(
            session = actualSession,
            tasks = if (history) {
                EkwingTaskListParser.examHistoryTasks(data)
            } else {
                EkwingTaskListParser.studyCenterExamTasks(data)
            },
        )
    }

    private fun getExamListData(
        session: EkwingLoginSession,
        useBasic: Boolean,
        history: Boolean,
        allPages: Boolean,
    ): Any? {
        if (!history) {
            return requestFirstSuccessful(EkwingHomeworkApiPlanner.studyCenterPaths(useBasic)) { path ->
                postForm(path, commonParams(session)).opt("data")
            }
        }

        val paths = EkwingHomeworkApiPlanner.examHistoryPaths(useBasic)
        val firstPage = requestFirstSuccessful(paths) { path ->
            val request = EkwingHomeworkRequestPlanner.examHistoryRequest(
                page = 1,
                commonParams = commonParams(session),
            )
            postForm(path, request.payload).opt("data")
        }
        if (!allPages) return firstPage

        val items = EkwingTaskListParser.examHistoryTasks(firstPage).toMutableList()
        val pageInfo = EkwingHomeworkDetailParser.pageInfo(firstPage)
        var current = pageInfo?.optInt("currentPage", 1) ?: 1
        val total = pageInfo?.optInt("totalPage", current) ?: current
        while (current < total && items.isNotEmpty()) {
            val archiveId = lastArchiveId(items)
            current += 1
            val pageData = requestFirstSuccessful(paths) { path ->
                val request = EkwingHomeworkRequestPlanner.examHistoryRequest(
                    page = current,
                    archiveId = archiveId,
                    commonParams = commonParams(session),
                )
                postForm(path, request.payload).opt("data")
            }
            val nextItems = EkwingTaskListParser.examHistoryTasks(pageData)
            if (nextItems.isEmpty()) break
            items += nextItems
        }
        return JSONArray().apply {
            items.forEach(::put)
        }
    }

    private fun reloginWithSavedIdentity(): EkwingLoginSession? {
        if (!EkwingLoginStore.shouldSaveLogin(context)) return null
        val identity = EkwingLoginStore.loadIdentity(context)
        val plan = EkwingReloginPlanner.plan(identity)
        return when (plan) {
            is EkwingReloginPlan.None -> null
            is EkwingReloginPlan.Account -> {
                saveReloginResult(
                    result = EkwingAuthClient(context).loginByAccount(plan.username, plan.password),
                    identity = identity,
                    loginMethod = "account",
                )
            }
            is EkwingReloginPlan.RealName -> {
                saveReloginResult(
                    result = EkwingAuthClient(context).loginByRealName(
                        name = plan.name,
                        password = plan.password,
                        schoolName = plan.schoolName,
                        schoolId = plan.schoolId,
                    ),
                    identity = identity,
                    loginMethod = "real-name",
                )
            }
        }
    }

    private fun saveReloginResult(
        result: EkwingLoginResult,
        identity: EkwingLoginIdentity,
        loginMethod: String,
    ): EkwingLoginSession {
        EkwingLoginStore.saveSession(
            context = context,
            result = result,
            loginMethod = loginMethod,
            identity = identity,
        )
        return EkwingLoginSession(
            uid = result.uid,
            token = result.token,
            userType = result.userType,
        )
    }

    private fun readHomeworkSections(
        session: EkwingLoginSession,
        homework: JSONObject,
        useBasic: Boolean,
    ): List<EkwingAnswerSection> {
        val detailItems = getDetailItems(session, homework, useBasic)
        if (detailItems.isEmpty()) {
            return listOf(
                EkwingAnswerSection(
                    title = "答案",
                    category = "empty",
                    originalText = "未读取到作业小项",
                    questions = emptyList(),
                )
            )
        }

        return detailItems.mapIndexed { index, item ->
            val contentQuestions = fetchContentQuestions(session, homework, item)
            val itemAnswers = parseAnswerQuestions(item)
            val fetchedAnswers = fetchAnswers(session, homework, item)
            val answers = mergeAnswerSources(itemAnswers, fetchedAnswers)
            val questions = mergeQuestions(contentQuestions, answers)
            val title = firstNonBlank(
                item.optString("type_name"),
                item.optString("title"),
                "小项 ${index + 1}",
            ) ?: "小项 ${index + 1}"
            EkwingAnswerSection(
                title = title,
                category = firstNonBlank(item.optString("tk_biz"), item.optString("type")) ?: "answer",
                originalText = firstNonBlank(
                    item.optString("name"),
                    item.optString("title"),
                    contentQuestions.firstOrNull()?.question,
                    item.optString("type_name"),
                    "已读取翼课作业小项",
                ) ?: "已读取翼课作业小项",
                questions = questions.ifEmpty {
                    listOf(
                        EkwingAnswerQuestion(
                            order = "Q${index + 1}",
                            question = title,
                            answer = "未解析到标准答案",
                        )
                    )
                },
            )
        }
    }

    private fun readExamSections(
        session: EkwingLoginSession,
        exam: JSONObject,
        useBasic: Boolean,
    ): List<EkwingAnswerSection> {
        val selfId = examSelfId(exam) ?: throw RuntimeException("选中的考试缺少 self_id")
        val examItem = runCatching {
            val request = EkwingHomeworkRequestPlanner.examItemRequest(
                selfId = selfId,
                commonParams = commonParams(session),
            )
            postForm(request.path, request.payload).opt("data")
        }.getOrNull()
        val scoreInfo = runCatching {
            val request = EkwingHomeworkRequestPlanner.examScoreRequest(
                exam = exam,
                selfId = selfId,
                useBasic = useBasic,
                commonParams = commonParams(session),
            )
            postScoreForm(request.path, request.payload)
        }.getOrElse { error ->
            throw RuntimeException("考试成绩读取失败：${error.message}")
        }
        val modelScoreInfos = fetchModelScoreInfos(session, selfId, scoreInfo)
        val questions = examAnswerQuestions(scoreInfo, modelScoreInfos).ifEmpty {
            EkwingAnswerParser.parseHomeworkAnswerQuestions(
                JSONObject().apply {
                    put("exam_item", examItem ?: JSONObject.NULL)
                    put("score_info", scoreInfo)
                    put("model_score_infos", modelScoreInfos)
                }
            )
        }
        return listOf(
            EkwingAnswerSection(
                title = firstNonBlank(exam.optString("title"), exam.optString("self_title"), "考试答案")
                    ?: "考试答案",
                category = "exam",
                originalText = exam.toAnswerPaper(0).summary,
                questions = questions.ifEmpty {
                    listOf(
                        EkwingAnswerQuestion(
                            order = "Q1",
                            question = "考试答案",
                            answer = "未解析到标准答案",
                        )
                    )
                },
            )
        )
    }

    private fun getDetailItems(session: EkwingLoginSession, homework: JSONObject, useBasic: Boolean): List<JSONObject> {
        val paths = EkwingHomeworkApiPlanner.detailPaths(useBasic)
        var lastError: Throwable? = null
        val mergedItems = linkedMapOf<String, JSONObject>()
        paths.forEach { path ->
            val items = runCatching {
                getDetailItemsFromPath(session, homework, path)
            }.onFailure { error ->
                lastError = error
            }.getOrNull()
            items.orEmpty().forEach { item ->
                val key = EkwingHomeworkDetailParser.detailItemKey(item)
                mergedItems[key] = mergeJsonObjects(mergedItems[key], item)
            }
        }
        if (mergedItems.isNotEmpty()) return mergedItems.values.toList()
        lastError?.let { throw it }
        return emptyList()
    }

    private fun getDetailItemsFromPath(
        session: EkwingLoginSession,
        homework: JSONObject,
        path: String,
    ): List<JSONObject> {
        val request = EkwingHomeworkRequestPlanner.detailItemsRequest(
            homework = homework,
            path = path,
            page = 1,
            commonParams = commonParams(session),
        )
        val payload = request.payload
        val data = postForm(path, payload).opt("data").takeUnless { it == JSONObject.NULL }
            ?: throw RuntimeException("作业详情返回缺少 data")
        val items = EkwingHomeworkDetailParser.detailItems(data).toMutableList()
        val pageInfo = EkwingHomeworkDetailParser.pageInfo(data)
        var current = pageInfo?.optInt("currentPage", 1) ?: 1
        val total = pageInfo?.optInt("totalPage", current) ?: current
        while (current < total) {
            current += 1
            val nextRequest = EkwingHomeworkRequestPlanner.detailItemsRequest(
                homework = homework,
                path = path,
                page = current,
                commonParams = commonParams(session),
            )
            val nextData = postForm(path, nextRequest.payload).opt("data").takeUnless { it == JSONObject.NULL }
                ?: throw RuntimeException("作业详情返回缺少 data")
            val nextItems = EkwingHomeworkDetailParser.detailItems(nextData)
            if (nextItems.isEmpty()) break
            items += nextItems
        }
        return items
    }

    private fun fetchContentQuestions(
        session: EkwingLoginSession,
        homework: JSONObject,
        item: JSONObject,
    ): List<EkwingAnswerQuestion> {
        return runCatching {
            val request = EkwingHomeworkRequestPlanner.contentRequest(
                homework = homework,
                item = item,
                commonParams = commonParams(session),
            )
            postForm(request.path, request.payload).opt("data")
        }.recoverCatching {
            fetchItemUrlData(session, item)
        }.map { data ->
            parseContentQuestions(data)
        }.getOrDefault(emptyList())
    }

    private fun fetchItemUrlData(session: EkwingLoginSession, item: JSONObject): Any? {
        val rawUrl = item.optString("url").takeIf { it.isNotBlank() }
            ?: throw RuntimeException("作业小项没有 url")
        val url = rawUrl.absoluteEkwingUrl().withQueryDefaults(commonParams(session))
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Mobile Safari/537.36 EkwingStudent/5.2.7",
            )
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        }
        return try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val text = stream.use(::readText)
            if (connection.responseCode !in 200..299) {
                throw RuntimeException("作业页面请求失败 ${connection.responseCode}：${text.take(160)}")
            }
            runCatching { JSONObject(text) }
                .recoverCatching { JSONArray(text) }
                .getOrDefault(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchAnswers(
        session: EkwingLoginSession,
        homework: JSONObject,
        item: JSONObject,
    ): List<EkwingAnswerQuestion> {
        val records = linkedMapOf<String, EkwingAnswerQuestion>()
        EkwingHomeworkRequestPlanner.answerRequests(
            homework = homework,
            item = item,
            commonParams = commonParams(session),
        ).forEach { request ->
            runCatching {
                postForm(request.path, request.payload).opt("data")
            }.onSuccess { data ->
                parseAnswerQuestions(data).forEach { question ->
                    val key = "${question.question}|${question.answer}"
                    records.putIfAbsent(key, question)
                }
            }
        }
        return records.values.mapIndexed { index, question ->
            question.copy(order = "Q${index + 1}")
        }
    }

    private fun fetchModelScoreInfos(
        session: EkwingLoginSession,
        selfId: String,
        scoreInfo: Any?,
    ): JSONArray {
        val records = JSONArray()
        extractModelScoreRequests(scoreInfo).forEach { request ->
            runCatching {
                val payload = commonParams(session).toMutableMap()
                queryParams(request.optString("url")).forEach { (key, value) ->
                    payload[key] = value
                }
                payload["self_id"] = firstNonBlank(request.optString("self_id"), selfId).orEmpty()
                request.optString("model_id").takeIf { it.isNotBlank() }?.let { payload["model_id"] = it }
                val path = request.optString("path").takeIf { it.isNotBlank() } ?: GET_MODEL_SCORE_PATH
                JSONObject().apply {
                    put("ok", true)
                    put("request", request)
                    put("path", path)
                    put("payload", JSONObject(payload))
                    put("data", getScorePage(path, payload) ?: JSONObject.NULL)
                }
            }.onSuccess { records.put(it) }
        }
        return records
    }

    private fun getScorePage(path: String, data: Map<String, String>): Any? {
        val url = "$EK_BASE_URL$path".withQueryDefaults(data)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        return try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val text = stream.use(::readText)
            if (connection.responseCode !in 200..299) {
                throw RuntimeException("接口请求失败 ${connection.responseCode}：${text.take(160)}")
            }
            parseScoreResponse(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun postScoreForm(path: String, data: Map<String, String>): Any? {
        val connection = (URL("$EK_BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return try {
            val form = data.entries.joinToString("&") { (key, value) ->
                "${key.urlEncode()}=${value.urlEncode()}"
            }
            connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val text = stream.use(::readText)
            if (connection.responseCode !in 200..299) {
                throw RuntimeException("接口请求失败 ${connection.responseCode}：${text.take(160)}")
            }
            parseScoreResponse(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseScoreResponse(text: String): Any? {
        val body = runCatching { JSONObject(text) }.getOrNull()
        if (body != null) {
            if (body.optInt("status", -1) != 0) {
                throw RuntimeException(errorMessage(body))
            }
            return normalizeJson(body.opt("data").takeUnless { it == JSONObject.NULL })
        }
        return extractScoreJsonFromText(text) ?: normalizeJson(text)
    }

    private fun parseAnswerQuestions(value: Any?): List<EkwingAnswerQuestion> {
        return EkwingAnswerParser.parseHomeworkAnswerQuestions(value)
    }

    private fun parseContentQuestions(value: Any?): List<EkwingAnswerQuestion> {
        return EkwingAnswerParser.parseContentQuestions(value)
    }

    private fun mergeQuestions(
        contentQuestions: List<EkwingAnswerQuestion>,
        answerQuestions: List<EkwingAnswerQuestion>,
    ): List<EkwingAnswerQuestion> {
        if (contentQuestions.isEmpty()) return answerQuestions
        if (answerQuestions.isEmpty()) return contentQuestions
        val count = maxOf(contentQuestions.size, answerQuestions.size)
        return (0 until count).map { index ->
            val content = contentQuestions.getOrNull(index)
            val answer = answerQuestions.getOrNull(index)
            EkwingAnswerQuestion(
                order = "Q${index + 1}",
                question = firstNonBlank(content?.question, answer?.question) ?: "题目 ${index + 1}",
                answer = firstNonBlank(answer?.answer, content?.answer) ?: "未解析到标准答案",
            )
        }
    }

    private fun mergeAnswerSources(
        first: List<EkwingAnswerQuestion>,
        second: List<EkwingAnswerQuestion>,
    ): List<EkwingAnswerQuestion> {
        val records = linkedMapOf<String, EkwingAnswerQuestion>()
        (first + second).forEach { question ->
            val key = "${question.question}|${question.answer}"
            records.putIfAbsent(key, question)
        }
        return records.values.mapIndexed { index, question -> question.copy(order = "Q${index + 1}") }
    }

    private fun commonParams(session: EkwingLoginSession): Map<String, String> {
        return mapOf(
            "v" to "5.1.0",
            "is_http" to "1",
            "os" to "Android",
            "client" to "student",
            "up_version" to "1.0",
            "osv" to (Build.VERSION.RELEASE ?: "Android"),
            "driverCode" to "5.2.7",
            "driverType" to (Build.MODEL ?: "Android"),
            "deviceToken" to deviceToken(),
            "uid" to session.uid,
            "author_id" to session.uid,
            "token" to session.token,
        )
    }

    private fun deviceToken(): String {
        return Settings.Secure.getString(context.applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "android-device"
    }

    private fun postForm(path: String, data: Map<String, String>): JSONObject {
        val connection = (URL("$EK_BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return try {
            val form = data.entries.joinToString("&") { (key, value) ->
                "${key.urlEncode()}=${value.urlEncode()}"
            }
            connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val text = stream.use(::readText)
            if (connection.responseCode !in 200..299) {
                throw RuntimeException("接口请求失败 ${connection.responseCode}：${text.take(160)}")
            }
            val body = runCatching { JSONObject(text) }
                .getOrElse { throw RuntimeException("接口返回不是 JSON：${text.take(160)}") }
            if (body.optInt("status", -1) != 0) {
                throw RuntimeException(errorMessage(body))
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(body: JSONObject): String {
        val data = body.optJSONObject("data")
        if (data != null) {
            for (key in listOf("msg", "error_msg", "errlog")) {
                val message = data.optString(key)
                if (message.isNotBlank()) return message
            }
            return data.toString()
        }
        return body.toString()
    }
}

internal data class EkwingAnswerReadResult(
    val papers: List<EkwingAnswerPaper>,
    val sectionsByPaperKey: Map<String, List<EkwingAnswerSection>>,
)

internal data class EkwingTaskList(
    val session: EkwingLoginSession,
    val tasks: List<JSONObject>,
)

internal object EkwingSessionRefreshPlanner {
    fun actualSession(
        current: EkwingLoginSession,
        refreshed: EkwingLoginSession?,
    ): EkwingLoginSession {
        return refreshed ?: current
    }
}

internal sealed class EkwingReloginPlan {
    data object None : EkwingReloginPlan()

    data class Account(
        val username: String,
        val password: String,
    ) : EkwingReloginPlan()

    data class RealName(
        val name: String,
        val password: String,
        val schoolName: String,
        val schoolId: String,
    ) : EkwingReloginPlan()
}

internal object EkwingReloginPlanner {
    fun plan(identity: EkwingLoginIdentity): EkwingReloginPlan {
        val password = identity.password?.takeIf { it.isNotBlank() } ?: return EkwingReloginPlan.None
        return when (identity.loginMethod) {
            "account" -> {
                val username = identity.username?.takeIf { it.isNotBlank() }
                    ?: return EkwingReloginPlan.None
                EkwingReloginPlan.Account(
                    username = username.trim(),
                    password = password,
                )
            }
            "real-name" -> {
                val name = identity.name?.takeIf { it.isNotBlank() }
                    ?: return EkwingReloginPlan.None
                val schoolName = identity.schoolName?.takeIf { it.isNotBlank() }
                    ?: return EkwingReloginPlan.None
                val schoolId = identity.schoolId?.takeIf { it.isNotBlank() }
                    ?: return EkwingReloginPlan.None
                EkwingReloginPlan.RealName(
                    name = name.trim(),
                    password = password,
                    schoolName = schoolName.trim(),
                    schoolId = schoolId.trim(),
                )
            }
            else -> EkwingReloginPlan.None
        }
    }
}

internal object EkwingAnswerAssembler {
    fun buildAnswerState(
        homeworks: List<JSONObject>,
        readSections: (JSONObject) -> List<EkwingAnswerSection>,
    ): EkwingAnswerReadResult {
        val sectionsByKey = linkedMapOf<String, List<EkwingAnswerSection>>()
        val papers = homeworks.mapIndexed { index, homework ->
            val paper = homework.toAnswerPaper(index)
            sectionsByKey[paper.key] = runCatching {
                readSections(homework)
            }.getOrElse { error ->
                listOf(readFailureSection(error))
            }
            paper
        }
        return EkwingAnswerReadResult(
            papers = papers,
            sectionsByPaperKey = sectionsByKey,
        )
    }

    fun readFailureSection(error: Throwable): EkwingAnswerSection {
        val message = error.message?.takeIf { it.isNotBlank() } ?: "未知错误"
        return EkwingAnswerSection(
            title = "读取失败",
            category = "error",
            originalText = message,
            questions = listOf(
                EkwingAnswerQuestion(
                    order = "Q1",
                    question = "读取失败",
                    answer = message,
                )
            ),
        )
    }
}

internal object EkwingTaskListParser {
    fun homeworkListTasks(value: Any?): List<JSONObject> {
        return extractList(value)
    }

    fun studyCenterTasks(value: Any?): List<JSONObject> {
        return extractList(value)
            .filter { it.optString("type").trim().equals("hw", ignoreCase = true) }
    }

    fun studyCenterExamTasks(value: Any?): List<JSONObject> {
        return extractList(value)
            .filter { it.optString("type").trim().equals("exam", ignoreCase = true) }
    }

    fun examHistoryTasks(value: Any?): List<JSONObject> {
        return extractList(value)
    }

    private fun extractList(value: Any?): List<JSONObject> {
        return when (val normalized = normalizeJson(value)) {
            is JSONArray -> normalized.jsonObjects()
            is JSONObject -> {
                val found = mutableListOf<JSONObject>()
                fun visit(item: Any?) {
                    when (item) {
                        is JSONArray -> found += item.jsonObjects()
                        is JSONObject -> item.keys().forEach { key -> visit(item.opt(key)) }
                    }
                }
                visit(normalized)
                found
            }
            else -> emptyList()
        }
    }
}

internal object EkwingHomeworkDetailParser {
    fun detailItems(value: Any?): List<JSONObject> {
        val normalized = normalizeJson(value)
        return when (normalized) {
            is JSONArray -> normalized.jsonObjects()
            is JSONObject -> {
                DETAIL_LIST_KEYS.asSequence()
                    .mapNotNull { key -> normalized.opt(key).takeUnless { it == JSONObject.NULL } }
                    .mapNotNull { candidate -> detailItems(candidate).takeIf { it.isNotEmpty() } }
                    .firstOrNull()
                    ?: collectNestedDetailItems(normalized)
            }
            else -> emptyList()
        }
    }

    fun pageInfo(value: Any?): JSONObject? {
        val normalized = normalizeJson(value)
        if (normalized !is JSONObject) return null
        normalized.optJSONObject("page")?.let { return normalizePageInfo(it) }
        val page = JSONObject()
        var hasPageValue = false
        DETAIL_PAGE_KEYS.forEach { key ->
            normalized.opt(key).takeUnless { it == JSONObject.NULL }?.let { value ->
                page.put(key, value)
                hasPageValue = true
            }
        }
        return page.takeIf { hasPageValue }?.let(::normalizePageInfo)
    }

    private fun normalizePageInfo(page: JSONObject): JSONObject {
        val normalized = JSONObject(page.toString())
        firstNonBlank(*DETAIL_CURRENT_PAGE_KEYS.map { key -> page.optString(key) }.toTypedArray())
            ?.let { normalized.put("currentPage", it) }
        firstNonBlank(*DETAIL_TOTAL_PAGE_KEYS.map { key -> page.optString(key) }.toTypedArray())
            ?.let { normalized.put("totalPage", it) }
        return normalized
    }

    private fun collectNestedDetailItems(value: JSONObject): List<JSONObject> {
        val found = mutableListOf<JSONObject>()
        fun visit(item: Any?) {
            when (item) {
                is JSONObject -> {
                    if (looksLikeDetailItem(item)) {
                        found += item
                    } else {
                        item.keys().forEach { key -> visit(item.opt(key)) }
                    }
                }
                is JSONArray -> {
                    for (index in 0 until item.length()) visit(item.opt(index))
                }
            }
        }
        visit(value)
        return found.distinctBy { detailItemKey(it) }
    }

    private fun looksLikeDetailItem(item: JSONObject): Boolean {
        return (item.has("id") || item.has("hwcid") || item.has("hw_cnt_id") || item.has("hwcnt_id")) &&
            DETAIL_ITEM_HINT_KEYS.any { item.has(it) }
    }

    fun detailItemKey(item: JSONObject): String {
        return firstNonBlank(
            homeworkItemContentId(item),
            item.optString("id"),
            item.optString("hwcid"),
            item.optString("hw_cnt_id"),
            item.optString("hwcnt_id"),
            item.optString("record_id"),
            item.optString("hid"),
            item.optString("hw_id"),
            item.toString(),
        ) ?: item.toString()
    }
}

private fun mergeJsonObjects(first: JSONObject?, second: JSONObject): JSONObject {
    if (first == null) return JSONObject(second.toString())
    val merged = JSONObject(first.toString())
    second.keys().forEach { key ->
        val value = second.opt(key)
        if (value != JSONObject.NULL) {
            merged.put(key, value)
        }
    }
    return merged
}

internal object EkwingAnswerParser {
    fun parseHomeworkAnswerQuestions(value: Any?): List<EkwingAnswerQuestion> {
        val normalized = normalizeJson(value)
        val answerRoot = (normalized as? JSONObject)?.optJSONObject("ans")
        val answers = answerRoot?.optJSONArray("answers")
        if (answers != null && answers.length() > 0) {
            return parseHomeworkAnswerItems(answers, answerRoot.optString("txt"))
        }

        val itemContainer = homeworkAnswerItems(normalized)
        if (itemContainer != null && itemContainer.length() > 0) {
            return parseHomeworkAnswerItems(itemContainer)
        }

        return findAnswerLikeObjects(normalized).mapIndexed { index, item ->
            EkwingAnswerQuestion(
                order = "Q${index + 1}",
                question = firstQuestionText(item) ?: "题目 ${index + 1}",
                answer = flattenAnswer(item).joinToString("\n").ifBlank { item.toString() },
            )
        }
    }

    fun parseContentQuestions(value: Any?): List<EkwingAnswerQuestion> {
        val normalized = normalizeJson(value)
        return findQuestionLikeObjects(normalized).mapIndexed { index, item ->
            EkwingAnswerQuestion(
                order = "Q${index + 1}",
                question = firstQuestionText(item) ?: "题目 ${index + 1}",
                answer = flattenAnswer(item, strict = true).joinToString("\n"),
            )
        }
    }

    fun parseExamAnswerQuestions(value: Any?): List<EkwingAnswerQuestion> {
        val questions = mutableListOf<EkwingAnswerQuestion>()
        collectExamQuestions(normalizeJson(value), questions)
        return questions.distinctBy { "${it.question}|${it.answer}" }
            .mapIndexed { index, question -> question.copy(order = "Q${index + 1}") }
    }
}

internal fun examAnswerQuestions(scoreInfo: Any?, modelScoreInfos: Any?): List<EkwingAnswerQuestion> {
    val modelQuestions = parseJsonExamAnswers(JSONObject().apply {
        put("model_score_infos", normalizeJson(modelScoreInfos) ?: JSONArray())
    })
    if (modelQuestions.any { it.answer.isNotBlank() }) {
        return modelQuestions
    }
    return EkwingAnswerParser.parseExamAnswerQuestions(scoreInfo)
}

internal fun parseJsonExamAnswers(root: Any?): List<EkwingAnswerQuestion> {
    val questions = mutableListOf<EkwingAnswerQuestion>()
    extractModelScoreItems(root).forEach { item ->
        val data = normalizeJson(item.opt("data")) as? JSONObject ?: return@forEach
        val modelInfo = data.optJSONObject("model_info") ?: return@forEach
        val modelBaseInfo = data.optJSONObject("model_base_info")
        questions += parseJsonModelInfo(modelInfo, modelBaseInfo)
    }
    return questions.mapIndexed { index, question -> question.copy(order = "Q${index + 1}") }
}

private fun extractModelScoreItems(root: Any?): List<JSONObject> {
    return when (val normalized = normalizeJson(root)) {
        is JSONArray -> normalized.jsonObjects()
        is JSONObject -> {
            val wrappedItems = normalized.optJSONArray("model_score_infos")?.jsonObjects().orEmpty()
            when {
                wrappedItems.isNotEmpty() -> wrappedItems
                normalized.optJSONObject("data")?.optJSONObject("model_info") != null -> listOf(normalized)
                normalized.optJSONObject("model_info") != null -> listOf(JSONObject().apply { put("data", normalized) })
                else -> emptyList()
            }
        }
        else -> emptyList()
    }
}

private fun parseJsonModelInfo(
    modelInfo: JSONObject,
    modelBaseInfo: JSONObject?,
): List<EkwingAnswerQuestion> {
    val modelName = firstNonBlank(
        knownModelTypeName(firstNonBlank(modelInfo.optString("model_type"), modelInfo.optString("type"))),
        titleFromModelBaseInfo(modelBaseInfo),
        modelInfo.optString("model_type_name"),
        modelInfo.optString("name"),
    )
    val quesList = modelInfo.optJSONArray("ques_list")
    if (quesList != null && quesList.length() > 0) {
        return (0 until quesList.length()).mapNotNull { index ->
            val question = quesList.optJSONObject(index) ?: return@mapNotNull null
            EkwingAnswerQuestion(
                order = "Q${index + 1}",
                question = firstNonBlank(
                    question.optString("title_text"),
                    question.optString("question"),
                    question.optString("title"),
                    question.optString("text"),
                ) ?: "题目 ${index + 1}",
                answer = flattenJsonExamAnswer(question.opt("answer")).joinToString("\n"),
            )
        }
    }

    val directAnswers = flattenJsonExamAnswer(modelInfo.opt("answer"))
    if (directAnswers.isNotEmpty()) {
        return listOf(
            EkwingAnswerQuestion(
                order = "Q1",
                question = firstNonBlank(
                    modelInfo.optString("answer_tip"),
                    modelInfo.optString("title_text"),
                    modelInfo.optString("desc"),
                    modelName,
                ) ?: "题目 1",
                answer = directAnswers.joinToString("\n"),
            )
        )
    }

    val readingText = firstNonBlank(modelInfo.optString("real_text"), modelInfo.optString("dis_text"))
    if (!readingText.isNullOrBlank()) {
        return listOf(
            EkwingAnswerQuestion(
                order = "Q1",
                question = readingText,
                answer = readingText,
            )
        )
    }

    return emptyList()
}

private fun flattenJsonExamAnswer(value: Any?): List<String> {
    val answers = linkedSetOf<String>()
    fun collect(item: Any?) {
        when (val normalized = normalizeJson(item)) {
            null, JSONObject.NULL -> Unit
            is JSONArray -> {
                for (index in 0 until normalized.length()) collect(normalized.opt(index))
            }
            is JSONObject -> {
                listOf("answer", "answers", "right_answer", "standard_answer", "refText", "text", "content")
                    .forEach { key ->
                        if (normalized.has(key)) collect(normalized.opt(key))
                    }
            }
            else -> cleanDisplayText(normalized.toString()).takeIf { it.isNotBlank() }?.let { answers += it }
        }
    }
    collect(value)
    return answers.toList()
}

private fun titleFromModelBaseInfo(modelBaseInfo: JSONObject?): String? {
    if (modelBaseInfo == null) return null
    val titleInfo = modelBaseInfo.optJSONObject("title_info")
    if (titleInfo != null) {
        firstNonBlank(titleInfo.optString("title"), titleInfo.optString("name"))?.let { return it }
    }
    return firstNonBlank(modelBaseInfo.optString("title"), modelBaseInfo.optString("name"))
}

private fun knownModelTypeName(modelType: String?): String? {
    return when (modelType) {
        "1" -> "模仿朗读"
        "6" -> "信息转述"
        "7" -> "听选信息"
        "8" -> "回答问题"
        "9" -> "询问信息"
        else -> null
    }
}

private fun collectExamQuestions(value: Any?, questions: MutableList<EkwingAnswerQuestion>) {
    when (value) {
        is JSONArray -> {
            for (index in 0 until value.length()) collectExamQuestions(value.opt(index), questions)
        }
        is JSONObject -> {
            parseExamModelInfoQuestions(value).takeIf { it.isNotEmpty() }?.let {
                questions += it
                return
            }
            parseExamReportQuestions(value).takeIf { it.isNotEmpty() }?.let {
                questions += it
            }
            if (looksLikeAnswerObject(value)) {
                val answer = formatAnswerText(value)
                if (answer.isNotBlank()) {
                    questions += EkwingAnswerQuestion(
                        order = "Q${questions.size + 1}",
                        question = firstQuestionText(value) ?: "题目 ${questions.size + 1}",
                        answer = answer,
                    )
                }
            }
            value.keys().forEach { key -> collectExamQuestions(value.opt(key), questions) }
        }
    }
}

private fun parseExamReportQuestions(report: JSONObject): List<EkwingAnswerQuestion> {
    val ansInfo = report.optJSONObject("ans_info") ?: return emptyList()
    val content = ansInfo.optJSONArray("content") ?: return emptyList()
    val parsed = mutableListOf<EkwingAnswerQuestion>()
    for (sectionIndex in 0 until content.length()) {
        val section = content.optJSONObject(sectionIndex) ?: continue
        val quesList = section.optJSONArray("ques_list") ?: continue
        for (questionIndex in 0 until quesList.length()) {
            val question = quesList.optJSONObject(questionIndex) ?: continue
            val answer = firstNonBlank(
                flattenAnswer(question.opt("refText")).joinToString("\n"),
                flattenAnswer(firstAnswerValue(question)).joinToString("\n"),
                firstNonBlank(question.optString("real_text"), question.optString("dis_text")),
            )
            if (!answer.isNullOrBlank()) {
                parsed += EkwingAnswerQuestion(
                    order = "Q${parsed.size + 1}",
                    question = firstQuestionText(question) ?: "题目 ${parsed.size + 1}",
                    answer = answer,
                )
            }
        }
    }
    return parsed
}

private fun parseExamModelInfoQuestions(value: JSONObject): List<EkwingAnswerQuestion> {
    val modelInfo = value.optJSONObject("model_info") ?: return emptyList()
    val quesList = modelInfo.optJSONArray("ques_list")
    if (quesList != null && quesList.length() > 0) {
        return parseHomeworkAnswerItems(quesList)
    }
    val answer = firstNonBlank(
        flattenAnswer(firstAnswerValue(modelInfo)).joinToString("\n"),
        firstNonBlank(modelInfo.optString("real_text"), modelInfo.optString("dis_text")),
    )
    if (answer.isNullOrBlank()) return emptyList()
    return listOf(
        EkwingAnswerQuestion(
            order = "Q1",
            question = firstQuestionText(modelInfo) ?: firstNonBlank(modelInfo.optString("model_type_name"), modelInfo.optString("name"))
                ?: "题目 1",
            answer = answer,
        )
    )
}

private fun parseHomeworkAnswerItems(items: JSONArray, fallbackQuestion: String? = null): List<EkwingAnswerQuestion> {
    return (0 until items.length()).mapNotNull { index ->
        val value = items.opt(index)
        when (value) {
            is JSONObject -> EkwingAnswerQuestion(
                order = "Q${index + 1}",
                question = firstQuestionText(value) ?: firstNonBlank(fallbackQuestion) ?: "题目 ${index + 1}",
                answer = formatAnswerText(value),
            )
            null, JSONObject.NULL -> null
            else -> EkwingAnswerQuestion(
                order = "Q${index + 1}",
                question = firstNonBlank(fallbackQuestion) ?: "题目 ${index + 1}",
                answer = flattenAnswer(value).joinToString("\n").ifBlank { value.toString() },
            )
        }
    }
}

private fun homeworkAnswerItems(value: Any?): JSONArray? {
    val containers = setOf(
        "answers",
        "answerList",
        "answer_list",
        "questions",
        "questionList",
        "question_list",
        "list",
        "data",
        "items",
        "records",
        "rows",
        "result",
    )
    fun isAnswerItemArray(array: JSONArray): Boolean {
        return (0 until array.length()).any { index ->
            when (val item = array.opt(index)) {
                is JSONObject -> STANDARD_ANSWER_KEYS.any { item.has(it) } || QUESTION_KEYS.any { item.has(it) }
                null, JSONObject.NULL -> false
                else -> item.toString().isNotBlank()
            }
        }
    }
    fun visit(item: Any?): JSONArray? {
        return when (item) {
            is JSONArray -> item.takeIf(::isAnswerItemArray)
            is JSONObject -> {
                containers.asSequence()
                    .mapNotNull { key -> item.opt(key).takeUnless { it == JSONObject.NULL } }
                    .mapNotNull(::visit)
                    .firstOrNull()
                    ?: item.keys().asSequence()
                        .mapNotNull { key -> visit(item.opt(key)) }
                        .firstOrNull()
            }
            else -> null
        }
    }
    return visit(value)
}

private fun findAnswerLikeObjects(value: Any?): List<JSONObject> {
    val found = mutableListOf<JSONObject>()
    fun visit(item: Any?) {
        when (item) {
            is JSONObject -> {
                if (STANDARD_ANSWER_KEYS.any { item.has(it) }) {
                    found += item
                }
                item.keys().forEach { key -> visit(item.opt(key)) }
            }
            is JSONArray -> {
                for (index in 0 until item.length()) visit(item.opt(index))
            }
        }
    }
    visit(value)
    return found
}

private fun looksLikeAnswerObject(item: JSONObject): Boolean {
    return STANDARD_ANSWER_KEYS.any { item.has(it) }
}

private fun findQuestionLikeObjects(value: Any?): List<JSONObject> {
    val found = mutableListOf<JSONObject>()
    fun visit(item: Any?) {
        when (item) {
            is JSONObject -> {
                if (QUESTION_KEYS.any { item.has(it) }) {
                    found += item
                    return
                }
                item.keys().forEach { key -> visit(item.opt(key)) }
            }
            is JSONArray -> {
                for (index in 0 until item.length()) visit(item.opt(index))
            }
        }
    }
    visit(value)
    return found
}

private fun flattenAnswer(value: Any?, strict: Boolean = false): List<String> {
    val answers = linkedSetOf<String>()
    fun addAnswer(value: String) {
        cleanDisplayText(value).takeIf { it.isNotBlank() }?.let { answers += it }
    }

    fun collect(item: Any?, fromAnswerField: Boolean = false) {
        when (item) {
            null, JSONObject.NULL -> Unit
            is JSONArray -> {
                for (index in 0 until item.length()) collect(item.opt(index), fromAnswerField)
            }
            is JSONObject -> {
                var collected = false
                STANDARD_ANSWER_KEYS.forEach { key ->
                    if (item.has(key)) {
                        collected = true
                        collect(item.opt(key), fromAnswerField = true)
                    }
                }
                if (!collected && (!strict || fromAnswerField)) {
                    firstNonBlank(*ANSWER_TEXT_KEYS.map { key -> item.optString(key) }.toTypedArray())
                        ?.let(::addAnswer)
                }
            }
            else -> item.toString().takeIf { it.isNotBlank() }?.let(::addAnswer)
        }
    }
    collect(normalizeJson(value))
    return answers.toList()
}

private fun formatAnswerText(item: JSONObject): String {
    val primaryAnswer = flattenAnswer(firstAnswerValue(item)).joinToString("\n")
        .ifBlank {
            if (QUESTION_KEYS.any { item.has(it) }) "" else flattenAnswer(item).joinToString("\n")
        }
    return primaryAnswer
}

private fun normalizeJson(value: Any?): Any? {
    if (value is String) {
        val repaired = repairMojibakeText(value)
        val decoded = decodeHtmlEntities(repaired)
        val text = decoded.trim()
        parseJsonCandidate(text)?.let { return normalizeJson(it) }
        decodeUnicodeEscapes(text).takeIf { it != text }?.let { unicodeDecoded ->
            parseJsonCandidate(unicodeDecoded.trim())?.let { return normalizeJson(it) }
        }
        text.urlDecode().takeIf { it != text }?.let { urlDecoded ->
            parseJsonCandidate(urlDecoded.trim())?.let { return normalizeJson(it) }
            extractEmbeddedJson(urlDecoded)?.let { return normalizeJson(it) }
        }
        extractEmbeddedJson(text)?.let { return normalizeJson(it) }
        return repaired
    }
    if (value is JSONObject) {
        val normalized = JSONObject()
        value.keys().forEach { key -> normalized.put(key, normalizeJson(value.opt(key))) }
        return normalized
    }
    if (value is JSONArray) {
        val normalized = JSONArray()
        for (index in 0 until value.length()) normalized.put(normalizeJson(value.opt(index)))
        return normalized
    }
    return value
}

private fun extractEmbeddedJson(text: String): Any? {
    return extractBalancedJson(text, '{') ?: extractBalancedJson(text, '[')
}

internal fun extractScoreJsonFromText(text: String): Any? {
    val decoded = decodeHtmlEntities(repairMojibakeText(text))
    extractJsonParseCandidates(decoded)
        .firstOrNull(::hasExamAnswerJson)
        ?.let { return normalizeJson(it) }
    extractRawJsonCandidates(decoded, maxCandidates = 80)
        .firstOrNull(::hasExamAnswerJson)
        ?.let { return normalizeJson(it) }
    return null
}

private fun extractJsonParseCandidates(text: String): List<Any> {
    val pattern = Regex("""JSON\.parse\(\s*(['"])(.*?)\1\s*\)""", RegexOption.DOT_MATCHES_ALL)
    return pattern.findAll(text).mapNotNull { match ->
        val raw = match.groupValues[2]
        listOf(raw, decodeUnicodeEscapes(raw))
            .asSequence()
            .mapNotNull { parseJsonCandidate(it) }
            .firstOrNull()
    }.toList()
}

private fun extractRawJsonCandidates(text: String, maxCandidates: Int): List<Any> {
    val candidates = mutableListOf<Any>()
    text.indices.asSequence()
        .filter { text[it] == '{' || text[it] == '[' }
        .forEach { start ->
            val opening = text[start]
            val closing = if (opening == '{') '}' else ']'
            extractBalancedJsonAt(text, start, opening, closing)?.let { candidate ->
                candidates += candidate
                if (candidates.size >= maxCandidates) return candidates
            }
        }
    return candidates
}

private fun extractBalancedJsonAt(text: String, start: Int, opening: Char, closing: Char): Any? {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until text.length) {
        val char = text[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            opening -> depth += 1
            closing -> {
                depth -= 1
                if (depth == 0) {
                    return parseJsonCandidate(text.substring(start, index + 1))
                }
            }
        }
    }
    return null
}

private fun hasExamAnswerJson(value: Any?): Boolean {
    return when (val normalized = normalizeJson(value)) {
        is JSONArray -> (0 until normalized.length()).any { index -> hasExamAnswerJson(normalized.opt(index)) }
        is JSONObject -> {
            if (normalized.has("model_info") || normalized.has("model_score_infos")) return true
            if (STANDARD_ANSWER_KEYS.any { normalized.has(it) }) return true
            normalized.keys().asSequence().any { key -> hasExamAnswerJson(normalized.opt(key)) }
        }
        is String -> {
            val lowered = normalized.lowercase()
            listOf("model_info", "model_score_infos", "answer", "right_answer", "standard_answer").any { it in lowered }
        }
        else -> false
    }
}

private fun extractBalancedJson(text: String, opening: Char): Any? {
    val closing = if (opening == '{') '}' else ']'
    text.indices.asSequence()
        .filter { text[it] == opening }
        .forEach { start ->
            var depth = 0
            var inString = false
            var escaped = false
            for (index in start until text.length) {
                val char = text[index]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' -> inString = false
                    }
                    continue
                }
                when (char) {
                    '"' -> inString = true
                    opening -> depth += 1
                    closing -> {
                        depth -= 1
                        if (depth == 0) {
                            parseJsonCandidate(text.substring(start, index + 1))?.let { return it }
                            break
                        }
                    }
                }
            }
        }
    return null
}

private fun parseJsonCandidate(text: String): Any? {
    val candidate = text.trim()
    return when {
        candidate.startsWith("{") -> runCatching { JSONObject(candidate) }.getOrNull()
        candidate.startsWith("[") -> runCatching { JSONArray(candidate) }.getOrNull()
        else -> null
    }
}

private fun decodeHtmlEntities(value: String): String {
    return value
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#x22;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&#160;", " ")
        .replace("&#xA0;", " ")
        .replace("&#xa0;", " ")
}

private fun decodeUnicodeEscapes(value: String): String {
    val pattern = Regex("""\\u([0-9a-fA-F]{4})""")
    return pattern.replace(value) { match ->
        match.groupValues[1].toInt(16).toChar().toString()
    }
}

private fun cleanDisplayText(value: String): String {
    return decodeUnicodeEscapes(decodeHtmlEntities(repairMojibakeText(value)))
        .replace(Regex("""(?i)<br\s*/?>"""), "\n")
        .replace(Regex("""<[^>]+>"""), " ")
        .replace(Regex("""[ \t\x0B\f\r]+"""), " ")
        .replace(Regex(""" *\n *"""), "\n")
        .trim()
}

internal data class EkwingScoreRequest(
    val path: String,
    val payload: Map<String, String>,
)

internal object EkwingHomeworkApiPlanner {
    fun homeworkListPaths(useBasic: Boolean): List<String> {
        return preferBasicPath(
            useBasic = useBasic,
            normal = HOMEWORK_LIST_PATH,
            basic = BASIC_HOMEWORK_LIST_PATH,
        )
    }

    fun studyCenterPaths(useBasic: Boolean): List<String> {
        return preferBasicPath(
            useBasic = useBasic,
            normal = STUDY_CENTER_PATH,
            basic = BASIC_STUDY_CENTER_PATH,
        )
    }

    fun examHistoryPaths(useBasic: Boolean): List<String> {
        return preferBasicPath(
            useBasic = useBasic,
            normal = EXAM_HISTORY_PATH,
            basic = BASIC_EXAM_HISTORY_PATH,
        )
    }

    fun examScorePaths(useBasic: Boolean): List<String> {
        return preferBasicPath(
            useBasic = useBasic,
            normal = EXAM_SCORE_PATH,
            basic = BASIC_EXAM_SCORE_PATH,
        )
    }

    fun detailPaths(useBasic: Boolean): List<String> {
        return preferBasicPath(
            useBasic = useBasic,
            normal = HOMEWORK_ITEMS_PATH,
            basic = BASIC_HOMEWORK_ITEMS_PATH,
        ) + preferBasicPath(
            useBasic = useBasic,
            normal = SCORE_DETAIL_PATH,
            basic = BASIC_SCORE_DETAIL_PATH,
        )
    }

    private fun preferBasicPath(useBasic: Boolean, normal: String, basic: String): List<String> {
        return if (useBasic) listOf(basic, normal) else listOf(normal, basic)
    }
}

private fun <T> requestFirstSuccessful(
    paths: List<String>,
    request: (String) -> T,
): T {
    var lastError: Throwable? = null
    paths.distinct().forEach { path ->
        runCatching { request(path) }
            .onSuccess { return it }
            .onFailure { lastError = it }
    }
    throw lastError ?: RuntimeException("没有可用接口")
}

internal object EkwingHomeworkRequestPlanner {
    fun homeworkListRequest(
        history: Boolean,
        page: Int,
        commonParams: Map<String, String>,
        archiveId: String? = null,
    ): EkwingScoreRequest {
        val payload = commonParams.toMutableMap()
        payload["method"] = if (history) "finish" else "new"
        payload["page"] = page.toString()
        payload["sortMethod"] = "desc"
        payload["sortField"] = if (history) "finish_times" else "publish_times"
        if (!archiveId.isNullOrBlank()) {
            payload["archiveId"] = archiveId
        }
        return EkwingScoreRequest(path = "", payload = payload)
    }

    fun examHistoryRequest(
        page: Int,
        commonParams: Map<String, String>,
        archiveId: String? = null,
    ): EkwingScoreRequest {
        val payload = commonParams.toMutableMap()
        payload["type"] = "his"
        payload["page"] = page.toString()
        if (!archiveId.isNullOrBlank()) {
            payload["archiveId"] = archiveId
        }
        return EkwingScoreRequest(path = "", payload = payload)
    }

    fun examItemRequest(
        selfId: String,
        commonParams: Map<String, String>,
    ): EkwingScoreRequest {
        val payload = commonParams.toMutableMap()
        payload["self_id"] = selfId
        return EkwingScoreRequest(path = EXAM_ITEM_PATH, payload = payload)
    }

    fun examScoreRequest(
        exam: JSONObject,
        selfId: String,
        useBasic: Boolean,
        commonParams: Map<String, String>,
    ): EkwingScoreRequest {
        val url = examScoreUrl(exam)
        val path = url?.pathFromUrl()
            ?: EkwingHomeworkApiPlanner.examScorePaths(useBasic).first()
        val payload = commonParams.toMutableMap()
        url?.let { rawUrl ->
            queryParams(rawUrl).forEach { (key, value) -> payload[key] = value }
        }
        payload["self_id"] = selfId
        payload["method"] = "exam_result"
        if ("type" !in payload && path.contains("basic", ignoreCase = true)) {
            payload["type"] = "0"
        }
        return EkwingScoreRequest(path = path, payload = payload)
    }

    fun detailItemsRequest(
        homework: JSONObject,
        path: String,
        page: Int,
        commonParams: Map<String, String>,
    ): EkwingScoreRequest {
        val hid = homeworkHid(homework) ?: throw RuntimeException("作业缺少 hid/id")
        val payload = commonParams.toMutableMap()
        payload["hid"] = hid
        payload["page"] = page.toString()
        payload["archiveId"] = homework.homeworkArchiveId()
        if (path == SCORE_DETAIL_PATH || path == BASIC_SCORE_DETAIL_PATH) {
            payload["self_id"] = hid
            payload["is_exercise"] = "0"
        }
        return EkwingScoreRequest(path = path, payload = payload)
    }

    fun contentRequest(
        homework: JSONObject,
        item: JSONObject,
        commonParams: Map<String, String>,
    ): EkwingScoreRequest {
        if (homeworkItemContentId(item) == null) {
            throw RuntimeException("作业小项缺少 hwcid/id")
        }
        return itemRequest(
            path = HW_DO_ITEM_PATH,
            homework = homework,
            item = item,
            commonParams = commonParams,
            extra = mapOf(
                "method" to "last",
                "is_exercise" to "0",
            ),
        )
    }

    fun answerRequests(
        homework: JSONObject,
        item: JSONObject,
        commonParams: Map<String, String>,
    ): List<EkwingScoreRequest> {
        val requests = mutableListOf<EkwingScoreRequest>()
        if (homeworkItemContentId(item) != null) {
            requests += itemRequest(
                path = HW_ANSWER_PATH,
                homework = homework,
                item = item,
                commonParams = commonParams,
                extra = mapOf("method" to "LAST"),
            )
            requests += itemRequest(
                path = HW_ANSWER_PATH,
                homework = homework,
                item = item,
                commonParams = commonParams,
                extra = mapOf("method" to "MAX"),
            )
            requests += itemRequest(
                path = HW_COUNT_PATH,
                homework = homework,
                item = item,
                commonParams = commonParams,
                extra = mapOf("is_exercise" to "0"),
            )
            requests += itemRequest(
                path = HW_HISTORY_SCORE_PATH,
                homework = homework,
                item = item,
                commonParams = commonParams,
                extra = mapOf("is_exercise" to "0", "method" to "last"),
            )
            requests += itemRequest(
                path = HW_HISTORY_SCORE_PATH,
                homework = homework,
                item = item,
                commonParams = commonParams,
                extra = mapOf("is_exercise" to "0"),
            )
            requests += itemRequest(
                path = HW_RESULT_PATH,
                homework = homework,
                item = item,
                commonParams = commonParams,
                extra = mapOf("is_exercise" to "0"),
            )
        }
        trainingAnswerRequest(
            path = TRAIN_ITEM_ANSWER_PATH,
            homework = homework,
            item = item,
            commonParams = commonParams,
            extra = mapOf("method" to "LAST"),
        )?.let { requests += it }
        trainingAnswerRequest(
            path = TRAIN_ITEM_ANSWER_PATH,
            homework = homework,
            item = item,
            commonParams = commonParams,
            extra = mapOf("method" to "MAX"),
        )?.let { requests += it }
        trainingAnswerRequest(
            path = TRAIN_JS_ITEM_ANSWER_PATH,
            homework = homework,
            item = item,
            commonParams = commonParams,
            extra = mapOf("method" to "LAST"),
        )?.let { requests += it }
        trainingAnswerRequest(
            path = TRAIN_JS_ITEM_ANSWER_PATH,
            homework = homework,
            item = item,
            commonParams = commonParams,
            extra = mapOf("method" to "MAX"),
        )?.let { requests += it }
        return requests
    }

    private fun trainingAnswerRequest(
        path: String,
        homework: JSONObject,
        item: JSONObject,
        commonParams: Map<String, String>,
        extra: Map<String, String>,
    ): EkwingScoreRequest? {
        val unitId = item.optString("unit_id").takeIf { it.isNotBlank() } ?: return null
        val type = item.optString("type").takeIf { it.isNotBlank() } ?: return null
        val recordId = item.optString("record_id").takeIf { it.isNotBlank() } ?: return null
        val payload = commonParams.toMutableMap()
        payload["unit_id"] = unitId
        payload["type"] = type
        payload["record_id"] = recordId
        payload["archiveId"] = homework.homeworkArchiveId()
        payload.putAll(extra)
        return EkwingScoreRequest(path = path, payload = payload)
    }

    private fun itemRequest(
        path: String,
        homework: JSONObject,
        item: JSONObject,
        commonParams: Map<String, String>,
        extra: Map<String, String>,
    ): EkwingScoreRequest {
        val payload = commonParams.toMutableMap()
        payload["hid"] = homeworkItemHid(homework, item).orEmpty()
        payload["hwcid"] = homeworkItemContentId(item).orEmpty()
        payload["archiveId"] = homework.homeworkArchiveId()
        payload.putAll(extra)
        return EkwingScoreRequest(path = path, payload = payload)
    }
}

private fun repairMojibakeText(value: String): String {
    val repaired = runCatching {
        value.toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8)
    }.getOrNull() ?: return value
    return if (countCjk(repaired) > countCjk(value)) repaired else value
}

private fun countCjk(value: String): Int {
    return value.count { it in '\u4e00'..'\u9fff' }
}


private fun firstAnswerValue(item: JSONObject): Any? {
    return PRIMARY_ANSWER_KEYS.firstNotNullOfOrNull { key ->
        if (item.has(key) && item.opt(key) != JSONObject.NULL) item.opt(key) else null
    }
}

private fun firstQuestionText(item: JSONObject): String? {
    return firstNonBlank(*QUESTION_KEYS.map { key -> item.optString(key) }.toTypedArray())
        ?.let(::cleanDisplayText)
        ?.takeIf { it.isNotBlank() }
}

private val PRIMARY_ANSWER_KEYS = listOf(
    "answer",
    "answers",
    "ans",
    "right_answer",
    "rightAnswer",
    "standard_answer",
    "standardAnswer",
    "correct_answer",
    "correctAnswer",
    "std_answer",
    "real_answer",
    "reference_answer",
    "refText",
    "real_text",
    "dis_text",
    "right",
    "right_ans",
    "rightAns",
    "rightText",
    "right_text",
    "option_answer",
    "correctText",
    "correct_text",
    "stdAnswer",
    "ref_answer",
)

private val STANDARD_ANSWER_KEYS = PRIMARY_ANSWER_KEYS

private val QUESTION_KEYS = listOf(
    "title_text",
    "title",
    "question",
    "question_text",
    "ques_title",
    "q_title",
    "stem",
    "text",
    "txt",
    "word",
    "sentence",
    "content",
    "topic",
    "body",
    "prompt",
    "material",
)

private val ANSWER_TEXT_KEYS = listOf(
    "text",
    "txt",
    "word",
    "sentence",
    "content",
    "content_text",
    "contentText",
    "display_text",
    "displayText",
    "value",
    "name",
    "label",
    "result",
    "result_text",
    "resultText",
    "option",
    "option_text",
    "optionText",
)

private val DETAIL_LIST_KEYS = listOf(
    "data",
    "list",
    "items",
    "rows",
    "records",
    "result",
    "detail",
    "details",
    "hw_items",
    "hwItems",
)

private val DETAIL_PAGE_KEYS = listOf(
    "currentPage",
    "totalPage",
    "current_page",
    "total_page",
    "page",
    "pageNum",
    "pageNo",
    "pageIndex",
    "current",
    "total",
    "pageCount",
    "pages",
    "lastPage",
)

private val DETAIL_CURRENT_PAGE_KEYS = listOf(
    "currentPage",
    "current_page",
    "page",
    "pageNum",
    "pageNo",
    "pageIndex",
    "current",
)

private val DETAIL_TOTAL_PAGE_KEYS = listOf(
    "totalPage",
    "total_page",
    "pageCount",
    "pages",
    "lastPage",
    "total",
)

private val DETAIL_ITEM_HINT_KEYS = listOf(
    "hid",
    "hw_id",
    "unit_id",
    "record_id",
    "type",
    "type_name",
    "tk_biz",
    "url",
    "num",
)

private fun JSONObject.toAnswerPaper(index: Int): EkwingAnswerPaper {
    val title = firstNonBlank(optString("title"), optString("self_title"), "考试 ${index + 1}") ?: "考试 ${index + 1}"
    val count = firstNonBlank(optString("cntTotal"), optString("finishCntNum"), optString("num"), optString("ques_num"))
    val score = firstNonBlank(optString("score"), optString("self_score"))
    val status = firstNonBlank(optString("status"), optString("self_status"))
    val summaryParts = listOfNotNull(
        "学习中心考试",
        count?.let { "$it 题" },
        score?.let { "分数 $it" },
        status?.let { "状态 $it" },
    )
    return EkwingAnswerPaper(
        key = answerPaperKey(),
        title = title,
        summary = summaryParts.joinToString(" | ").ifBlank { "学习中心考试" },
    )
}

private fun JSONObject.answerPaperKey(): String {
    val type = optString("type").takeIf { it.isNotBlank() } ?: "task"
    val id = firstNonBlank(
        examSelfId(this),
        optString("record_id"),
        optString("self_id"),
        optString("hid"),
        optString("id"),
        optString("title"),
    ) ?: toString()
    return "$type:$id"
}

private fun examSelfId(exam: JSONObject): String? {
    return firstNonBlank(
        exam.optString("self_id"),
        exam.firstQueryParam("self_id"),
        exam.optString("record_id"),
        exam.optString("id"),
    )
}

private fun examScoreUrl(exam: JSONObject): String? {
    return listOf(exam.optString("url"), exam.optString("start_url"))
        .firstOrNull { it.pathFromUrl()?.contains("scoreinfo", ignoreCase = true) == true }
}

internal fun extractModelScoreRequests(value: Any?): List<JSONObject> {
    val requests = linkedMapOf<String, JSONObject>()
    fun visit(item: Any?) {
        when (val normalized = normalizeJson(item)) {
            is JSONArray -> {
                for (index in 0 until normalized.length()) visit(normalized.opt(index))
            }
            is JSONObject -> {
                val url = normalized.optString("url")
                val path = url.pathFromUrl()
                if (path == GET_MODEL_SCORE_PATH) {
                    val modelId = firstNonBlank(normalized.optString("model_id"), url.queryValue("model_id"))
                    val selfId = firstNonBlank(normalized.optString("self_id"), url.queryValue("self_id"))
                    val key = "${selfId.orEmpty()}:${modelId.orEmpty()}:$url"
                    requests[key] = JSONObject().apply {
                        put("url", url)
                        put("path", path)
                        put("self_id", selfId.orEmpty())
                        put("model_id", modelId.orEmpty())
                    }
                }
                normalized.keys().forEach { key -> visit(normalized.opt(key)) }
            }
        }
    }
    visit(value)
    return requests.values.toList()
}

private fun homeworkHid(homework: JSONObject): String? {
    return firstNonBlank(
        homework.optString("hid"),
        homework.optString("hw_id"),
        homework.optString("id"),
        homework.firstQueryParam("hid", "hw_id", "id"),
    )
}

private fun homeworkItemHid(homework: JSONObject, item: JSONObject): String? {
    return firstNonBlank(
        item.optString("hid"),
        item.optString("hw_id"),
        item.firstQueryParam("hid", "hw_id"),
        homeworkHid(homework),
    )
}

private fun homeworkItemContentId(item: JSONObject): String? {
    return firstNonBlank(
        item.optString("hwcid"),
        item.optString("hw_cnt_id"),
        item.optString("hwcnt_id"),
        item.optString("id"),
        item.firstQueryParam("hwcid", "hw_cnt_id", "hwcnt_id", "id"),
    )
}

private fun JSONObject.homeworkArchiveId(): String {
    return firstNonBlank(
        optString("archiveId"),
        optString("archive_id"),
        firstQueryParam("archiveId", "archive_id"),
    ).orEmpty()
}

private fun lastArchiveId(items: List<JSONObject>): String? {
    return items.asReversed().firstNotNullOfOrNull { item ->
        firstNonBlank(
            item.optString("archiveId"),
            item.optString("archive_id"),
            item.firstQueryParam("archiveId", "archive_id"),
        )
    }
}

private fun JSONObject.firstQueryParam(vararg names: String): String? {
    val urls = listOf(optString("url"), optString("start_url"))
    return urls.firstNotNullOfOrNull { url ->
        queryParams(url).firstNotNullOfOrNull { (key, value) ->
            value.takeIf { key in names && it.isNotBlank() }
        }
    }
}

private fun String.pathFromUrl(): String? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    val withoutScheme = trimmed.substringAfter("://", trimmed)
    val pathStart = withoutScheme.indexOf('/')
    if (pathStart < 0) return null
    return withoutScheme.substring(pathStart).substringBefore("?").substringBefore("#")
}

private fun String.queryValue(name: String): String? {
    return queryParams(this).firstOrNull { (key, value) -> key == name && value.isNotBlank() }?.second
}

private fun JSONArray?.jsonObjects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index) }
}

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

private fun String.withQueryDefaults(defaults: Map<String, String>): String {
    val separator = if (contains("?")) "&" else "?"
    val existingKeys = substringAfter("?", "")
        .split("&")
        .mapNotNull { it.substringBefore("=", "").takeIf(String::isNotBlank) }
        .toSet()
    val extraQuery = defaults
        .filterKeys { it !in existingKeys }
        .entries
        .joinToString("&") { (key, value) -> "${key.urlEncode()}=${value.urlEncode()}" }
    if (extraQuery.isBlank()) return this
    return this + separator + extraQuery
}

private fun String.absoluteEkwingUrl(): String {
    if (startsWith("http://") || startsWith("https://")) return this
    return EK_BASE_URL + if (startsWith("/")) this else "/$this"
}

private fun queryParams(url: String): List<Pair<String, String>> {
    val query = url.substringAfter("?", "").substringBefore("#")
    if (query.isBlank()) return emptyList()
    return query.split("&")
        .mapNotNull { part ->
            val key = part.substringBefore("=", "")
            if (key.isBlank()) return@mapNotNull null
            val value = part.substringAfter("=", "")
            key.urlDecode() to value.urlDecode()
        }
}

private fun String.urlDecode(): String {
    return runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrDefault(this)
}

private fun readText(input: InputStream): String {
    return BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader -> reader.readText() }
}

private fun firstNonBlank(vararg values: String?): String? {
    return values.firstOrNull { !it.isNullOrBlank() }
}
