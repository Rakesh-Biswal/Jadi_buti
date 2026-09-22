package com.chefotech.jadibuti.ui.history

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.chefotech.jadibuti.data.local.EventWithDetails
import com.chefotech.jadibuti.data.local.MedicineEntity
import com.chefotech.jadibuti.data.local.MemberEntity
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.repo.EventRepository
import com.chefotech.jadibuti.data.repo.MedicineRepository
import com.chefotech.jadibuti.data.repo.MemberRepository
import com.chefotech.jadibuti.ui.components.AppCard
import com.chefotech.jadibuti.ui.components.AppTopBar
import com.chefotech.jadibuti.ui.components.ChoiceChips
import com.chefotech.jadibuti.ui.components.EmptyState
import com.chefotech.jadibuti.ui.components.LabelledProgress
import com.chefotech.jadibuti.ui.components.MemberAvatar
import com.chefotech.jadibuti.ui.components.SectionHeader
import com.chefotech.jadibuti.ui.components.SelectField
import com.chefotech.jadibuti.ui.components.StatTile
import com.chefotech.jadibuti.ui.components.StatusChip
import com.chefotech.jadibuti.ui.components.Tone
import com.chefotech.jadibuti.ui.components.statusTone
import com.chefotech.jadibuti.ui.format.formatDateHeading
import com.chefotech.jadibuti.ui.format.formatDose
import com.chefotech.jadibuti.ui.format.formatTime
import com.chefotech.jadibuti.ui.format.statusLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HistoryFilter(val days: Int = 7, val memberId: String? = null, val medicineId: String? = null, val status: String? = null)
data class DayGroup(val date: LocalDate, val rows: List<EventWithDetails>)
data class HistoryState(val days: List<DayGroup> = emptyList(), val members: List<MemberEntity> = emptyList(), val medicines: List<MedicineEntity> = emptyList(), val scheduled: Int = 0, val taken: Int = 0, val missed: Int = 0, val skipped: Int = 0)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(events: EventRepository, members: MemberRepository, medicines: MedicineRepository, session: SessionStore) : ViewModel() {
    val filter = MutableStateFlow(HistoryFilter())
    val state = combine(session.snapshot, filter) { s, f -> s.activeFamilyId to f }.flatMapLatest { (familyId, f) ->
        if (familyId == null) return@flatMapLatest flowOf(HistoryState())
        val to = LocalDate.now()
        val from = to.minusDays((f.days - 1).toLong())
        combine(events.observeRange(familyId, from.toString(), to.toString(), f.memberId, f.medicineId), members.observe(familyId), medicines.observe(familyId)) { rows, mems, meds ->
            val now = System.currentTimeMillis()
            val past = rows.filter { it.event.scheduledAt <= now || it.event.status in setOf("TAKEN", "SKIPPED", "MISSED") }
            val shown = if (f.status == null) past else past.filter { it.event.status == f.status }
            val days = shown.groupBy { LocalDate.parse(it.event.localDate) }.entries.sortedByDescending { it.key }.map { DayGroup(it.key, it.value) }
            HistoryState(days, mems, meds, past.size, past.count { it.event.status == "TAKEN" }, past.count { it.event.status == "MISSED" }, past.count { it.event.status == "SKIPPED" })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())
}

@Composable
fun HistoryScreen(nav: NavHostController, vm: HistoryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val filter by vm.filter.collectAsState()
    Scaffold(topBar = { AppTopBar("History", subtitle = "Last ${filter.days} day${if (filter.days > 1) "s" else ""}") }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                ChoiceChips(listOf(1 to "Today", 7 to "7 days", 30 to "30 days"), filter.days) { d -> vm.filter.value = filter.copy(days = d) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectField("Member", listOf<Pair<String?, String>>(null to "All members") + state.members.map { it.id to it.name }, filter.memberId, { vm.filter.value = filter.copy(memberId = it, medicineId = null) }, Modifier.weight(1f))
                    SelectField("Medicine", listOf<Pair<String?, String>>(null to "All medicines") + state.medicines.filter { filter.memberId == null || it.memberId == filter.memberId }.map { it.id to it.name }, filter.medicineId, { vm.filter.value = filter.copy(medicineId = it) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                ChoiceChips(listOf<Pair<String?, String>>(null to "All", "TAKEN" to "Taken", "MISSED" to "Missed", "SKIPPED" to "Skipped"), filter.status) { s -> vm.filter.value = filter.copy(status = s) }
            }
            item {
                AppCard(container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
                    Column {
                        val pct = if (state.scheduled == 0) 0f else state.taken.toFloat() / state.scheduled
                        Text(if (state.scheduled == 0) "No doses in this period" else "${(pct * 100).toInt()}% of doses taken", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        LabelledProgress("${state.taken} of ${state.scheduled} scheduled", pct)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(state.taken.toString(), "Taken", Tone.GREEN, Modifier.weight(1f))
                            StatTile(state.missed.toString(), "Missed", Tone.RED, Modifier.weight(1f))
                            StatTile(state.skipped.toString(), "Skipped", Tone.GREY, Modifier.weight(1f))
                        }
                    }
                }
            }
            if (state.days.isEmpty()) item { EmptyState("No records", "Doses you mark as taken, skipped or that were missed will appear here.", icon = Icons.Default.History) }
            state.days.forEach { day ->
                item(key = "d-${day.date}") { SectionHeader(formatDateHeading(day.date)) }
                items(day.rows, key = { it.event.id }) { r ->
                    val e = r.event
                    AppCard(padding = 14) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MemberAvatar(r.memberName, 40)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${r.memberName} · ${r.medicineName}", style = MaterialTheme.typography.titleMedium)
                                Text("${formatTime(e.time)} · ${formatDose(e.doseAmount, e.doseUnit)}", style = MaterialTheme.typography.bodyLarge)
                                if (e.status == "TAKEN" && e.actualAt != null) Text("Taken at ${formatTime(Instant.ofEpochMilli(e.actualAt).atZone(ZoneId.systemDefault()).toLocalTime())}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusChip(statusLabel(e.status), statusTone(e.status))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
