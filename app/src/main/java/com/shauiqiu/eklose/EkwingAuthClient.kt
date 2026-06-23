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
import java.net.URLEncoder
import java.security.MessageDigest

private const val BASE_URL = "https://mapi.ekwing.com"
private const val LOGIN_SCHOOL_PATH = "/student/User/loginschool"
private const val LOGIN_ACCOUNT_PATH = "/student/User/login"
private const val SEARCH_SCHOOL_PATH = "/student/user/searchschool"

data class EkwingSchool(
    val schoolId: String,
    val schoolName: String,
    val zone: String,
)

data class EkwingLoginResult(
    val uid: String,
    val token: String,
    val userType: String?,
)

data class EkwingOvernameAccount(
    val username: String,
    val uid: String?,
    val type: String?,
    val className: String?,
)

class EkwingOvernameRequired(
    val accounts: List<EkwingOvernameAccount>,
    val passwordMd5: String,
) : RuntimeException("实名登录匹配到多个账号，请选择具体账号")

data class EkwingLoginIdentity(
    val loginMethod: String?,
    val username: String?,
    val name: String?,
    val schoolName: String?,
    val schoolId: String?,
    val password: String?,
)

data class EkwingLoginSession(
    val uid: String,
    val token: String,
    val userType: String?,
)

class EkwingAuthClient(context: Context) {
    private val appContext = context.applicationContext

    fun searchSchools(keyword: String, page: Int = 1): List<EkwingSchool> {
        val payload = commonParams().toMutableMap()
        payload["key"] = keyword
        payload["page"] = page.toString()
        val body = postForm(SEARCH_SCHOOL_PATH, payload)
        if (body.optInt("status", -1) != 0) {
            throw RuntimeException(errorMessage(body))
        }
        return extractList(body.opt("data")).mapNotNull(::normalizeSchool)
    }

    fun loginByAccount(username: String, password: String): EkwingLoginResult {
        return loginByAccountMd5(username = username, passwordMd5 = md5Hex(password))
    }

    fun loginByAccountMd5(username: String, passwordMd5: String): EkwingLoginResult {
        val payload = commonParams().toMutableMap()
        payload["username"] = username
        payload["password"] = passwordMd5
        return normalizeLoginResult(postForm(LOGIN_ACCOUNT_PATH, payload))
    }

    fun loginByRealName(
        name: String,
        password: String,
        schoolName: String,
        schoolId: String,
    ): EkwingLoginResult {
        val passwordMd5 = md5Hex(password)
        val payload = commonParams().toMutableMap()
        payload["nicename"] = name
        payload["pwd"] = passwordMd5
        payload["schoolName"] = schoolName
        payload["schoolId"] = schoolId

        val body = postForm(LOGIN_SCHOOL_PATH, payload)
        if (body.optInt("status", -1) == 0) {
            return normalizeLoginResult(body)
        }

        val data = body.optJSONObject("data")
        val state = data?.let { errorState(it) }
        if (state == 10001) {
            val accounts = extractOvernameAccounts(data.optJSONArray("overname"))
            if (accounts.isEmpty()) {
                throw RuntimeException("实名登录返回同名分支，但账号列表为空")
            }
            throw EkwingOvernameRequired(accounts, passwordMd5)
        }

        throw RuntimeException(errorMessage(body))
    }

    private fun commonParams(): Map<String, String> {
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
        )
    }

    private fun deviceToken(): String {
        return Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "android-device"
    }

    private fun postForm(path: String, data: Map<String, String>): JSONObject {
        val connection = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        val form = data.entries.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
        connection.outputStream.use { output ->
            output.write(form.toByteArray(Charsets.UTF_8))
        }

        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        val text = stream.use(::readText)
        if (connection.responseCode !in 200..299) {
            throw RuntimeException("接口请求失败 ${connection.responseCode}：${text.take(200)}")
        }
        return try {
            JSONObject(text)
        } catch (exception: Exception) {
            throw RuntimeException("接口返回不是 JSON：${text.take(200)}")
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeLoginResult(body: JSONObject): EkwingLoginResult {
        if (body.optInt("status", -1) != 0) {
            throw RuntimeException(errorMessage(body))
        }
        val data = body.optJSONObject("data")
            ?: throw RuntimeException("登录成功返回缺少 data：$body")
        val uid = data.optString("uid").takeIf { it.isNotBlank() }
            ?: throw RuntimeException("登录成功返回缺少 uid：$body")
        val token = data.optString("token").takeIf { it.isNotBlank() }
            ?: throw RuntimeException("登录成功返回缺少 token：$body")
        return EkwingLoginResult(
            uid = uid,
            token = token,
            userType = data.optString("userType").takeIf { it.isNotBlank() },
        )
    }

    private fun extractList(value: Any?): List<JSONObject> {
        return when (value) {
            is JSONArray -> value.jsonObjects()
            is JSONObject -> {
                sequenceOf("list", "rows", "data")
                    .mapNotNull { value.optJSONArray(it) }
                    .firstOrNull()
                    ?.jsonObjects()
                    ?: emptyList()
            }
            else -> emptyList()
        }
    }

    private fun normalizeSchool(item: JSONObject): EkwingSchool? {
        val schoolId = firstNonEmpty(item.opt("school_id"), item.opt("id")) ?: return null
        val schoolName = firstNonEmpty(item.opt("school_name"), item.opt("name"))?.trim() ?: return null
        val zone = listOf(
            item.optString("province_name"),
            item.optString("city_name"),
            item.optString("county_name"),
        ).filter { it.isNotBlank() }.joinToString("-").ifBlank {
            item.optString("zone")
        }
        return EkwingSchool(
            schoolId = schoolId,
            schoolName = schoolName,
            zone = zone,
        )
    }

    private fun extractOvernameAccounts(items: JSONArray?): List<EkwingOvernameAccount> {
        if (items == null) return emptyList()
        return items.jsonObjects().mapNotNull { item ->
            val username = item.optString("username").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            EkwingOvernameAccount(
                username = username,
                uid = item.optString("uid").takeIf { it.isNotBlank() },
                type = item.optString("type").takeIf { it.isNotBlank() },
                className = item.optString("classname").takeIf { it.isNotBlank() },
            )
        }
    }

    private fun errorState(data: JSONObject): Int? {
        return sequenceOf("state", "intent", "intend")
            .mapNotNull { key -> data.opt(key)?.toString()?.toIntOrNull() }
            .firstOrNull()
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

object EkwingLoginStore {
    private const val PREFS_NAME = "ekwing_login"
    private const val KEY_SAVE_LOGIN = "save_login"
    private const val KEY_BASIC_API = "basic_api"
    private const val KEY_ALL_PAGES = "all_pages"
    private const val KEY_REAL_NAME_ALIAS_FROM = "real_name_alias_from"
    private const val KEY_REAL_NAME_ALIAS_TO = "real_name_alias_to"

    fun saveSession(
        context: Context,
        result: EkwingLoginResult,
        loginMethod: String,
        identity: EkwingLoginIdentity,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shouldSaveLogin = prefs.getBoolean(KEY_SAVE_LOGIN, false)
        prefs.edit()
            .putString("uid", result.uid)
            .putString("token", result.token)
            .putString("userType", result.userType)
            .putString("login_method", loginMethod)
            .apply {
                if (shouldSaveLogin) {
                    putString("password", identity.password)
                    when (loginMethod) {
                        "account" -> putString("username", identity.username)
                        "real-name" -> {
                            putString("name", identity.name)
                            putString("school_name", identity.schoolName)
                            putString("school_id", identity.schoolId)
                        }
                    }
                } else {
                    remove("username")
                    remove("name")
                    remove("school_name")
                    remove("school_id")
                    remove("password")
                }
            }
            .apply()
    }

    fun loadIdentity(context: Context): EkwingLoginIdentity {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return EkwingLoginIdentity(
            loginMethod = prefs.getString("login_method", null),
            username = prefs.getString("username", null),
            name = prefs.getString("name", null),
            schoolName = prefs.getString("school_name", null),
            schoolId = prefs.getString("school_id", null),
            password = prefs.getString("password", null),
        )
    }

    fun loadSession(context: Context): EkwingLoginSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uid = prefs.getString("uid", null)?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString("token", null)?.takeIf { it.isNotBlank() } ?: return null
        return EkwingLoginSession(
            uid = uid,
            token = token,
            userType = prefs.getString("userType", null),
        )
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("uid")
            .remove("token")
            .remove("userType")
            .remove("login_method")
            .remove("username")
            .remove("name")
            .remove("school_name")
            .remove("school_id")
            .remove("password")
            .apply()
    }

    fun shouldSaveLogin(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SAVE_LOGIN, false)
    }

    fun setSaveLogin(context: Context, enabled: Boolean) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SAVE_LOGIN, enabled)
        if (!enabled) {
            editor
                .remove("username")
                .remove("name")
                .remove("school_name")
                .remove("school_id")
                .remove("password")
        }
        editor.apply()
    }

    fun useBasicApi(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BASIC_API, false)
    }

    fun setUseBasicApi(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BASIC_API, enabled)
            .apply()
    }

    fun loadAllPages(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALL_PAGES, true)
    }

    fun setLoadAllPages(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALL_PAGES, enabled)
            .apply()
    }

    fun loadRealNameAlias(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (prefs.getString(KEY_REAL_NAME_ALIAS_FROM, null).orEmpty()) to
            (prefs.getString(KEY_REAL_NAME_ALIAS_TO, null).orEmpty())
    }

    fun setRealNameAlias(context: Context, fromName: String, toName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REAL_NAME_ALIAS_FROM, fromName.trim())
            .putString(KEY_REAL_NAME_ALIAS_TO, toName.trim())
            .apply()
    }

    fun getLocalVerificationCode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("local_verification_code", null).orEmpty()
    }

    fun saveLocalVerificationCode(context: Context, code: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("local_verification_code", code.trim())
            .apply()
    }

    fun resolveRealName(context: Context, typedName: String): String {
        val inputName = typedName.trim()
        val (fromName, toName) = loadRealNameAlias(context)
        return if (fromName.isNotBlank() && toName.isNotBlank() && inputName == fromName.trim()) {
            toName.trim()
        } else {
            inputName
        }
    }
}

private fun md5Hex(text: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun String.urlEncode(): String {
    return URLEncoder.encode(this, Charsets.UTF_8.name())
}

private fun readText(input: InputStream): String {
    return BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
        reader.readText()
    }
}

private fun JSONArray.jsonObjects(): List<JSONObject> {
    return (0 until length()).mapNotNull { index -> optJSONObject(index) }
}

private fun firstNonEmpty(vararg values: Any?): String? {
    return values.firstNotNullOfOrNull { value ->
        value?.takeUnless { it == JSONObject.NULL }?.toString()?.takeIf { it.isNotBlank() }
    }
}
