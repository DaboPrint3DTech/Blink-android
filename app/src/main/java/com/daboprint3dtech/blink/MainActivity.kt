package com.daboprint3dtech.blink

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.daboprint3dtech.blink.ui.BlinkAppRoot
import com.daboprint3dtech.blink.ui.DeepLink
import com.daboprint3dtech.blink.ui.theme.BlinkTheme
import com.daboprint3dtech.blink.work.ForegroundAutoRefresh
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var deepLink by mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as BlinkApp).container
        deepLink = parseIntent(intent)
        val foregroundRefresh = ForegroundAutoRefresh(
            container.auth,
            container.autoRefreshStore,
            container.autoRefreshEngine,
            container.refreshScheduler,
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { foregroundRefresh.run() }
        }
        setContent {
            BlinkTheme {
                BlinkAppRoot(deepLink = deepLink, onDeepLinkHandled = { deepLink = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIntent(intent)?.let { deepLink = it }
    }

    private fun parseIntent(intent: Intent?): DeepLink? = when (intent?.action) {
        "open-camera" -> intent.getStringExtra("camera_key")?.let { DeepLink.Camera(it) }
        "open-battery" -> DeepLink.Battery
        else -> null
    }
}
