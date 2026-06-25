package com.fortnitecloudsync.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONTokener

/**
 * In-app Epic Games sign-in.
 *
 * Loads Epic's authorization-code redirect page in a WebView, lets the user log
 * in normally, and then reads the resulting authorizationCode JSON straight off
 * the page — no manual copy/paste. The captured text is handed to the existing
 * [onCodeCaptured] callback, which feeds it through the unchanged authentication
 * path (the Python backend still parses the code and exchanges it). This screen
 * only replaces the "how the user obtains the code" step; it touches nothing in
 * the script <-> API exchange.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(
    authUrl: String,
    onCodeCaptured: (String) -> Unit,
    onCancel: () -> Unit,
    onLog: (String) -> Unit = {}
) {
    var loading by remember { mutableStateOf(true) }
    var captured by remember { mutableStateOf(false) }
    val context = LocalContext.current

    BackHandler(onBack = onCancel)

    val webView = remember {
        onLog("🌐 In-app sign-in opened; loading Epic login…")
        CookieManager.getInstance().setAcceptCookie(true)
        WebView(context).apply {
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Present as a normal mobile Chrome browser (not an "; wv" WebView) so
            // Epic serves its standard mobile login page and is less likely to flag
            // the embedded client.
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    loading = true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    loading = false
                    val path = url?.substringBefore('?') ?: ""
                    onLog("Page loaded: ${path.take(60).ifEmpty { "?" }}")
                    // Only capture on the ACTUAL code page. Match the path, not the
                    // whole URL — the logout/login pages carry "id/api/redirect" in
                    // their query string, and matching that would fire capture (and
                    // close the screen) before the user ever sees the login form.
                    // Android's WebView may render the code JSON through a viewer that
                    // reformats it, so read the code value out directly in JS.
                    if (!captured && path.endsWith("/id/api/redirect")) {
                        val js = "(function(){" +
                            "var t=(document.body&&document.body.innerText)||'';" +
                            "var m=t.match(/authorizationCode[^a-zA-Z0-9]{0,12}([a-zA-Z0-9]{20,48})/);" +
                            "return m?m[1]:'';})();"
                        view.evaluateJavascript(js) { raw ->
                            val code = runCatching { JSONTokener(raw).nextValue() as? String }
                                .getOrNull()
                            if (!captured && !code.isNullOrBlank()) {
                                captured = true
                                onLog("✅ Captured authorization code from Epic")
                                onCodeCaptured(code)
                            } else if (!captured) {
                                onLog("⚠️ Reached the code page but couldn't read the code")
                            }
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        onLog("❌ WebView error: ${error?.description ?: "unknown"}")
                    }
                }
            }
            loadUrl(authUrl)
        }
    }

    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in with Epic Games") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel sign-in")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { webView }
            )
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
