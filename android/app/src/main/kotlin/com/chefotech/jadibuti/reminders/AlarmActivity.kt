package com.chefotech.jadibuti.reminders

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chefotech.jadibuti.data.local.EventDao
import com.chefotech.jadibuti.data.local.EventWithDetails
import com.chefotech.jadibuti.data.prefs.SettingsStore
import com.chefotech.jadibuti.ui.components.BigButton
import com.chefotech.jadibuti.ui.components.ChoiceChips
import com.chefotech.jadibuti.ui.format.foodLabel
import com.chefotech.jadibuti.ui.format.formatDose
import com.chefotech.jadibuti.ui.format.formatTime
import com.chefotech.jadibuti.ui.theme.GreenDark
import com.chefotech.jadibuti.ui.theme.JadiButiTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class AlarmViewModel @Inject constructor(private val events: EventDao, private val controller: AlarmController, private val settings: SettingsStore) : ViewModel() {
    val rows = MutableStateFlow<List<EventWithDetails>>(emptyList())
    val snoozeMinutes = MutableStateFlow(10)
    val loaded = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun load(ids: List<String>) = viewModelScope.launch {
        snoozeMinutes.value = settings.current().snoozeMinutes
        rows.value = ids.mapNotNull { events.getWithDetails(it) }.filter { !it.event.deleted && it.event.status !in setOf("TAKEN", "SKIPPED") }
        loaded.value = true
    }

    private fun remove(id: String) { rows.value = rows.value.filter { it.event.id != id } }

    fun taken(id: String) = viewModelScope.launch {
        val r = controller.taken(id)
        if (r?.insufficientStock == true) message.value = "Marked as taken. Stock was lower than the dose — please update the stock."
        remove(id)
    }
    fun snooze(id: String) = viewModelScope.launch { controller.snooze(id, snoozeMinutes.value); remove(id) }
    fun skip(id: String) = viewModelScope.launch { controller.skip(id); remove(id) }
    fun stopSound() = controller.stopSound()
}

/**
 * Full-screen medication alarm, shown over the lock screen when a dose is due. Each dose has
 * its own TAKEN / SNOOZE / SKIP so several medicines due together are handled individually.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val ids = intent.getStringArrayListExtra(EXTRA_EVENT_IDS).orEmpty()
        setContent {
            JadiButiTheme {
                val vm: AlarmViewModel = viewModel()
                LaunchedEffect(ids) { vm.load(ids) }
                AlarmScreen(vm, onDone = { finishAndRemoveTask() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object { const val EXTRA_EVENT_IDS = "eventIds" }
}

@Composable
private fun AlarmScreen(vm: AlarmViewModel, onDone: () -> Unit) {
    val rows by vm.rows.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val snooze by vm.snoozeMinutes.collectAsState()
    val message by vm.message.collectAsState()
    val haptics = LocalHapticFeedback.current
    var showSnoozeFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rows, loaded) { if (loaded && rows.isEmpty()) { vm.stopSound(); onDone() } }

    Surface(Modifier.fillMaxSize(), color = GreenDark) {
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(GreenDark, Color(0xFF0F3D24)))).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Text(formatTime(LocalTime.now()), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
            Text("Time to take medicine", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            rows.forEach { row ->
                val e = row.event
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(row.memberName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(row.medicineName + if (row.medicineStrength.isNotBlank()) " ${row.medicineStrength}" else "", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF1D2A1F))
                        Text("Dose: ${formatDose(e.doseAmount, e.doseUnit)}", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1D2A1F))
                        Text("${foodLabel(row.foodInstruction)} · Scheduled ${formatTime(e.time)}", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF4B5563))
                        Spacer(Modifier.height(14.dp))
                        BigButton("TAKEN", icon = Icons.Default.Check, onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.taken(e.id) })
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { showSnoozeFor = e.id }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("SNOOZE", style = MaterialTheme.typography.labelLarge) }
                            OutlinedButton(onClick = { vm.skip(e.id) }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("SKIP", style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
            if (loaded && rows.isEmpty()) Text("All done", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = vm::stopSound) {
                Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = Color.White)
                Spacer(Modifier.height(0.dp))
                Text("  Stop sound, decide later", color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
            message?.let { Text(it, color = Color(0xFFFFE082), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(24.dp))
        }
    }

    showSnoozeFor?.let { id ->
        AlertDialog(
            onDismissRequest = { showSnoozeFor = null },
            title = { Text("Remind me again in") },
            text = { ChoiceChips(listOf(5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min"), snooze) { vm.snoozeMinutes.value = it } },
            confirmButton = { TextButton(onClick = { vm.snooze(id); showSnoozeFor = null }) { Text("Snooze", style = MaterialTheme.typography.labelLarge) } },
            dismissButton = { TextButton(onClick = { showSnoozeFor = null }) { Text("Cancel") } },
        )
    }
}
