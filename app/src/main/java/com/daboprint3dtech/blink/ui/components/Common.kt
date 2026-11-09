package com.daboprint3dtech.blink.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daboprint3dtech.blink.data.api.BlinkApiException
import com.daboprint3dtech.blink.data.api.BlinkAuthException
import com.daboprint3dtech.blink.data.api.BlinkBusyException
import com.daboprint3dtech.blink.data.api.BlinkLoginException
import com.daboprint3dtech.blink.data.settings.AutoRefreshSettings
import java.io.IOException

/** Messaggio d'errore comprensibile per l'utente. */
fun Throwable.userMessage(): String = when (this) {
    is BlinkAuthException -> "Sessione scaduta: effettua di nuovo l'accesso"
    is BlinkBusyException -> "Il sistema Blink è occupato, riprova tra qualche secondo"
    is BlinkApiException -> message
    is BlinkLoginException -> message ?: "Accesso non riuscito"
    is IOException -> "Connessione non disponibile o server Blink non raggiungibile"
    else -> message ?: "Errore imprevisto"
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Riga impostazione con titolo, descrizione e interruttore. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .selectable(checked, enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = textColor(enabled))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Riga impostazione cliccabile con valore a destra. */
@Composable
fun ValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = textColor(enabled))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun textColor(enabled: Boolean): Color =
    if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

/** Piccola etichetta colorata (es. "Armato", "Batteria scarica"). */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(color = color.copy(alpha = 0.85f), contentColor = Color.White, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Dialog di scelta dell'intervallo: valori predefiniti oppure personalizzato (in minuti).
 * Se [defaultMinutes] non è null compare l'opzione "Usa predefinito" (valore null).
 */
@Composable
fun IntervalDialog(
    title: String,
    current: Int?,
    defaultMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
) {
    val presets = AutoRefreshSettings.PRESET_INTERVALS
    val isCustomInitially = current != null && current !in presets
    var selected by rememberSaveable { mutableIntStateOf(if (isCustomInitially) CUSTOM else current ?: DEFAULT) }
    var custom by rememberSaveable { mutableStateOf(if (isCustomInitially) current.toString() else "") }
    val customValue = custom.toIntOrNull()
    val customValid = customValue != null &&
        customValue in AutoRefreshSettings.MIN_INTERVAL_MINUTES..AutoRefreshSettings.MAX_INTERVAL_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (defaultMinutes != null) {
                    item {
                        RadioRow(
                            "Usa predefinito (${AutoRefreshSettings.formatInterval(defaultMinutes)})",
                            selected == DEFAULT,
                        ) { selected = DEFAULT }
                    }
                }
                items(presets) { minutes ->
                    RadioRow("Ogni ${AutoRefreshSettings.formatInterval(minutes)}", selected == minutes) { selected = minutes }
                }
                item {
                    RadioRow("Personalizzato", selected == CUSTOM) { selected = CUSTOM }
                    if (selected == CUSTOM) {
                        OutlinedTextField(
                            value = custom,
                            onValueChange = { v -> custom = v.filter(Char::isDigit).take(5) },
                            label = { Text("Minuti") },
                            singleLine = true,
                            isError = custom.isNotEmpty() && !customValid,
                            supportingText = { Text("Da 1 a ${AutoRefreshSettings.MAX_INTERVAL_MINUTES} minuti") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != CUSTOM || customValid,
                onClick = {
                    onConfirm(
                        when (selected) {
                            DEFAULT -> null
                            CUSTOM -> customValue
                            else -> selected
                        },
                    )
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

private const val DEFAULT = -1
private const val CUSTOM = -2

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Selettore orario (minuti dalla mezzanotte). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOfDayDialog(title: String, minuteOfDay: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
fun ListDivider() = HorizontalDivider(Modifier.padding(start = 16.dp))

@Composable
fun rememberDialogState() = remember { mutableStateOf(false) }
