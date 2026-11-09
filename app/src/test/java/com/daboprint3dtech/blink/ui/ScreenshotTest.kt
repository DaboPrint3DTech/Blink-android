package com.daboprint3dtech.blink.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.api.BlinkNetwork
import com.daboprint3dtech.blink.data.api.DeviceKind
import com.daboprint3dtech.blink.data.api.Homescreen
import com.daboprint3dtech.blink.data.settings.AutoRefreshSettings
import com.daboprint3dtech.blink.data.settings.CameraRefreshConfig
import com.daboprint3dtech.blink.ui.home.HomeActions
import com.daboprint3dtech.blink.ui.home.HomeContent
import com.daboprint3dtech.blink.ui.home.HomeUiState
import com.daboprint3dtech.blink.ui.login.LoginActions
import com.daboprint3dtech.blink.ui.login.LoginContent
import com.daboprint3dtech.blink.ui.login.LoginUiState
import com.daboprint3dtech.blink.ui.settings.SettingsActions
import com.daboprint3dtech.blink.ui.settings.SettingsContent
import com.daboprint3dtech.blink.ui.settings.SettingsUiState
import com.daboprint3dtech.blink.ui.theme.BlinkTheme
import com.daboprint3dtech.blink.work.AutoRefreshRun
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Renderizza le schermate principali con dati di esempio (senza rete) e salva i PNG in
 * app/build/screenshots: verifica che la UI si componga senza errori.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }
    private val now = Instant.now()

    private fun cam(id: Long, name: String, minutesAgo: Long, kind: DeviceKind = DeviceKind.CAMERA, network: Long = 1, battery: String? = "ok", online: Boolean = true, motion: Boolean = true) =
        BlinkCamera(
            id = id, networkId = network, name = name, kind = kind, productType = "catalina", motionEnabled = motion,
            status = if (online) "online" else "offline", batteryState = battery, batteryLevel = 3, wifiSignal = 4,
            syncSignal = 5, temperatureF = 66, thumbnailPath = (now.epochSecond - minutesAgo * 60).toString(),
            serial = "G8T1-0000-$id", firmware = "10.62",
        )

    private val home = Homescreen(
        networks = listOf(BlinkNetwork(1, "Casa", armed = true, syncModuleStatus = "online"), BlinkNetwork(2, "Garage", armed = false, syncModuleStatus = null)),
        cameras = listOf(
            cam(11, "Ingresso", 3),
            cam(12, "Giardino", 42, battery = "low"),
            cam(21, "Salotto", 12, kind = DeviceKind.MINI, network = 2, battery = null, motion = false),
        ),
    )

    private val settings = AutoRefreshSettings(
        enabled = true,
        defaultIntervalMinutes = 30,
        windowEnabled = true,
        windowStartMinute = 7 * 60,
        windowEndMinute = 23 * 60,
        cameras = mapOf(
            "camera-11" to CameraRefreshConfig(intervalMinutes = 10),
            "mini-21" to CameraRefreshConfig(included = false),
        ),
    )

    /** Immagine finta che somiglia a una ripresa di telecamera. */
    private fun fakeThumbnail(key: String, top: Int, bottom: Int): File {
        val bmp = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, 360f, top, bottom, Shader.TileMode.CLAMP) })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255) }
        c.drawRect(80f, 150f, 260f, 330f, p)
        c.drawRect(380f, 110f, 560f, 330f, p)
        p.color = Color.WHITE
        p.textSize = 22f
        c.drawText("2026-11-08 11:02  $key", 16f, 32f, p)
        return File(outDir, "thumb-$key.jpg").apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
    }

    private fun capture(name: String) {
        repeat(10) {
            Thread.sleep(80)
            compose.waitForIdle()
        }
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    @Config(qualifiers = "w400dp-h1500dp-xhdpi")
    fun home() {
        val files = mapOf(
            "camera-11" to fakeThumbnail("camera-11", Color.rgb(70, 110, 160), Color.rgb(60, 90, 50)),
            "camera-12" to fakeThumbnail("camera-12", Color.rgb(120, 150, 190), Color.rgb(40, 80, 40)),
            "mini-21" to fakeThumbnail("mini-21", Color.rgb(150, 120, 90), Color.rgb(80, 60, 50)),
        )
        val state = HomeUiState(
            home = home,
            snapshots = setOf("camera-12"),
            thumbnailVersions = files.mapValues { 1L },
            settings = settings,
            attempts = mapOf("camera-11" to now.minus(Duration.ofMinutes(3))),
            lastUpdated = now.minusSeconds(60),
            cameraPrefs = com.daboprint3dtech.blink.data.prefs.CameraPreferences(favorites = setOf("camera-11")),
        )
        compose.setContent {
            BlinkTheme { HomeContent(state, SnackbarHostState(), { files.getValue(it) }, HomeActions(), now) }
        }
        capture("home")
    }

    @Test
    @Config(qualifiers = "w400dp-h2150dp-xhdpi")
    fun settings() {
        val state = SettingsUiState(
            settings = settings,
            cameras = home.cameras,
            lastRun = AutoRefreshRun(now.minus(Duration.ofMinutes(4)), listOf("Ingresso"), emptyList(), emptyList(), listOf("Giardino (batteria scarica)"), now.plus(Duration.ofMinutes(6))),
            nextRunAt = now.plus(Duration.ofMinutes(6)),
            email = "utente@example.com",
        )
        compose.setContent {
            BlinkTheme { SettingsContent(state, SnackbarHostState(), ignoringBattery = false, actions = SettingsActions(), now = now) }
        }
        capture("settings")
    }

    @Test
    fun login() {
        val actions = object : LoginActions {
            override fun onEmail(v: String) = Unit
            override fun onPassword(v: String) = Unit
            override fun onCode(v: String) = Unit
            override fun signIn() = Unit
            override fun verifyCode() = Unit
            override fun backToCredentials() = Unit
        }
        compose.setContent {
            BlinkTheme { LoginContent(LoginUiState(email = "utente@example.com", awaitingCode = true, code = "1234"), actions) }
        }
        capture("login-2fa")
    }

    @Test
    @Config(qualifiers = "w400dp-h1200dp-xhdpi")
    fun battery() {
        val cams = listOf(
            cam(12, "Giardino", 42, battery = "low").copy(batteryLevel = 1),
            cam(13, "Retro", 20).copy(batteryLevel = 2),
            cam(11, "Ingresso", 3).copy(batteryLevel = 3),
            cam(21, "Campanello", 5, kind = DeviceKind.DOORBELL, network = 2, battery = null).copy(batteryLevel = null),
        )
        val state = com.daboprint3dtech.blink.ui.battery.BatteryUiState(
            cameras = cams,
            prefs = com.daboprint3dtech.blink.data.prefs.CameraPreferences(batteryAlertEnabled = true, batteryAlertLevel = 1),
        )
        compose.setContent {
            BlinkTheme { com.daboprint3dtech.blink.ui.battery.BatteryContent(state, com.daboprint3dtech.blink.ui.battery.BatteryActions()) }
        }
        capture("battery")
    }

    @Test
    @Config(qualifiers = "w400dp-h1200dp-xhdpi")
    fun organize() {
        val cams = listOf(cam(11, "Ingresso", 3), cam(12, "Giardino", 42), cam(13, "Retro", 20), cam(21, "Salotto", 12, kind = DeviceKind.MINI, network = 2))
        val state = com.daboprint3dtech.blink.ui.cameras.OrganizeUiState(
            cameras = cams,
            prefs = com.daboprint3dtech.blink.data.prefs.CameraPreferences(
                order = listOf("camera-13", "camera-11", "camera-12", "mini-21"),
                favorites = setOf("camera-13"),
            ),
        )
        compose.setContent {
            BlinkTheme { com.daboprint3dtech.blink.ui.cameras.OrganizeContent(state, com.daboprint3dtech.blink.ui.cameras.OrganizeActions()) }
        }
        capture("organize")
    }
}
