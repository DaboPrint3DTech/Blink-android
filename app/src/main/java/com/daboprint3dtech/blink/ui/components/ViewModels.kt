package com.daboprint3dtech.blink.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daboprint3dtech.blink.AppContainer
import com.daboprint3dtech.blink.BlinkApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as BlinkApp).container

/** Crea un ViewModel passandogli il contenitore delle dipendenze. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
