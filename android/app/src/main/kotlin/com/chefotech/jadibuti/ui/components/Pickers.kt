package com.chefotech.jadibuti.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chefotech.jadibuti.ui.format.formatDate
import com.chefotech.jadibuti.ui.format.formatTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(label: String, value: String?, onChange: (String?) -> Unit, allowClear: Boolean = false, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
                Text(value?.let(::formatDate) ?: "Choose date", style = MaterialTheme.typography.bodyLarge)
            }
            if (allowClear && value != null) TextButton(onClick = { onChange(null) }) { Text("Clear") }
        }
    }
    if (open) {
        val initial = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dose time") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TimeButton(time: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
        Text(formatTime(time), style = MaterialTheme.typography.titleMedium)
    }
    if (open) TimePickerDialog(time, onDismiss = { open = false }, onConfirm = { onChange(it); open = false })
}

/** Simple single-choice chip row with large targets. */
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            androidx.compose.material3.FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp)) },
            )
        }
    }
}

@Composable
fun <T> MultiChoiceChips(options: List<Pair<T, String>>, selected: Set<T>, onToggle: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            androidx.compose.material3.FilterChip(
                selected = value in selected,
                onClick = { onToggle(value) },
                label = { Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp)) },
            )
        }
    }
}

/** Dropdown-like selector rendered as a tappable field. */
@Composable
fun <T> SelectField(label: String, options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
            Text(options.firstOrNull { it.first == selected }?.second ?: "Choose…", style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(label) },
        text = {
            Column {
                options.forEach { (v, l) ->
                    Text(l, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { onSelect(v); open = false }.padding(vertical = 14.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
    )
}
