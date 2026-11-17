package io.github.ultrafeed.app4pda.app

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.ultrafeed.app4pda.data.forum.ForumVerification

@Composable
internal fun ForumVerificationDialog() {
    LifecycleResumeEffect(Unit) {
        ForumVerification.foreground = true
        onPauseOrDispose { ForumVerification.foreground = false }
    }
    val request by ForumVerification.requests.collectAsState()
    request?.let { current ->
        DisposableEffect(current) {
            onDispose { current.result.complete(null) }
        }
        Dialog(
            onDismissRequest = { current.result.complete(null) },
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
                        webViewClient =
                            object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean =
                                    request.isForMainFrame &&
                                        (request.url.scheme != "https" ||
                                            request.url.host != "4pda.to")

                                override fun onPageFinished(view: WebView, url: String) {
                                    if (url != current.url) return
                                    view.evaluateJavascript(
                                        "!!document.querySelector('#ipbwrapper, [id^=post-main-], input[name=\"captcha-time\"], input[name=\"login\"]') && !document.querySelector('script[src*=\"/cdn-cgi/challenge-platform/\"]')",
                                    ) { ready ->
                                        if (ready == "true") {
                                            val cookie =
                                                CookieManager.getInstance()
                                                    .getCookie(current.url)
                                                    .orEmpty()
                                                    .split(';')
                                                    .map(String::trim)
                                                    .firstOrNull { it.startsWith("cf_clearance=") }
                                            current.result.complete(cookie.orEmpty())
                                        }
                                    }
                                }
                            }
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
