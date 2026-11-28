package io.github.munhozmib.cifrario.ui.components

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * Mostra a cópia offline (MHTML) da página do site, exatamente como foi salva.
 * Os scripts do site ficam desligados: é só leitura, sem anúncios pulando nem internet.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun OriginalPage(
    path: String,
    textZoom: Int,
    onReady: (WebView?) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = true
                settings.blockNetworkLoads = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                isFocusable = false
                webViewClient = object : WebViewClient() {
                    // Links da página salva não navegam (estamos offline).
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest) = true
                    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                        onReady(null)
                        (view.parent as? android.view.ViewGroup)?.removeView(view)
                        view.destroy()
                        return true
                    }
                }
                val taps = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onTap(); return false }
                })
                setOnTouchListener { _, e -> taps.onTouchEvent(e); false }
                loadUrl(File(path).toURI().toString())
                onReady(this)
            }
        },
        update = { it.settings.textZoom = textZoom },
        onRelease = { onReady(null); it.destroy() },
    )
}
