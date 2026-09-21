package io.shikimove.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import io.shikimove.app.data.AccountRepository
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.shikimove.app.ui.ShikiApp
import io.shikimove.app.ui.ShikiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { ShikiTheme { ShikiApp() } }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            val account = AccountRepository.get(this@MainActivity)
            account.sync()
            while (true) { delay(30_000); if (account.store.pending.value.isNotEmpty()) account.sync() }
        } }
    }
}

fun openBrowser(context: Context, url: String) {
    if (!url.startsWith("https://")) return
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (_: Exception) { Toast.makeText(context, "Не найден браузер для открытия ссылки", Toast.LENGTH_LONG).show() }
}
