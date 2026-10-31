package com.github.yumelira.yumebox.screen.about

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.github.yumelira.yumebox.common.util.openUrl
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import java.io.ByteArrayInputStream
import java.net.URLConnection

private const val README_HOST = "yumebox-readme.invalid"
private const val README_START = "https://$README_HOST/README.md"
private val readmePages = setOf("/README.md", "/docs/README.md", "/docs/README_ZH_HANS.md")

@Composable
@Destination<RootGraph>
fun ReadmeScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    fun cssColor(value: androidx.compose.ui.graphics.Color) = "#%06x".format(value.toArgb() and 0xffffff)
    val css = "--bg:${cssColor(colors.surface)};--fg:${cssColor(colors.onSurface)};" +
        "--link:${cssColor(colors.primary)};--muted:${cssColor(colors.surfaceContainer)};" +
        "--border:${cssColor(colors.outlineVariant)};--scheme:${if (colors.surface.luminance() < .5f) "dark" else "light"};"
    val latestCss by rememberUpdatedState(css)
    var restoreScroll by remember { mutableStateOf<Int?>(null) }
    val textZoom = (LocalDensity.current.fontScale * 100).toInt()
    var currentUrl by rememberSaveable { mutableStateOf(README_START) }
    var scrollY by rememberSaveable { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else navigator.popBackStack()
    }

    Scaffold(topBar = { TopBar(title = "Readme") }) { innerPadding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            factory = { viewContext ->
                val restoreY = scrollY
                WebView(viewContext).apply {
                    webView = this
                    tag = css
                    restoreScroll = restoreY
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.textZoom = textZoom
                    setBackgroundColor(colors.surface.toArgb())
                    setOnScrollChangeListener { _, _, y, _, _ -> scrollY = y }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            val uri = request.url
                            if (uri.host != README_HOST) return null
                            val path = uri.path.orEmpty()
                            if (path.split('/').any { it == ".." }) return missingAsset()
                            return try {
                                val input = context.assets.open("readme$path")
                                if (path in readmePages) {
                                    val html = input.bufferedReader().use { it.readText() }.replace("/*APP_THEME*/", latestCss)
                                    WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
                                } else WebResourceResponse(when (path.substringAfterLast('.').lowercase()) {
                                    "webp" -> "image/webp"
                                    "svg" -> "image/svg+xml"
                                    else -> URLConnection.guessContentTypeFromName(path) ?: "application/octet-stream"
                                }, null, input)
                            } catch (_: java.io.IOException) { missingAsset() }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val uri = request.url
                            if (uri.host == README_HOST && uri.path in readmePages) return false
                            if (uri.scheme in setOf("https", "http")) {
                                val target = if (uri.host == README_HOST) {
                                    "https://github.com/Yizuka17/YumeBox-MaterialDesign/blob/Yume${uri.encodedPath}" +
                                        (uri.encodedFragment?.let { "#$it" } ?: "")
                                } else uri.toString()
                                openUrl(context, target)
                            }
                            return true
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            if (url != null) currentUrl = url
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            currentUrl = url
                            restoreScroll?.let { y -> view.scrollTo(0, y); restoreScroll = null }
                        }
                    }
                    loadUrl(currentUrl)
                }
            },
            update = { view ->
                view.settings.textZoom = textZoom
                if (view.tag != css) {
                    restoreScroll = view.scrollY
                    view.tag = css
                    view.setBackgroundColor(colors.surface.toArgb())
                    view.reload()
                }
            },
            onRelease = { view ->
                webView = null
                view.stopLoading()
                view.setOnScrollChangeListener(null)
                view.webViewClient = WebViewClient()
                view.destroy()
            },
        )
    }
}

private fun missingAsset() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(),
    ByteArrayInputStream("Not found".toByteArray()))
