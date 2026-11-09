package com.daboprint3dtech.blink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.daboprint3dtech.blink.ui.BlinkAppRoot
import com.daboprint3dtech.blink.ui.theme.BlinkTheme
import com.daboprint3dtech.blink.work.ForegroundAutoRefresh
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as BlinkApp).container
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
            BlinkTheme { BlinkAppRoot() }
        }
    }
}
