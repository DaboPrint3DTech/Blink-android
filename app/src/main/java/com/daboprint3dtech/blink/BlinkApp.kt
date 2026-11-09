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
import com.daboprint3dtech.blink.data.prefs.CameraPreferences
import com.daboprint3dtech.blink.data.prefs.CameraPreferencesStore
import com.daboprint3dtech.blink.data.settings.AutoRefreshSettings
import com.daboprint3dtech.blink.data.settings.AutoRefreshStore
import com.daboprint3dtech.blink.data.thumbs.ThumbnailCache
import com.daboprint3dtech.blink.ui.components.RemoteImageLoader
import com.daboprint3dtech.blink.work.AutoRefreshEngine
import com.daboprint3dtech.blink.work.BatteryNotifier
import com.daboprint3dtech.blink.work.RefreshScheduler
import com.daboprint3dtech.blink.widget.BlinkWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

private val Context.sessionDataStore by preferencesDataStore(name = "session")
private val Context.autoRefreshDataStore by preferencesDataStore(name = "auto_refresh")
private val Context.cameraPrefsDataStore by preferencesDataStore(name = "camera_prefs")

/** Contenitore delle dipendenze (DI manuale, l'app è piccola). */
class AppContainer(private val appContext: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = HttpClients.default()
    private val sessionStore = SessionStore(appContext.sessionDataStore, KeystoreTokenCipher())
    val auth = AuthManager(OAuthClient(http), sessionStore)
    private val api = BlinkApi(http, auth)
    val repository = BlinkRepository(api, ThumbnailCache(File(appContext.filesDir, "thumbnails")))
    val autoRefreshStore = AutoRefreshStore(appContext.autoRefreshDataStore)
    val cameraPreferencesStore = CameraPreferencesStore(appContext.cameraPrefsDataStore)
    val refreshScheduler = RefreshScheduler(appContext)
    val widgetUpdater = BlinkWidgetUpdater(appContext)
    private val batteryNotifier = BatteryNotifier(appContext, cameraPreferencesStore)
    val autoRefreshEngine = AutoRefreshEngine(
        repository,
        autoRefreshStore,
        afterRun = {
            batteryNotifier.check(repository.homescreen.value?.cameras.orEmpty())
            widgetUpdater.update()
        },
    )
    val imageLoader = RemoteImageLoader(repository)

    /**
     * Esegue un'operazione nello scope dell'app: continua anche se l'utente chiude la schermata
     * che l'ha avviata (es. scatto di più miniature).
     */
    fun <T> launchDetached(block: suspend () -> T): Deferred<T> = appScope.async { block() }

    /** Salva le impostazioni e riprogramma il lavoro in background. */
    suspend fun updateAutoRefresh(transform: (AutoRefreshSettings) -> AutoRefreshSettings): AutoRefreshSettings {
        val updated = autoRefreshStore.update(transform)
        if (updated.enabled) refreshScheduler.restart(updated.unmeteredOnly) else refreshScheduler.cancel()
        return updated
    }

    suspend fun updateCameraPreferences(transform: (CameraPreferences) -> CameraPreferences): CameraPreferences {
        val updated = cameraPreferencesStore.update(transform)
        widgetUpdater.update()
        return updated
    }

    suspend fun logout() {
        refreshScheduler.cancel()
        auth.logout()
        repository.clear()
        imageLoader.clear()
        autoRefreshStore.clearAttempts()
        cameraPreferencesStore.setAlertedKeys(emptySet())
        widgetUpdater.update()
    }
}

class BlinkApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Lo snapshot per il widget è costruito dallo stato in memoria (telecamere, preferiti).
        container.widgetUpdater.snapshotProvider = {
            val home = container.repository.homescreen.value
            com.daboprint3dtech.blink.widget.WidgetSnapshotStore.build(
                loggedIn = container.auth.state.value is AuthState.LoggedIn,
                cameras = home?.cameras.orEmpty(),
                prefs = container.cameraPreferencesStore.current(),
                thumbnailMillis = { it.thumbnailTime?.toEpochMilli() },
            )
        }
        container.appScope.launch {
            container.auth.init()
            val settings = container.autoRefreshStore.current()
            if (container.auth.state.value is AuthState.LoggedIn && settings.enabled) {
                container.refreshScheduler.ensureScheduled(settings.unmeteredOnly)
            }
            // Primo riempimento del widget (anche se l'aggiornamento automatico è spento).
            runCatching { container.repository.refresh() }
            container.widgetUpdater.update()
        }
    }
}
