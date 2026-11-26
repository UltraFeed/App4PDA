package io.github.ultrafeed.app4pda.app

import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.ultrafeed.app4pda.data.forum.ForumUrls
import io.github.ultrafeed.app4pda.data.forum.ForumVerification
import io.github.ultrafeed.app4pda.data.forum.forumAccessEvent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
internal fun ForumVerificationDialog() {
    LifecycleResumeEffect(Unit) {
        ForumVerification.foreground = true
        onPauseOrDispose { ForumVerification.foreground = false }
    }
    val request by ForumVerification.requests.collectAsState()
    val context = LocalContext.current
    request?.let { current ->
        key(current) {
            DisposableEffect(current) {
                onDispose {
                    if (!current.result.isCompleted) {
                        forumAccessEvent(context, "verification-disposed", current.url)
                        current.result.complete(null)
                    }
                }
            }
            Dialog(
                onDismissRequest = {
                    forumAccessEvent(context, "verification-cancelled", current.url)
                    current.result.complete(null)
                },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webViewClient = ForumVerificationClient(current)
                            loadUrl(current.url)
                        }
                    },
                    onRelease = { view ->
                        view.stopLoading()
                        view.destroy()
                    },
                )
            }
        }
    }
}

private class ForumVerificationClient(private val verification: ForumVerification.Request) :
    WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        request.isForMainFrame &&
            (request.url.scheme != "https" || request.url.host != ForumUrls.HOST)

    override fun onPageCommitVisible(view: WebView, url: String) {
        inspectPage(view, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        forumAccessEvent(view.context, "verification-finished", url)
        inspectPage(view, url)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        response: WebResourceResponse,
    ) {
        forumAccessEvent(
            view.context,
            if (request.isForMainFrame) "verification-main-http" else "verification-resource-http",
            request.url.toString(),
            response.statusCode,
        )
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        forumAccessEvent(
            view.context,
            if (request.isForMainFrame) "verification-main-error"
            else "verification-resource-error",
            request.url.toString(),
            error.errorCode,
        )
        if (request.isForMainFrame) verification.result.complete(null)
    }

    private fun inspectPage(view: WebView, url: String) {
        val target = url.toHttpUrlOrNull()
        if (view.url != url || target?.host != ForumUrls.HOST) return
        if (target.scheme != "https" || !target.encodedPath.startsWith("/forum/")) return
        view.evaluateJavascript(
            "!!document.querySelector('#ipbwrapper, [id^=post-main-], .postcontent, input[name=\"captcha-time\"], input[name=\"login\"]') && !document.querySelector('#challenge-running, #challenge-stage, script[src*=\"/cdn-cgi/challenge-platform/\"]')",
        ) { ready ->
            if (ready == "true" && view.url == url && !verification.result.isCompleted) {
                val cookie =
                    CookieManager.getInstance()
                        .getCookie(url)
                        .orEmpty()
                        .split(';')
                        .map(String::trim)
                        .firstOrNull { it.startsWith("cf_clearance=") }
                forumAccessEvent(view.context, "verification-ready", url)
                verification.result.complete(cookie.orEmpty())
            }
        }
    }
}
