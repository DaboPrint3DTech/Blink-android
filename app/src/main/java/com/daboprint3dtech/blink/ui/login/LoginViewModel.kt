package com.daboprint3dtech.blink.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daboprint3dtech.blink.AppContainer
import com.daboprint3dtech.blink.data.api.PendingTwoFactor
import com.daboprint3dtech.blink.data.auth.LoginStep
import com.daboprint3dtech.blink.ui.components.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val awaitingCode: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val container: AppContainer) : ViewModel(), LoginActions {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()
    private var pending: PendingTwoFactor? = null

    init {
        viewModelScope.launch {
            container.auth.lastEmail()?.let { email -> _state.update { if (it.email.isEmpty()) it.copy(email = email) else it } }
        }
    }

    override fun onEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    override fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    override fun onCode(v: String) = _state.update { it.copy(code = v.filter(Char::isDigit).take(8), error = null) }

    override fun signIn() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isEmpty() || s.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                when (val step = container.auth.login(s.email, s.password)) {
                    LoginStep.Done -> onLoggedIn()
                    is LoginStep.NeedsTwoFactor -> {
                        pending = step.pending
                        _state.update { it.copy(awaitingCode = true, loading = false, password = "") }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    override fun verifyCode() {
        val p = pending ?: return
        val code = _state.value.code
        if (code.length < 4 || _state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                container.auth.verifyTwoFactor(p, code)
                onLoggedIn()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    override fun backToCredentials() {
        pending = null
        _state.update { it.copy(awaitingCode = false, code = "", error = null, loading = false) }
    }

    private suspend fun onLoggedIn() {
        pending = null
        _state.update { it.copy(loading = false, password = "", code = "", awaitingCode = false) }
        val settings = container.autoRefreshStore.current()
        if (settings.enabled) container.refreshScheduler.ensureScheduled(settings.unmeteredOnly)
    }
}
