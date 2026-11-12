package io.github.ultrafeed.app4pda.feature.auth.data

import android.content.Context
import android.text.Html
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.network.SourceHttpException
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.network.retryAfterMillis
import io.github.ultrafeed.app4pda.data.forum.ForumApiException
import io.github.ultrafeed.app4pda.data.forum.ForumDocumentParser
import io.github.ultrafeed.app4pda.data.forum.ForumFailure
import io.github.ultrafeed.app4pda.data.forum.ForumRequests
import io.github.ultrafeed.app4pda.data.forum.ForumUrls
import io.github.ultrafeed.app4pda.data.forum.forumAccessEvent
import io.github.ultrafeed.app4pda.data.forum.forumUserAgent
import io.github.ultrafeed.app4pda.data.forum.isForumChallenge
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.preferences.ForumAccount
import io.github.ultrafeed.app4pda.data.preferences.SettingsValues
import io.github.ultrafeed.app4pda.data.preferences.forumAccounts
import io.github.ultrafeed.app4pda.feature.auth.ForumCaptcha
import io.github.ultrafeed.app4pda.feature.auth.ForumSession
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

internal class ForumAuthResponse(
    val code: Int,
    val html: String,
    val cookies: List<String>,
    val mitigated: String? = null,
    val retryAfter: String? = null,
)

/** A rejection message parsed from the forum, rather than an internal exception description. */
internal class ForumAuthRejected(message: String) : IllegalArgumentException(message)

internal fun interface ForumAuthTransport {
    suspend fun execute(request: HttpRequestBuilder): ForumAuthResponse
}

internal class ForumAuthHttp(private val client: HttpClient) : ForumAuthTransport {
    override suspend fun execute(request: HttpRequestBuilder): ForumAuthResponse {
        val response = client.request(request)
        val charset = response.contentType()?.charset() ?: Charset.forName("windows-1251")
        return ForumAuthResponse(
            response.status.value,
            String(response.bodyAsBytes(), charset),
            response.headers.getAll("Set-Cookie").orEmpty(),
            response.headers["Cf-Mitigated"],
            response.headers["Retry-After"],
        )
    }
}

class ForumAuthRepository
internal constructor(
    private val context: Context,
    private val settings: AppSettings,
    private val transport: ForumAuthTransport,
    private val ioDispatcher: CoroutineDispatcher,
    private val requests: ForumRequests,
) {
    suspend fun refreshProfiles(): SourceProblem? {
        var problem: SourceProblem? = null
        val now = System.currentTimeMillis()
        val accounts = settings.awaitReady().forumAccounts().filterNot { it.hasFreshProfile(now) }
        for (account in accounts) {
            val present =
                settings.current.forumAccounts().any {
                    it.id == account.id && it.cookies == account.cookies
                }
            if (present) {
                try {
                    refreshProfile(account)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: IOException) {
                    if (problem == null)
                        problem =
                            SourceProblem.from(
                                context,
                                "4PDA",
                                failure,
                            )
                }
            }
        }
        return problem
    }

    private suspend fun refreshProfile(
        account: ForumAccount,
    ) {
        requests.execute(userInitiated = true) {
            val response =
                transport.execute(
                    request(
                            "https://${ForumUrls.HOST}/forum/index.php?showuser=${account.id}&action=edit",
                        )
                        .apply {
                            headers.append("Cookie", account.cookies)
                        },
                )
            validateResponse(response)
            if (
                response.code !in HttpStatusCode.OK.value until HttpStatusCode.MultipleChoices.value
            )
                throw SourceHttpException(
                    response.code,
                )

            val profile =
                withContext(ioDispatcher) {
                    parseProfile(response.html, account.id)
                }
            settings.accounts.updateProfile(account, profile.first, profile.second)
        }
    }

    private fun parseProfile(html: String, accountId: Int): Pair<String?, String?> {
        val document = Jsoup.parse(html, ForumUrls.BASE)
        val ownProfile =
            document.select("a[href]").any {
                ForumDocumentParser.safeUrl(
                        it.attr("href"),
                        ForumUrls.BASE,
                    )
                    ?.let { url ->
                        url.toHttpUrlOrNull()?.queryParameter("showuser")
                    } == accountId.toString()
            }
        if (!ownProfile) throw IOException("Profile is not authenticated")
        val emailInput =
            document.select("input").firstOrNull {
                it.attr("type").equals("email", true) ||
                    it.attr("name").lowercase(Locale.ROOT) in
                        setOf("email", "email_address", "emailaddress")
            }
        val email =
            emailInput?.attr("value")?.trim()?.takeIf {
                it.isNotBlank() && it.length <= 320
            }
        val avatar =
            document
                .selectFirst(".photo img")
                ?.let {
                    ForumDocumentParser.safeUrl(
                        it.attr("src"),
                        ForumUrls.BASE,
                    )
                }
                ?.takeIf { it.startsWith("https://") }
        if (emailInput == null && avatar == null)
            throw IOException(
                "Profile fields unavailable",
            )
        return avatar to email
    }

    val session: ForumSession?
        get() {
            val session = settings.current.session
            return session.userId
                .takeIf { it > 0 }
                ?.let {
                    ForumSession(
                        it,
                        session.username,
                        session.avatarUrl,
                    )
                }
        }

    val loginName: String
        get() = settings.current.session.username

    private fun request(url: String) =
        HttpRequestBuilder().apply {
            url(ForumUrls.normalize(url))
            headers.append(
                "User-Agent",
                forumUserAgent(context),
            )
            headers.append("Accept-Encoding", "identity")
        }

    suspend fun loadCaptcha(): ForumCaptcha {
        val rules = settings.awaitReady()
        val response =
            requests.execute(true) {
                transport.execute(request(rules.rules["login_url"])).also(::validateResponse)
            }
        coroutineContext.ensureActive()
        forumAccessEvent(
            context,
            "captcha-form",
            rules.rules["login_url"],
            response.code,
        )
        validateResponse(response)
        if (response.code !in HttpStatusCode.OK.value until HttpStatusCode.MultipleChoices.value)
            throw IOException("HTTP ${response.code}")
        val scoped =
            response.cookies
                .mapNotNull { Cookie.parse(rules.rules["login_url"].toHttpUrl(), it) }
                .filter { !it.name.startsWith("cf_", true) && !it.name.startsWith("__cf", true) }
        return parseCaptcha(response.html, cookieHeader(response.cookies), rules, scoped)
    }

    internal fun parseCaptcha(
        html: String,
        cookies: String,
        rules: SettingsValues = settings.current,
        imageCookies: List<Cookie> = emptyList(),
    ): ForumCaptcha {
        if (isForumChallenge(null, html))
            throw ForumApiException(
                ForumFailure.VERIFICATION_REQUIRED,
                context.getString(R.string.forum_verification_required),
            )
        val normalized = html
        val block =
            Regex(rules.rules["login_form_captcha_div"]).find(normalized)?.groupValues?.get(1)
                ?: run {
                    val form =
                        Jsoup.parse(html).select("form").firstOrNull {
                            it.selectFirst("input[name=login]") != null &&
                                it.selectFirst("input[name=password]") != null
                        }
                    checkNotNull(form) { context.getString(R.string.forum_auth_invalid_form) }
                    return ForumCaptcha(null, null, null, cookies)
                }

        fun field(name: String) = Regex(rules.rules[name]).find(block)?.groupValues?.get(1)
        val image =
            field("login_form_captcha_url")?.let {
                rules.rules["login_url"]
                    .toHttpUrl()
                    .resolve(Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString())
                    ?.toString()
            }
        val time = field("login_form_captcha_time")
        val signature = field("login_form_captcha_sig")
        check(!image.isNullOrBlank() && !time.isNullOrBlank() && !signature.isNullOrBlank()) {
            context.getString(R.string.forum_auth_invalid_form)
        }
        return ForumCaptcha(ForumUrls.normalize(image), time, signature, cookies, imageCookies)
    }

    internal fun loginRequest(
        username: String,
        password: String,
        hidden: Boolean,
        answer: String,
        captcha: ForumCaptcha,
        rules: SettingsValues = settings.current,
    ): HttpRequestBuilder {
        val fields =
            listOf(
                "return" to ForumUrls.INDEX + "?act=idx",
                "login" to username.trim(),
                "password" to password,
                "hidden" to if (hidden) "1" else "0",
                "captcha-time" to captcha.time.orEmpty(),
                "captcha-sig" to captcha.signature.orEmpty(),
                "captcha" to answer,
            )
        val body =
            fields.joinToString("&") { (name, value) ->
                URLEncoder.encode(name, "windows-1251") +
                    "=" +
                    URLEncoder.encode(value, "windows-1251")
            }
        return request(rules.rules["login_url"]).apply {
            method = HttpMethod.Post
            if (captcha.cookies.isNotBlank()) headers.append("Cookie", captcha.cookies)
            setBody(TextContent(body, ContentType.Application.FormUrlEncoded))
        }
    }

    internal suspend fun authenticate(
        username: String,
        password: String,
        hidden: Boolean,
        answer: String,
        captcha: ForumCaptcha,
    ): String {
        val rules = settings.awaitReady()
        val response =
            requests.execute(true) {
                transport
                    .execute(loginRequest(username, password, hidden, answer, captcha, rules))
                    .also(::validateResponse)
            }
        coroutineContext.ensureActive()
        validateResponse(response)
        if (response.code !in HttpStatusCode.OK.value until HttpStatusCode.BadRequest.value)
            throw IOException("HTTP ${response.code}")
        val cookies = cookieHeader(response.cookies)
        if (userId(cookies) <= 0) {
            val message =
                Regex(rules.rules["login_form_errors"])
                    .find(response.html)
                    ?.groupValues
                    ?.get(1)
                    ?.let {
                        Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                    }
            var safeMessage =
                message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.forum_auth_rejected)
            for (secret in listOf(password, answer, captcha.cookies).filter { it.isNotEmpty() }) {
                safeMessage = safeMessage.replace(secret, "…")
            }
            throw ForumAuthRejected(safeMessage)
        }
        coroutineContext.ensureActive()
        return cookies
    }

    internal suspend fun commit(cookies: String, username: String) {
        settings.accounts.save(
            cookies,
            username,
        )
    }

    suspend fun logout() {
        settings.accounts.logout()
    }

    private fun validateResponse(response: ForumAuthResponse) {
        if (
            isForumChallenge(
                response.mitigated,
                response.html,
            )
        )
            throw ForumApiException(
                ForumFailure.VERIFICATION_REQUIRED,
                context.getString(R.string.forum_verification_required),
                response.code,
                retryAfterMillis = retryAfterMillis(response.retryAfter),
            )
    }

    companion object {
        internal fun userId(cookies: String) =
            Regex("(?:^|;\\s*)member_id=(\\d+)(?:;|$)")
                .find(cookies)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: 0

        internal fun cookieHeader(headers: List<String>): String =
            headers
                .mapNotNull { Cookie.parse("https://${ForumUrls.HOST}/".toHttpUrl(), it) }
                .associateBy { it.name }
                .values
                .filter { it.expiresAt > System.currentTimeMillis() }
                .filter { !it.name.startsWith("cf_", true) && !it.name.startsWith("__cf", true) }
                .joinToString("; ") { "${it.name}=${it.value}" }
    }
}
