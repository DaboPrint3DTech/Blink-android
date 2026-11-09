package com.daboprint3dtech.blink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.daboprint3dtech.blink.data.auth.AuthState
import com.daboprint3dtech.blink.ui.battery.BatteryScreen
import com.daboprint3dtech.blink.ui.camera.CameraScreen
import com.daboprint3dtech.blink.ui.cameras.OrganizeScreen
import com.daboprint3dtech.blink.ui.clips.ClipsScreen
import com.daboprint3dtech.blink.ui.components.appContainer
import com.daboprint3dtech.blink.ui.home.HomeScreen
import com.daboprint3dtech.blink.ui.login.LoginScreen
import com.daboprint3dtech.blink.ui.player.PlayerScreen
import com.daboprint3dtech.blink.ui.settings.SettingsScreen

/** Destinazione aperta da un tap esterno (widget, notifica). */
sealed interface DeepLink {
    data class Camera(val key: String) : DeepLink
    data object Battery : DeepLink
}

private object Routes {
    const val HOME = "home"
    const val CAMERA = "camera/{key}"
    const val CLIPS = "clips"
    const val PLAYER = "player/{id}"
    const val SETTINGS = "settings"
    const val BATTERY = "battery"
    const val ORGANIZE = "organize"

    fun camera(key: String) = "camera/$key"
    fun player(id: Long) = "player/$id"
}

@Composable
fun BlinkAppRoot(deepLink: DeepLink? = null, onDeepLinkHandled: () -> Unit = {}) {
    val container = appContainer()
    val auth by container.auth.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (auth) {
            AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            AuthState.LoggedOut -> LoginScreen()
            is AuthState.LoggedIn -> MainNavHost(deepLink, onDeepLinkHandled)
        }
    }
}

@Composable
private fun MainNavHost(deepLink: DeepLink?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    androidx.compose.runtime.LaunchedEffect(deepLink) {
        when (val d = deepLink) {
            is DeepLink.Camera -> {
                nav.navigate(Routes.camera(d.key)) { launchSingleTop = true }
                onDeepLinkHandled()
            }
            DeepLink.Battery -> {
                nav.navigate(Routes.BATTERY) { launchSingleTop = true }
                onDeepLinkHandled()
            }
            null -> Unit
        }
    }
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenCamera = { nav.navigate(Routes.camera(it)) },
                onOpenClips = { nav.navigate(Routes.CLIPS) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenBattery = { nav.navigate(Routes.BATTERY) },
                onOpenOrganize = { nav.navigate(Routes.ORGANIZE) },
            )
        }
        composable(Routes.CAMERA, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
            CameraScreen(
                cameraKey = entry.arguments?.getString("key").orEmpty(),
                onBack = { nav.popBackStack() },
                onPlayClip = { nav.navigate(Routes.player(it)) },
            )
        }
        composable(Routes.CLIPS) {
            ClipsScreen(onBack = { nav.popBackStack() }, onPlayClip = { nav.navigate(Routes.player(it)) })
        }
        composable(Routes.PLAYER, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            PlayerScreen(clipId = entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.BATTERY) {
            BatteryScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.ORGANIZE) {
            OrganizeScreen(onBack = { nav.popBackStack() })
        }
    }
}
