package com.daboprint3dtech.blink.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import com.daboprint3dtech.blink.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daboprint3dtech.blink.ui.components.DABOPRINT3D_YOUTUBE_URL
import com.daboprint3dtech.blink.ui.components.appViewModel
import com.daboprint3dtech.blink.ui.components.openUrl
import androidx.compose.ui.platform.LocalContext

/** Azioni della schermata di login. */
interface LoginActions {
    fun onEmail(v: String)
    fun onPassword(v: String)
    fun onCode(v: String)
    fun signIn()
    fun verifyCode()
    fun backToCredentials()
}

@Composable
fun LoginScreen() {
    val vm = appViewModel { LoginViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    LoginContent(state, vm)
}

@Composable
fun LoginContent(state: LoginUiState, vm: LoginActions) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(32.dp))
            AppLogo(Modifier.size(112.dp))
            Spacer(Modifier.height(12.dp))
            Text("Blink Cam", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Accedi con il tuo account Blink",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                if (!state.awaitingCode) CredentialsForm(state, vm) else CodeForm(state, vm)

                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    "App non ufficiale: usa le stesse API dell'app Blink. Le credenziali vengono inviate solo " +
                        "ai server Blink e la password non viene salvata sul telefono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                val context = LocalContext.current
                TextButton(
                    onClick = { openUrl(context, DABOPRINT3D_YOUTUBE_URL) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Un progetto DaboPrint3D · canale YouTube", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Logo dell'app: le stesse due immagini dell'icona adattiva, ritagliate a cerchio. */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape), contentAlignment = Alignment.Center) {
        // L'icona adattiva è 108x108 con la parte visibile nei 72 centrali: si ingrandisce di 1,5x.
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize().scale(1.5f))
        Image(painterResource(R.drawable.ic_launcher_foreground), "Blink Cam", Modifier.fillMaxSize().scale(1.5f))
    }
}

@Composable
private fun CredentialsForm(state: LoginUiState, vm: LoginActions) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = state.email,
        onValueChange = vm::onEmail,
        label = { Text("Email") },
        singleLine = true,
        enabled = !state.loading,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.password,
        onValueChange = vm::onPassword,
        label = { Text("Password") },
        singleLine = true,
        enabled = !state.loading,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                    if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (showPassword) "Nascondi password" else "Mostra password",
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.signIn() }),
        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = vm::signIn,
        enabled = !state.loading && state.email.isNotBlank() && state.password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Accedi")
    }
}

@Composable
private fun CodeForm(state: LoginUiState, vm: LoginActions) {
    Text("Verifica in due passaggi", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Blink ha inviato un codice via SMS o email a ${state.email}. Inseriscilo qui sotto.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.code,
        onValueChange = vm::onCode,
        label = { Text("Codice di verifica") },
        singleLine = true,
        enabled = !state.loading,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.verifyCode() }),
        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.SmsOtpCode },
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = vm::verifyCode,
        enabled = !state.loading && state.code.length >= 4,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Verifica")
    }
    TextButton(onClick = vm::backToCredentials, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
        Text("Torna indietro")
    }
}
