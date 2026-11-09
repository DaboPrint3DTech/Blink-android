package com.daboprint3dtech.blink

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.daboprint3dtech.blink.data.BlinkRepository
import com.daboprint3dtech.blink.data.api.BlinkApi
import com.daboprint3dtech.blink.data.api.HttpClients
import com.daboprint3dtech.blink.data.api.OAuthClient
import com.daboprint3dtech.blink.data.auth.AuthManager
import com.daboprint3dtech.blink.data.auth.AuthState
import com.daboprint3dtech.blink.data.auth.KeystoreTokenCipher
import com.daboprint3dtech.blink.data.auth.SessionStore
import com.daboprint3dtech.blink.data.settings.AutoRefreshSettings
import com.daboprint3dtech.blink.data.settings.AutoRefreshStore
import com.daboprint3dtech.blink.data.thumbs.ThumbnailCache
import com.daboprint3dtech.blink.ui.components.RemoteImageLoader
import com.daboprint3dtech.blink.work.AutoRefreshEngine
import com.daboprint3dtech.blink.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

private val Context.sessionDataStore by preferencesDataStore(name = "session")
private val Context.autoRefreshDataStore by preferencesDataStore(name = "auto_refresh")

/** Contenitore delle dipendenze (DI manuale, l'app è piccola). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = HttpClients.default()
    private val sessionStore = SessionStore(context.sessionDataStore, KeystoreTokenCipher())
    val auth = AuthManager(OAuthClient(http), sessionStore)
    private val api = BlinkApi(http, auth)
    val repository = BlinkRepository(api, ThumbnailCache(File(context.filesDir, "thumbnails")))
    val autoRefreshStore = AutoRefreshStore(context.autoRefreshDataStore)
    val refreshScheduler = RefreshScheduler(context)
    val autoRefreshEngine = AutoRefreshEngine(repository, autoRefreshStore)
    val imageLoader = RemoteImageLoader(repository)

    /** Salva le impostazioni e riprogramma il lavoro in background. */
    suspend fun updateAutoRefresh(transform: (AutoRefreshSettings) -> AutoRefreshSettings): AutoRefreshSettings {
        val updated = autoRefreshStore.update(transform)
        if (updated.enabled) refreshScheduler.restart(updated.unmeteredOnly) else refreshScheduler.cancel()
        return updated
    }

    suspend fun logout() {
        refreshScheduler.cancel()
        auth.logout()
        repository.clear()
        imageLoader.clear()
        autoRefreshStore.clearAttempts()
    }
}

class BlinkApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            container.auth.init()
            val settings = container.autoRefreshStore.current()
            if (container.auth.state.value is AuthState.LoggedIn && settings.enabled) {
                container.refreshScheduler.ensureScheduled(settings.unmeteredOnly)
            }
        }
    }
}
