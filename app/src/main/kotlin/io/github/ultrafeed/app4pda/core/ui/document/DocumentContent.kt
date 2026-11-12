package io.github.ultrafeed.app4pda.core.ui.document

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as GraphicsColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.images.AppImages
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import java.net.URI
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.koin.compose.koinInject

/** HTML is sanitized before reaching this view. Only our local interaction script executes. */
@Composable
fun DocumentContent(
    document: String,
    origin: ImageOrigin,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fitContent: Boolean = false,
    gallery: List<String> = emptyList(),
    galleryPreviews: List<String> = gallery,
    onImages: (List<String>, Int) -> Unit = { _, _ -> },
    onLink: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val surface = if (compact) colors.surfaceContainerHigh else colors.surface
    val content =
        remember(document, gallery, galleryPreviews) {
            DocumentPresentation(document, gallery.toList(), galleryPreviews.toList())
        }
    val css = documentStyle(colors, surface, compact)
    Column(modifier.background(surface)) {
        DocumentWebView(content, origin, css, fitContent, onImages, onLink)
    }
}

@Composable
private fun ColumnScope.DocumentWebView(
    content: DocumentPresentation,
    origin: ImageOrigin,
    css: String,
    fitContent: Boolean,
    onImages: (List<String>, Int) -> Unit,
    onLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val images: AppImages = koinInject()
    val imageSession =
        remember(content, origin) { DocumentImages(context, images, origin, content.imageUrls()) }
    val currentImageSession by rememberUpdatedState(imageSession)
    DisposableEffect(imageSession) { onDispose { imageSession.close() } }
    var contentHeight by remember(content) { mutableIntStateOf(1) }
    val documentId = remember(content, origin) { UUID.randomUUID().toString() }
    val currentDocumentId by rememberUpdatedState(documentId)
    val spoilerLabel = stringResource(R.string.forum_spoiler)
    val currentLink by rememberUpdatedState(onLink)
    val currentGallery by rememberUpdatedState(content.gallery)
    val currentImages by rememberUpdatedState(onImages)
    val updateHeight by
        rememberUpdatedState<(Int) -> Unit> {
            contentHeight = it
        }

    AndroidView(
        modifier =
            (if (fitContent) Modifier.height(contentHeight.dp) else Modifier.weight(1f))
                .fillMaxWidth(),
        factory = { context ->
            createDocumentView(
                context,
                DocumentViewCallbacks(
                    report = { view, id, height ->
                        view.post {
                            if (fitContent && view.isAttachedToWindow && id == currentDocumentId)
                                updateHeight(
                                    height,
                                )
                        }
                    },
                    gesture = { view, id, exclusive ->
                        view.post {
                            if (view.isAttachedToWindow && id == currentDocumentId) {
                                view.horizontalContentGesture = exclusive
                                if (exclusive) view.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                    },
                    intercept = { currentImageSession.intercept(it) },
                    onRequest = { request ->
                        documentNavigation(request, currentGallery, currentImages, currentLink)
                    },
                    onDownload = { currentLink(it) },
                ),
            )
        },
        onRelease = WebView::releaseDocument,
        update = { view ->
            view.displayDocument(
                content,
                origin,
                DocumentStyle(css, spoilerLabel, fitContent, documentId),
            )
        },
    )
}

private const val BASE = "https://4pda.to/forum/"
private const val GALLERY_SCHEME = "app-gallery"

/** Presentation-only markup; cached forum content is never rewritten. */
internal data class DocumentPresentation(
    val document: String,
    val gallery: List<String>,
    private val previews: List<String>,
) {
    fun imageUrls(): Set<String> =
        Jsoup.parseBodyFragment(body(), BASE)
            .select("img[src]")
            .map { it.absUrl("src") }
            .filter(::safeImage)
            .toSet()

    fun body(): String {
        val strip = Element("div").addClass("app-gallery")
        gallery.forEachIndexed { index, original ->
            if (!safeImage(original)) return@forEachIndexed
            val preview = previews.getOrNull(index)?.takeIf(::safeImage) ?: original
            strip
                .appendElement("a")
                .attr("href", "$GALLERY_SCHEME://$index")
                .appendElement("img")
                .attr("src", preview)
                .attr("alt", "${index + 1}")
        }
        return (if (strip.childrenSize() == 0) "" else strip.outerHtml()) + document
    }
}

internal fun safeImage(url: String): Boolean = runCatching {
    URI(url).let {
        it.scheme.equals("https", true) && !it.host.isNullOrBlank() && it.rawUserInfo == null
    }
}
    .getOrDefault(false)

/** Sanitized local documents may only report their own height; no Android resources are exposed. */
private class DocumentBridge(
    private val report: (String, Int) -> Unit,
    private val gesture: (String, Boolean) -> Unit,
) {
    // Called by the local document script through the AppDocument bridge.
    @Suppress("unused")
    @JavascriptInterface
    fun horizontalGesture(documentId: String, exclusive: Boolean) = gesture(documentId, exclusive)

    @Suppress("unused")
    @JavascriptInterface
    fun reportHeight(documentId: String, height: Int) {
        if (height > 0) report(documentId, height)
    }
}

private fun page(
    body: String,
    css: String,
    spoilerLabel: String,
    fitContent: Boolean,
    documentId: String,
) =
    """
    <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https:; style-src 'unsafe-inline'; script-src 'nonce-app-document'; base-uri 'none'; form-action 'none'">
    <style id="app-theme">$css</style></head><body>$body
    <script nonce="app-document">
    var lastTouch=null;
    document.addEventListener("touchstart",function(e){var node=e.target.closest(".app-gallery,table,pre,.post-block.code");AppDocument.horizontalGesture(${
    JSONObject.quote(
        documentId,
    )
},e.touches.length>1||!!(node&&node.scrollWidth>node.clientWidth));},{passive:true});
    ["touchend","touchcancel"].forEach(function(name){document.addEventListener(name,function(){AppDocument.horizontalGesture(${
    JSONObject.quote(
        documentId,
    )
},false);},{passive:true});});
    document.addEventListener('touchend',function(e){var t=e.changedTouches[0];if(t)lastTouch={x:t.clientX,y:t.clientY,at:performance.now()};},true);
    document.addEventListener('touchcancel',function(){lastTouch=null;},true);
    function actualHit(element,event){
      if(!lastTouch||event.detail===0||performance.now()-lastTouch.at>750)return true;
      return Array.from(element.getClientRects()).some(function(r){return lastTouch.x>=r.left&&lastTouch.x<r.right&&lastTouch.y>=r.top&&lastTouch.y<r.bottom;});
    }
    document.addEventListener('click',function(e){var link=e.target.closest('a');if(link&&!actualHit(link,e)){e.preventDefault();e.stopPropagation();}},true);
    document.querySelectorAll('.spoil > .block-title').forEach(function(title){
      if(!title.textContent.trim())title.appendChild(document.createTextNode(${
    JSONObject.quote(
        spoilerLabel,
    )
}));
      title.setAttribute('role','button');title.tabIndex=0;
      function toggle(){title.parentElement.classList.toggle('close');title.setAttribute('aria-expanded',!title.parentElement.classList.contains('close'));}
      title.setAttribute('aria-expanded',!title.parentElement.classList.contains('close'));
      title.addEventListener('click',function(e){if(!actualHit(title,e)){e.preventDefault();return;}if(!e.target.closest('a'))toggle();});
      title.addEventListener('keydown',function(e){if(e.key==='Enter'||e.key===' '){e.preventDefault();toggle();}});
    });
    document.querySelectorAll('img').forEach(function(image){
      if(!image.closest('a')&&image.src){var link=document.createElement('a');link.href=image.src;image.replaceWith(link);link.appendChild(image);}
    });
    if ($fitContent) {
      var measuredHeight=0;
      function reportHeight(){
        var height=Math.max(1,Math.ceil(document.body.getBoundingClientRect().height));
        if(height!==measuredHeight){measuredHeight=height;AppDocument.reportHeight(${
    JSONObject.quote(
        documentId,
    )
},height);}
      }
      new ResizeObserver(reportHeight).observe(document.body);
      reportHeight();
    }
    </script></body></html>
"""
        .trimIndent()

private const val RGB_MASK = 0xffffff
private const val LIGHT_SURFACE_LUMINANCE = 0.5f

private fun documentStyle(
    colors: ColorScheme,
    surface: GraphicsColor,
    compact: Boolean,
): String {
    fun hex(color: GraphicsColor) = "#%06x".format(Locale.ROOT, color.toArgb() and RGB_MASK)
    return """
        :root {color-scheme:${if (surface.luminance() < LIGHT_SURFACE_LUMINANCE) "dark" else "light"}}
        * {box-sizing:border-box}
        html,body {margin:0;padding:0;background:${hex(surface)};color:${hex(colors.onSurface)}}
        body {padding:16px;font-family:system-ui,sans-serif;font-size:${if (compact) COMPACT_FONT_PX else BODY_FONT_PX}px;line-height:1.5;overflow-wrap:anywhere}
        a {color:${hex(colors.primary)};text-decoration:none}
        img {max-width:100%;height:auto;vertical-align:middle}
        .app-gallery {display:flex;gap:8px;overflow-x:auto;margin:0 0 16px;padding:0 0 8px}
        .app-gallery a {flex:0 0 auto;display:flex;align-items:center;border-radius:12px;overflow:visible}
        .app-gallery img {display:block;height:auto!important;width:auto!important;max-height:160px;max-width:80vw;object-fit:contain}
        p {margin:0 0 12px} ul,ol {padding-left:24px}
        h1,h2,h3,.block-title {font-weight:500}
        .post-block,blockquote,pre {margin:12px 0;border-radius:20px}
        .post-block.quote,blockquote {background:${hex(colors.surfaceContainer)};padding:12px 16px;border-left:3px solid ${
        hex(
            colors.outlineVariant,
        )
    }}
        .post-block.code,pre {background:${hex(colors.surfaceContainerHigh)};padding:12px}
        pre,.code .block-body {white-space:pre;overflow-x:auto;font-family:monospace}
        .post-block.spoil {padding:0;background:transparent}
        .spoil > .block-title,summary {display:table;max-width:100%;padding:10px 18px;border-radius:24px;background:${
        hex(
            colors.secondaryContainer,
        )
    };color:${hex(colors.onSecondaryContainer)};cursor:pointer;font-style:normal}
        .spoil > .block-title:before {content:'▾';margin-right:8px}
        .spoil.close > .block-title:before {content:'▸'}
        .spoil > .block-body {padding:12px 0}
        .post-block.close > .block-body {display:none}
        .hidden {background:${hex(colors.surfaceContainer)};padding:12px}
        table {max-width:100%;display:block;overflow-x:auto;border-collapse:collapse}
        td,th {padding:8px;border:1px solid ${hex(colors.outlineVariant)}}
        .edit,.post-edit-reason {color:${hex(colors.onSurfaceVariant)};font-size:12px}
        a:focus-visible,.block-title:focus-visible {outline:2px solid ${hex(colors.primary)}}
        @media(prefers-reduced-motion:reduce) {* {animation:none!important;transition:none!important}}
    """
        .trimIndent()
}

/** Owns the platform view; callbacks read the current Compose state without reloading HTML. */
private data class DocumentViewCallbacks(
    val report: (WebView, String, Int) -> Unit,
    val gesture: (DocumentView, String, Boolean) -> Unit,
    val intercept: (WebResourceRequest) -> WebResourceResponse?,
    val onRequest: (WebResourceRequest) -> Boolean,
    val onDownload: (String) -> Unit,
)

private fun createDocumentView(
    context: Context,
    callbacks: DocumentViewCallbacks,
): DocumentView =
    DocumentView(context).apply {
        isSaveEnabled = false
        isSaveFromParentEnabled = false
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        val view = this
        addJavascriptInterface(
            DocumentBridge(
                report = { id, height -> callbacks.report(view, id, height) },
                gesture = { id, exclusive -> callbacks.gesture(view, id, exclusive) },
            ),
            "AppDocument",
        )
        webViewClient =
            object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ) = callbacks.intercept(request)

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    return callbacks.onRequest(request)
                }
            }
        setDownloadListener { url, _, _, _, _ -> callbacks.onDownload(url) }
    }

/** The document owns vertical drags; the pager may take horizontal drags outside HTML scrollers. */
private class DocumentView(context: Context) : WebView(context) {
    var fitContent = false
    var horizontalContentGesture = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var horizontalDrag: Boolean? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            startX = event.x
            startY = event.y
            horizontalDrag = null
            horizontalContentGesture = false
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE && horizontalDrag == null) {
            val dx = abs(event.x - startX)
            val dy = abs(event.y - startY)
            if (maxOf(dx, dy) > touchSlop) horizontalDrag = dx > dy
        }
        val exclusive =
            event.pointerCount > 1 ||
                horizontalContentGesture ||
                (!fitContent && horizontalDrag != true)
        parent?.requestDisallowInterceptTouchEvent(exclusive)
        val handled = super.dispatchTouchEvent(event)
        val finished =
            event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
        parent?.requestDisallowInterceptTouchEvent(!finished && exclusive)
        return handled
    }
}

private const val COMPACT_FONT_PX = 12
private const val BODY_FONT_PX = 14

private fun documentNavigation(
    request: WebResourceRequest,
    gallery: List<String>,
    onImages: (List<String>, Int) -> Unit,
    onLink: (String) -> Unit,
): Boolean {

    val url = request.url
    return when {
        !request.isForMainFrame -> false
        url.scheme == GALLERY_SCHEME -> {
            val index = url.host?.toIntOrNull()
            if (index != null && index in gallery.indices) onImages(gallery, index)
            true
        }

        "$url".substringBefore('#') == BASE && url.fragment != null -> false
        else -> {
            onLink("$url")
            true
        }
    }
}

/** A palette update changes only the local stylesheet; identical content keeps its WebView. */
private data class DocumentStyle(
    val css: String,
    val spoilerLabel: String,
    val fitContent: Boolean,
    val documentId: String,
)

private fun DocumentView.displayDocument(
    content: DocumentPresentation,
    origin: ImageOrigin,
    style: DocumentStyle,
) {
    fitContent = style.fitContent
    if (tag != content to origin) {
        tag = content to origin
        loadDataWithBaseURL(
            BASE,
            page(content.body(), style.css, style.spoilerLabel, style.fitContent, style.documentId),
            "text/html",
            "UTF-8",
            null,
        )
    } else {
        evaluateJavascript(
            "(function(){var theme=document.getElementById('app-theme');if(theme)theme.textContent=" +
                JSONObject.quote(
                    style.css,
                ) +
                ";})()",
            null,
        )
    }
}

private fun WebView.releaseDocument() {
    stopLoading()
    webViewClient = WebViewClient()
    removeJavascriptInterface("AppDocument")
    setDownloadListener(null)
    destroy()
}
