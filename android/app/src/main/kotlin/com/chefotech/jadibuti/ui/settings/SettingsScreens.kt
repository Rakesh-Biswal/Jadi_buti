package com.chefotech.jadibuti.ui.settings

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.chefotech.jadibuti.BuildConfig
import com.chefotech.jadibuti.R
import com.chefotech.jadibuti.data.prefs.AlarmTone
import com.chefotech.jadibuti.data.prefs.ReminderSettings
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.prefs.SettingsStore
import com.chefotech.jadibuti.data.prefs.ThemeMode
import com.chefotech.jadibuti.data.prefs.VibrationPattern
import com.chefotech.jadibuti.data.repo.AuthRepository
import com.chefotech.jadibuti.reminders.AlarmPlayer
import com.chefotech.jadibuti.reminders.ReminderNotifier
import com.chefotech.jadibuti.reminders.ReminderScheduler
import com.chefotech.jadibuti.ui.auth.ServerDialog
import com.chefotech.jadibuti.ui.auth.stringRes
import com.chefotech.jadibuti.ui.components.AppCard
import com.chefotech.jadibuti.ui.components.AppTopBar
import com.chefotech.jadibuti.ui.components.BigButton
import com.chefotech.jadibuti.ui.components.BigOutlinedButton
import com.chefotech.jadibuti.ui.components.ChoiceChips
import com.chefotech.jadibuti.ui.components.IconBadge
import com.chefotech.jadibuti.ui.components.MemberAvatar
import com.chefotech.jadibuti.ui.components.SectionHeader
import com.chefotech.jadibuti.ui.components.StatusChip
import com.chefotech.jadibuti.ui.components.Tone
import com.chefotech.jadibuti.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val session: SessionStore,
    private val settingsStore: SettingsStore,
    private val auth: AuthRepository,
    val notifier: ReminderNotifier,
    val scheduler: ReminderScheduler,
    val player: AlarmPlayer,
) : ViewModel() {
    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettings())
    val snapshot = session.snapshot

    fun update(transform: (ReminderSettings) -> ReminderSettings) = viewModelScope.launch {
        settingsStore.update(transform)
        notifier.ensureChannels(settingsStore.current())
        scheduler.rescheduleAll()
    }

    fun preview(tone: AlarmTone) = viewModelScope.launch { player.preview(settingsStore.current().copy(tone = tone)) }
    fun stopPreview() = player.stop()
    fun testVibration(p: VibrationPattern) = player.vibrate(p, repeat = false)
    fun logout() = viewModelScope.launch { auth.logout() }
    fun switchFamily() { session.setActiveFamily(null, null, null, true) }
    fun setServer(url: String) = session.setApiBaseUrl(url)
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    AppCard(modifier = Modifier.clickable(onClick = onClick), padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsState()
    val s by vm.settings.collectAsState()
    var serverDialog by remember { mutableStateOf(false) }
    val notifOk = vm.notifier.notificationsEnabled()
    val exactOk = vm.scheduler.canScheduleExact()
    Scaffold(topBar = { AppTopBar("Settings", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppCard(container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemberAvatar(snap.userName ?: "?", 56)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(snap.userName ?: "", style = MaterialTheme.typography.titleLarge)
                        Text(snap.userEmail ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${snap.activeFamilyName ?: "—"} · ${if (snap.isOwner) "Owner" else "Caregiver"}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            SectionHeader("Appearance", icon = Icons.Default.Palette)
            AppCard {
                Column {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    ChoiceChips(ThemeMode.entries.map { it to it.label }, s.themeMode) { m -> vm.update { it.copy(themeMode = m) } }
                }
            }
            SectionHeader("Reminders", icon = Icons.Default.Alarm)
            SettingsRow(Icons.Default.MusicNote, "Alarm sound & vibration", "${s.tone.label} · Vibration ${if (s.vibrationEnabled) "on" else "off"} · Snooze ${s.snoozeMinutes} min") { nav.navigate(Routes.REMINDER_SETTINGS) }
            SettingsRow(Icons.AutoMirrored.Filled.HelpOutline, "Permissions & battery", if (notifOk && exactOk) "Everything is allowed" else "Action needed for reliable reminders") { nav.navigate(Routes.PERMISSIONS_HELP) }
            SectionHeader("Family", icon = Icons.Default.Groups)
            SettingsRow(Icons.Default.Groups, "Caregivers & sharing", "Invite family, manage access") { nav.navigate(Routes.CAREGIVERS) }
            SettingsRow(Icons.Default.SwapHoriz, "Switch family", "Choose another family you belong to") { vm.switchFamily() }
            SectionHeader("Account", icon = Icons.Default.AccountCircle)
            SettingsRow(Icons.Default.Info, "Server address", vm.session.apiBaseUrl) { serverDialog = true }
            Spacer(Modifier.height(4.dp))
            BigButton("Sign out", icon = Icons.Default.Logout, color = MaterialTheme.colorScheme.error, onClick = vm::logout)
            SectionHeader("About", icon = Icons.Default.Info)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Jadi-Buti ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                com.chefotech.jadibuti.ui.components.BrandBadge(onHeader = false)
            }
            Text(stringRes(R.string.safety_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Reminders work offline. Your family's data syncs automatically when the phone is online.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (serverDialog) ServerDialog(vm.session.apiBaseUrl, onDismiss = { serverDialog = false }, onSave = { vm.setServer(it); serverDialog = false })
}

@Composable
fun ReminderSettingsScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsState()
    var playing by remember { mutableStateOf<AlarmTone?>(null) }
    DisposableEffect(Unit) { onDispose { vm.stopPreview() } }
    val pickTone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        @Suppress("DEPRECATION")
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) vm.update { it.copy(tone = AlarmTone.CUSTOM, customToneUri = uri.toString()) }
    }
    Scaffold(topBar = { AppTopBar("Alarm sound & vibration", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("Medication alarm sound", icon = Icons.Default.MusicNote)
            AppCard {
                Column {
                    Text("The alarm keeps ringing until you respond or the ring time ends. It uses the phone's alarm volume.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    AlarmTone.entries.forEach { tone ->
                        Row(Modifier.fillMaxWidth().clickable {
                            if (tone == AlarmTone.CUSTOM) pickTone.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM).putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Medication alarm sound"))
                            else vm.update { it.copy(tone = tone) }
                        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = s.tone == tone, onClick = null)
                            Text(tone.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (tone != AlarmTone.CUSTOM || s.customToneUri != null) IconButton(onClick = {
                                if (playing == tone) { vm.stopPreview(); playing = null } else { vm.preview(tone); playing = tone }
                            }) { Icon(if (playing == tone) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = "Preview") }
                        }
                    }
                }
            }
            Text("Ring for", style = MaterialTheme.typography.bodyLarge)
            ChoiceChips(listOf(30 to "30 sec", 60 to "1 min", 120 to "2 min", 300 to "5 min"), s.ringSeconds) { v -> vm.update { it.copy(ringSeconds = v) } }

            SectionHeader("Vibration", icon = Icons.Default.Vibration)
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vibrate with the alarm", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(s.vibrationEnabled, { v -> vm.update { it.copy(vibrationEnabled = v) } })
                    }
                    if (s.vibrationEnabled) {
                        Text("Reminder pattern", style = MaterialTheme.typography.bodyMedium)
                        ChoiceChips(VibrationPattern.entries.filter { it != VibrationPattern.OFF }.map { it to it.label }, s.vibration) { p -> vm.update { it.copy(vibration = p) }; vm.testVibration(p) }
                        Text("Overdue follow-up pattern", style = MaterialTheme.typography.bodyMedium)
                        ChoiceChips(VibrationPattern.entries.filter { it != VibrationPattern.OFF }.map { it to it.label }, s.overdueVibration) { p -> vm.update { it.copy(overdueVibration = p) }; vm.testVibration(p) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vibrate when I tap Taken", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(s.hapticsEnabled, { v -> vm.update { it.copy(hapticsEnabled = v) } })
                    }
                }
            }

            SectionHeader("Timing", icon = Icons.Default.Schedule)
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Snooze length", style = MaterialTheme.typography.bodyLarge)
                    ChoiceChips(listOf(5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min"), s.snoozeMinutes) { m -> vm.update { it.copy(snoozeMinutes = m) } }
                    Text("Follow-up alarm if not acknowledged", style = MaterialTheme.typography.bodyLarge)
                    ChoiceChips(listOf(0 to "Off", 5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min"), s.followUpMinutes) { m -> vm.update { it.copy(followUpMinutes = m) } }
                    Text("Mark as missed after", style = MaterialTheme.typography.bodyLarge)
                    ChoiceChips(listOf(30 to "30 min", 60 to "1 hour", 120 to "2 hours", 240 to "4 hours"), s.missedAfterMinutes) { m -> vm.update { it.copy(missedAfterMinutes = m) } }
                }
            }
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Alert me when a family member misses a dose", style = MaterialTheme.typography.bodyLarge)
                        Text("Shown on this phone after the family's data syncs.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(s.caregiverAlerts, { v -> vm.update { it.copy(caregiverAlerts = v) } })
                }
            }
            Text("Alarms respect Do Not Disturb unless you allow alarms in its settings. Silent mode does not silence alarms, just like a clock alarm.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun PermissionsHelpScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val notifOk = remember(refresh) { vm.notifier.notificationsEnabled() }
    val exactOk = remember(refresh) { vm.scheduler.canScheduleExact() }
    val fullScreenOk = remember(refresh) { vm.notifier.canUseFullScreenIntent() }
    val batteryOk = remember(refresh) { (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Scaffold(topBar = { AppTopBar("Permissions & battery", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("For alarms to work reliably, Android needs these settings. Jadi-Buti never pretends a reminder is on when it cannot be shown.", style = MaterialTheme.typography.bodyLarge)
            HelpCard("Notifications", if (notifOk) "Allowed" else "Blocked — reminders will NOT appear", notifOk, "Reminders and alarms are shown as notifications.") {
                if (Build.VERSION.SDK_INT >= 33 && !vm.notifier.hasPermission()) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else launcher.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            HelpCard("Alarms & reminders (exact timing)", if (exactOk) "Allowed" else "Not allowed — reminders may be late", exactOk, "Lets alarms fire at the exact minute even when the phone is asleep.") {
                if (Build.VERSION.SDK_INT >= 31) launcher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}")))
            }
            HelpCard("Full-screen alarm", if (fullScreenOk) "Allowed" else "Not allowed — alarm shows as a notification only", fullScreenOk, "Shows the big TAKEN / SNOOZE / SKIP screen over the lock screen when medicine is due.") {
                if (Build.VERSION.SDK_INT >= 34) launcher.launch(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(Uri.parse("package:${context.packageName}")))
            }
            HelpCard("Battery optimisation", if (batteryOk) "Unrestricted" else "Restricted — some phones delay or block alarms", batteryOk, "Some phones (especially with battery saver) stop background apps. Setting Jadi-Buti to 'Unrestricted' keeps alarms reliable.") {
                launcher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:${context.packageName}")))
            }
            AppCard {
                Column {
                    Text("Tips", style = MaterialTheme.typography.titleMedium)
                    Text("• Do not force-stop the app from Settings; that cancels scheduled alarms until it is opened again.\n• After a restart, alarms are restored automatically.\n• Alarms work without internet. Changes sync when you are back online.\n• Alarms use the phone's alarm volume, so keep it turned up.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = { refresh++ }) { Text("Re-check") }
        }
    }
}

@Composable
private fun HelpCard(title: String, status: String, ok: Boolean, body: String, onFix: () -> Unit) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); StatusChip(if (ok) "OK" else "Action needed", if (ok) Tone.GREEN else Tone.RED) }
            Text(status, style = MaterialTheme.typography.bodyLarge, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!ok) BigOutlinedButton("Open settings", onClick = onFix)
        }
    }
}
