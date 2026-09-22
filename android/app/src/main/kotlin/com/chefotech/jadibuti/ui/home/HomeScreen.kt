package com.chefotech.jadibuti.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.chefotech.jadibuti.data.doseTimes
import com.chefotech.jadibuti.data.local.EventWithDetails
import com.chefotech.jadibuti.data.local.MedicineDao
import com.chefotech.jadibuti.data.local.MemberDao
import com.chefotech.jadibuti.data.local.MemberEntity
import com.chefotech.jadibuti.data.local.SyncStateDao
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.prefs.SettingsStore
import com.chefotech.jadibuti.data.repo.EventRepository
import com.chefotech.jadibuti.data.repo.InventoryRepository
import com.chefotech.jadibuti.data.repo.Outbox
import com.chefotech.jadibuti.domain.EventStatus
import com.chefotech.jadibuti.domain.MealSlot
import com.chefotech.jadibuti.domain.StatusPolicy
import com.chefotech.jadibuti.reminders.AlarmController
import com.chefotech.jadibuti.reminders.ReminderNotifier
import com.chefotech.jadibuti.reminders.ReminderScheduler
import com.chefotech.jadibuti.sync.SyncRepository
import com.chefotech.jadibuti.sync.SyncScheduler
import com.chefotech.jadibuti.ui.components.AppCard
import com.chefotech.jadibuti.ui.components.BigButton
import com.chefotech.jadibuti.ui.components.BigOutlinedButton
import com.chefotech.jadibuti.ui.components.BrandTopBar
import com.chefotech.jadibuti.ui.components.EmptyState
import com.chefotech.jadibuti.ui.components.IconLabel
import com.chefotech.jadibuti.ui.components.InfoBanner
import com.chefotech.jadibuti.ui.components.LabelledProgress
import com.chefotech.jadibuti.ui.components.MemberAvatar
import com.chefotech.jadibuti.ui.components.StatTile
import com.chefotech.jadibuti.ui.components.StatusChip
import com.chefotech.jadibuti.ui.components.Tone
import com.chefotech.jadibuti.ui.components.statusTone
import com.chefotech.jadibuti.ui.format.foodIcon
import com.chefotech.jadibuti.ui.format.foodLabel
import com.chefotech.jadibuti.ui.format.formatDose
import com.chefotech.jadibuti.ui.format.formatTime
import com.chefotech.jadibuti.ui.format.frequencyLabel
import com.chefotech.jadibuti.ui.format.mealIcon
import com.chefotech.jadibuti.ui.format.statusLabel
import com.chefotech.jadibuti.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** One dose on the dashboard, with its resolved display status and meal slot. */
data class DoseRow(val row: EventWithDetails, val displayStatus: EventStatus, val slot: MealSlot, val frequencyText: String)
data class MemberGroup(val memberId: String, val memberName: String, val doses: List<DoseRow>)
data class SlotGroup(val slot: MealSlot, val members: List<MemberGroup>) {
    val total get() = members.sumOf { it.doses.size }
    val taken get() = members.sumOf { m -> m.doses.count { it.displayStatus == EventStatus.TAKEN } }
}

/** Today's numbers for one family member, used by the chooser. */
data class MemberToday(val member: MemberEntity, val total: Int, val taken: Int, val due: Int, val missed: Int)

data class HomeState(
    val date: LocalDate = LocalDate.now(),
    val slots: List<SlotGroup> = emptyList(),
    val lowStockCount: Int = 0,
    val pendingSync: Int = 0,
    val lastSyncError: String? = null,
    val familyName: String = "",
    val members: List<MemberToday> = emptyList(),
    /** null = everyone. */
    val focusMemberId: String? = null,
    /** True when the family has several members and this process has not asked yet. */
    val needsChoice: Boolean = false,
    val loaded: Boolean = false,
) {
    val all get() = slots.flatMap { it.members }.flatMap { it.doses }
    val total get() = all.size
    val taken get() = all.count { it.displayStatus == EventStatus.TAKEN }
    val due get() = all.count { it.displayStatus == EventStatus.DUE || it.displayStatus == EventStatus.SNOOZED }
    val missed get() = all.count { it.displayStatus == EventStatus.MISSED }
    val upcoming get() = all.count { it.displayStatus == EventStatus.UPCOMING }
    val focusMember get() = members.firstOrNull { it.member.id == focusMemberId }?.member
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val events: EventRepository,
    private val medicines: MedicineDao,
    private val memberDao: MemberDao,
    private val session: SessionStore,
    private val settings: SettingsStore,
    private val inventory: InventoryRepository,
    private val outbox: Outbox,
    private val syncState: SyncStateDao,
    private val syncRepo: SyncRepository,
    private val syncScheduler: SyncScheduler,
    private val controller: AlarmController,
    private val focus: MemberFocus,
    val notifier: ReminderNotifier,
    val reminderScheduler: ReminderScheduler,
) : ViewModel() {
    private val tick = MutableStateFlow(System.currentTimeMillis())
    val message = MutableStateFlow<String?>(null)
    /** null = show every meal; otherwise only that slot. */
    val selectedSlot = MutableStateFlow<MealSlot?>(null)

    val state = session.snapshot.flatMapLatest { snap ->
        val familyId = snap.activeFamilyId ?: return@flatMapLatest flowOf(HomeState(loaded = true))
        val today = LocalDate.now().toString()
        combine(
            events.observeForDate(familyId, today), settings.settings, inventory.observeStock(familyId), outbox.observeCount(),
            syncState.observe(familyId), medicines.observe(familyId), tick, memberDao.observe(familyId), focus.selectedMemberId, focus.chosen,
        ) { arr ->
            @Suppress("UNCHECKED_CAST")
            val rows = arr[0] as List<EventWithDetails>
            val s = arr[1] as com.chefotech.jadibuti.data.prefs.ReminderSettings
            val stock = arr[2] as List<com.chefotech.jadibuti.data.repo.StockRow>
            val pending = arr[3] as Int
            val sync = arr[4] as com.chefotech.jadibuti.data.local.SyncStateEntity?
            val meds = (arr[5] as List<com.chefotech.jadibuti.data.local.MedicineEntity>).associateBy { it.id }
            val members = (arr[7] as List<MemberEntity>).filter { it.active }
            val chosen = arr[9] as Boolean
            val focusId = (arr[8] as String?)?.takeIf { id -> members.any { it.id == id } }

            val now = Instant.now()
            val missedAfter = Duration.ofMinutes(s.missedAfterMinutes.toLong())
            val doses = rows.map { r ->
                val stored = runCatching { EventStatus.valueOf(r.event.status) }.getOrDefault(EventStatus.UPCOMING)
                val med = meds[r.event.medicineId]
                val explicitMeal = med?.doseTimes()?.firstOrNull { it.time == r.event.time }?.meal
                DoseRow(
                    row = r,
                    displayStatus = StatusPolicy.resolve(stored, Instant.ofEpochMilli(r.event.scheduledAt), r.event.snoozedUntil?.let(Instant::ofEpochMilli), now, missedAfter),
                    slot = MealSlot.forDose(explicitMeal, LocalTime.parse(r.event.time)),
                    frequencyText = med?.let { frequencyLabel(it.frequency, it.weekdays.split(',').mapNotNull { d -> d.trim().toIntOrNull() }, it.intervalDays) } ?: "",
                )
            }
            val perMember = members.map { m ->
                val mine = doses.filter { it.row.event.memberId == m.id }
                MemberToday(m, mine.size, mine.count { it.displayStatus == EventStatus.TAKEN }, mine.count { it.displayStatus == EventStatus.DUE || it.displayStatus == EventStatus.SNOOZED }, mine.count { it.displayStatus == EventStatus.MISSED })
            }
            val visible = if (focusId != null) doses.filter { it.row.event.memberId == focusId } else doses
            val slots = MealSlot.entries.mapNotNull { slot ->
                val inSlot = visible.filter { it.slot == slot }
                if (inSlot.isEmpty()) null
                else SlotGroup(slot, inSlot.groupBy { it.row.event.memberId }.map { (id, list) -> MemberGroup(id, list.first().row.memberName, list.sortedBy { it.row.event.scheduledAt }) }.sortedBy { it.memberName })
            }
            val lowStock = stock.count { it.summary.lowStock && (focusId == null || it.medicine.memberId == focusId) }
            HomeState(
                LocalDate.now(), slots, lowStock, pending, sync?.lastError, snap.activeFamilyName ?: "",
                members = perMember, focusMemberId = focusId, needsChoice = members.size > 1 && !chosen, loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun choose(memberId: String?) = focus.choose(memberId)
    fun changeMember() = focus.askAgain()

    fun refresh() = viewModelScope.launch {
        tick.value = System.currentTimeMillis()
        when (val r = syncRepo.sync()) {
            is com.chefotech.jadibuti.sync.SyncOutcome.Offline -> message.value = r.message
            is com.chefotech.jadibuti.sync.SyncOutcome.Failed -> message.value = "Sync failed: ${r.message}"
            else -> {}
        }
    }

    fun taken(id: String) = viewModelScope.launch {
        val r = controller.taken(id)
        if (r?.insufficientStock == true) message.value = "Marked as taken. Stock was lower than the dose — please update the stock."
        tick.value = System.currentTimeMillis()
    }
    fun skip(id: String) = viewModelScope.launch { controller.skip(id); tick.value = System.currentTimeMillis() }
    fun snooze(id: String) = viewModelScope.launch { controller.snooze(id); tick.value = System.currentTimeMillis(); message.value = "Snoozed for ${settings.current().snoozeMinutes} minutes" }
    fun tick() { tick.value = System.currentTimeMillis() }
    fun onPermissionResult() { syncScheduler.requestMaintenance() }
}

@Composable
fun HomeScreen(nav: NavHostController, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val selected by vm.selectedSlot.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    var notificationsOk by remember { mutableStateOf(vm.notifier.notificationsEnabled()) }
    var exactOk by remember { mutableStateOf(vm.reminderScheduler.canScheduleExact()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsOk = vm.notifier.notificationsEnabled(); vm.onPermissionResult() }

    LaunchedEffect(Unit) {
        vm.refresh()
        if (Build.VERSION.SDK_INT >= 33 && !vm.notifier.hasPermission()) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        while (true) { kotlinx.coroutines.delay(30_000); vm.tick(); notificationsOk = vm.notifier.notificationsEnabled(); exactOk = vm.reminderScheduler.canScheduleExact() }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }

    if (state.needsChoice) {
        MemberChooser(state, onChoose = vm::choose)
        return
    }

    val visibleSlots = if (selected == null) state.slots else state.slots.filter { it.slot == selected }
    val subtitle = listOfNotNull(state.familyName.ifBlank { null }, state.date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM"))).joinToString(" · ")

    Scaffold(
        topBar = {
            BrandTopBar(subtitle = subtitle) {
                IconButton(onClick = vm::refresh, modifier = Modifier.width(52.dp)) { Icon(Icons.Default.Refresh, contentDescription = "Sync now", tint = Color.White) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.members.size > 1) item { ViewingRow(state, onChange = vm::changeMember) }
            item { TodaySummary(state) }
            if (!notificationsOk) item {
                InfoBanner("Reminders are OFF: notifications are not allowed. Reminders will not appear until you enable them.", Tone.RED, icon = Icons.Default.NotificationsOff) {
                    TextButton(onClick = { nav.navigate(Routes.PERMISSIONS_HELP) }) { Text("Fix") }
                }
            }
            if (notificationsOk && !exactOk) item {
                InfoBanner("Exact-time reminders are not allowed on this phone. Reminders may arrive a few minutes late.", Tone.AMBER, icon = Icons.Default.Schedule) {
                    TextButton(onClick = { nav.navigate(Routes.PERMISSIONS_HELP) }) { Text("Fix") }
                }
            }
            if (state.lowStockCount > 0) item {
                InfoBanner("${state.lowStockCount} medicine${if (state.lowStockCount > 1) "s are" else " is"} running low.", Tone.AMBER, icon = Icons.Default.Inventory) {
                    TextButton(onClick = { nav.navigate(Routes.INVENTORY) }) { Text("View stock") }
                }
            }
            if (state.pendingSync > 0) item {
                InfoBanner("${state.pendingSync} change${if (state.pendingSync > 1) "s" else ""} waiting to sync${state.lastSyncError?.let { " · $it" } ?: ""}.", Tone.BLUE, icon = Icons.Default.Sync) {
                    TextButton(onClick = vm::refresh) { Text("Sync") }
                }
            }
            if (state.total > 0) item { MealTabs(state, selected) { vm.selectedSlot.value = it } }
            if (state.loaded && state.total == 0) item {
                val who = state.focusMember?.name
                EmptyState(if (who != null) "Nothing scheduled for $who today" else "Nothing scheduled today", "Add a medicine and its schedule to start getting reminders.", icon = Icons.Default.Schedule)
                BigButton("Add a medicine", icon = Icons.Default.Add, onClick = { nav.navigate(Routes.medicineEdit(memberId = state.focusMemberId)) })
            }
            if (state.total > 0 && visibleSlots.isEmpty()) item { EmptyState("No ${selected?.label?.lowercase()} medicines today", "Nothing is scheduled for this meal. Tap another meal above.") }
            visibleSlots.forEach { slotGroup ->
                item(key = "slot-${slotGroup.slot}") { SlotHeader(slotGroup) }
                slotGroup.members.forEach { group ->
                    // When a single member is in focus their name is already in the header row.
                    if (state.focusMemberId == null) item(key = "m-${slotGroup.slot}-${group.memberId}") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            MemberAvatar(group.memberName, 36)
                            Spacer(Modifier.width(10.dp))
                            Text(group.memberName, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    items(group.doses, key = { "${slotGroup.slot}-${it.row.event.id}" }) { dose ->
                        DoseCard(dose, onTaken = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.taken(dose.row.event.id) }, onSkip = { vm.skip(dose.row.event.id) }, onSnooze = { vm.snooze(dose.row.event.id) })
                    }
                }
            }
        }
    }
}

/** Full-screen question shown on launch when the family has more than one member. */
@Composable
private fun MemberChooser(state: HomeState, onChoose: (String?) -> Unit) {
    Scaffold(topBar = { BrandTopBar(subtitle = state.familyName.ifBlank { null }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Whose medicines?", style = MaterialTheme.typography.headlineSmall)
            Text("Choose a family member to see only their medication plan. You can change this any time from the Home screen.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            state.members.forEach { m ->
                AppCard(modifier = Modifier.clickable { onChoose(m.member.id) }, padding = 14) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MemberAvatar(m.member.name, 56)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.member.name, style = MaterialTheme.typography.titleLarge)
                            val summary = when {
                                m.total == 0 -> "No doses today"
                                m.missed > 0 -> "${m.missed} missed · ${m.taken}/${m.total} taken"
                                m.due > 0 -> "${m.due} due now · ${m.taken}/${m.total} taken"
                                else -> "${m.taken}/${m.total} taken today"
                            }
                            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            BigOutlinedButton("Show everyone", icon = Icons.Default.Groups, onClick = { onChoose(null) })
        }
    }
}

@Composable
private fun ViewingRow(state: HomeState, onChange: () -> Unit) {
    val member = state.focusMember
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (member != null) MemberAvatar(member.name, 32) else Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Showing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(member?.name ?: "Everyone", style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            TextButton(onClick = onChange) { Text("Change", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun TodaySummary(state: HomeState) {
    AppCard(container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
        Column {
            val headline = when {
                state.total == 0 -> "No doses scheduled today"
                state.missed > 0 -> "${state.missed} dose${if (state.missed > 1) "s" else ""} missed today"
                state.due > 0 -> "${state.due} dose${if (state.due > 1) "s" else ""} due now"
                state.taken == state.total -> "All ${state.total} doses taken — well done!"
                else -> "${state.upcoming} dose${if (state.upcoming > 1) "s" else ""} coming up"
            }
            Text(headline, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            LabelledProgress("${state.taken} of ${state.total} taken", if (state.total == 0) 0f else state.taken.toFloat() / state.total)
            if (state.total > 0) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(state.due.toString(), "Due now", Tone.AMBER, Modifier.weight(1f))
                    StatTile(state.upcoming.toString(), "Upcoming", Tone.BLUE, Modifier.weight(1f))
                    StatTile(state.taken.toString(), "Taken", Tone.GREEN, Modifier.weight(1f))
                    StatTile(state.missed.toString(), "Missed", Tone.RED, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MealTabs(state: HomeState, selected: MealSlot?, onSelect: (MealSlot?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All (${state.total})", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
        MealSlot.entries.forEach { slot ->
            val g = state.slots.firstOrNull { it.slot == slot }
            val count = g?.total ?: 0
            FilterChip(
                selected = selected == slot,
                onClick = { onSelect(if (selected == slot) null else slot) },
                leadingIcon = { Icon(mealIcon(slot), contentDescription = null, modifier = Modifier.width(20.dp)) },
                label = { Text(if (count > 0) "${slot.label} ($count)" else slot.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary, selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary),
            )
        }
    }
}

@Composable
private fun SlotHeader(g: SlotGroup) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(mealIcon(g.slot), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(g.slot.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${g.taken}/${g.total} taken", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DoseCard(dose: DoseRow, onTaken: () -> Unit, onSkip: () -> Unit, onSnooze: () -> Unit) {
    val e = dose.row.event
    val status = dose.displayStatus
    val accent = when (status) {
        EventStatus.DUE, EventStatus.SNOOZED -> Tone.AMBER
        EventStatus.MISSED -> Tone.RED
        EventStatus.TAKEN -> Tone.GREEN
        EventStatus.SKIPPED -> Tone.GREY
        else -> Tone.BLUE
    }
    val (accentBg, accentFg) = com.chefotech.jadibuti.ui.components.toneColors(accent)
    AppCard(container = if (status == EventStatus.DUE || status == EventStatus.MISSED) accentBg.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface, padding = 0) {
        Row {
            Box(Modifier.width(6.dp).height(if (status.isTerminal) 120.dp else 210.dp).background(accentFg))
            Column(Modifier.padding(16.dp).weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(e.time), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    StatusChip(statusLabel(status.name), statusTone(status.name))
                }
                Spacer(Modifier.height(4.dp))
                Text(dose.row.medicineName + if (dose.row.medicineStrength.isNotBlank()) " ${dose.row.medicineStrength}" else "", style = MaterialTheme.typography.titleLarge)
                Text(formatDose(e.doseAmount, e.doseUnit), style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconLabel(foodIcon(dose.row.foodInstruction), foodLabel(dose.row.foodInstruction))
                    if (dose.frequencyText.isNotBlank()) IconLabel(Icons.Default.Schedule, dose.frequencyText)
                }
                if (status == EventStatus.TAKEN && e.actualAt != null) {
                    Text("Taken at ${formatTime(Instant.ofEpochMilli(e.actualAt).atZone(ZoneId.systemDefault()).toLocalTime())}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                }
                if (status == EventStatus.SNOOZED && e.snoozedUntil != null) {
                    Text("Reminder again at ${formatTime(Instant.ofEpochMilli(e.snoozedUntil).atZone(ZoneId.systemDefault()).toLocalTime())}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 4.dp))
                }
                if (!status.isTerminal) {
                    Spacer(Modifier.height(12.dp))
                    BigButton("MARK AS TAKEN", onClick = onTaken, icon = Icons.Default.Check)
                    if (status != EventStatus.UPCOMING) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = onSnooze) { Text("Snooze", style = MaterialTheme.typography.labelLarge) }
                            TextButton(onClick = onSkip) { Text("Skip", style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                } else if (status == EventStatus.SKIPPED) {
                    TextButton(onClick = onTaken) { Text("Actually taken", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}
