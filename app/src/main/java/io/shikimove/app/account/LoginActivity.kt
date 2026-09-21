package io.shikimove.app.account

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import io.shikimove.app.data.AccountApi
import io.shikimove.app.data.AccountRepository
import io.shikimove.app.ui.ShikiTheme
import io.shikimove.app.openBrowser
import kotlinx.coroutines.*

class LoginActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var checking by mutableStateOf(false)
    private var message by mutableStateOf("Войдите на сайте и нажмите «Готово».")
    private var checkJob: Job? = null
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShikiTheme {
            BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else finish() }
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { finish() }) { Text("Закрыть") }
                    TextButton(onClick = { verify(true) }, enabled = !checking) { Text(if (checking) "Проверяем…" else "Готово") }
                }
                Text("Вход · shikimori.io", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
                Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context -> WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                    settings.allowFileAccess = false; settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    CookieManager.getInstance().setAcceptCookie(true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame) return false
                            val uri = request.url
                            if (uri.scheme == "https" && uri.host == "shikimori.io") return false
                            if (uri.scheme == "https") openBrowser(this@LoginActivity, uri.toString())
                            return true
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            if (Uri.parse(url).host == "shikimori.io" && !url.contains("sign_in")) verify(false)
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) message = "Страница не загрузилась. Проверьте соединение и откройте вход заново."
                        }
                    }
                    loadUrl(AccountApi.ORIGIN + "users/sign_in")
                } })
            }
        } }
    }
    private fun verify(explicit: Boolean) {
        if (checkJob?.isActive == true) return
        checkJob = lifecycleScope.launch {
            checking = true
            try {
                if (AccountRepository.get(this@LoginActivity).finishLogin()) finish()
                else if (explicit) message = "Вход ещё не завершён. Войдите с логином и паролем Shikimori на странице ниже."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (explicit) message = "Не удалось проверить аккаунт. Попробуйте ещё раз." }
            finally { checking = false }
        }
    }
    override fun onDestroy() { webView?.stopLoading(); webView?.destroy(); webView = null; super.onDestroy() }
}
