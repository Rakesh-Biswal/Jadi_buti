package com.chefotech.jadibuti.ui.family

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.chefotech.jadibuti.data.local.MemberEntity
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.repo.EventRepository
import com.chefotech.jadibuti.data.repo.InventoryRepository
import com.chefotech.jadibuti.data.repo.MemberRepository
import com.chefotech.jadibuti.data.repo.MedicineRepository
import com.chefotech.jadibuti.ui.components.AppCard
import com.chefotech.jadibuti.ui.components.AppTopBar
import com.chefotech.jadibuti.ui.components.BigOutlinedButton
import com.chefotech.jadibuti.ui.components.EmptyState
import com.chefotech.jadibuti.ui.components.LabelledProgress
import com.chefotech.jadibuti.ui.components.MemberAvatar
import com.chefotech.jadibuti.ui.components.StatusChip
import com.chefotech.jadibuti.ui.components.Tone
import com.chefotech.jadibuti.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class MemberOverview(val member: MemberEntity, val total: Int, val taken: Int, val missed: Int, val pending: Int, val lowStock: Int, val medicineCount: Int)
data class FamilyState(val familyName: String = "", val members: List<MemberOverview> = emptyList(), val canEdit: Boolean = true, val isOwner: Boolean = false, val loaded: Boolean = false)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class FamilyViewModel @Inject constructor(session: SessionStore, members: MemberRepository, events: EventRepository, inventory: InventoryRepository, medicines: MedicineRepository) : ViewModel() {
    val state = session.snapshot.flatMapLatest { snap ->
        val familyId = snap.activeFamilyId ?: return@flatMapLatest flowOf(FamilyState(loaded = true))
        val today = LocalDate.now().toString()
        combine(members.observe(familyId), events.observeForDate(familyId, today), inventory.observeStock(familyId), medicines.observe(familyId)) { mems, evs, stock, meds ->
            val now = System.currentTimeMillis()
            FamilyState(
                familyName = snap.activeFamilyName ?: "",
                members = mems.map { m ->
                    val mine = evs.filter { it.event.memberId == m.id }
                    MemberOverview(
                        member = m, total = mine.size,
                        taken = mine.count { it.event.status == "TAKEN" },
                        missed = mine.count { it.event.status == "MISSED" },
                        pending = mine.count { it.event.status !in setOf("TAKEN", "MISSED", "SKIPPED") && it.event.scheduledAt <= now },
                        lowStock = stock.count { it.medicine.memberId == m.id && it.summary.lowStock },
                        medicineCount = meds.count { it.memberId == m.id && it.active },
                    )
                },
                canEdit = snap.canEdit, isOwner = snap.isOwner, loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FamilyState())
}

@Composable
fun FamilyScreen(nav: NavHostController, vm: FamilyViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    Scaffold(
        topBar = {
            AppTopBar(state.familyName.ifBlank { "Family" }, subtitle = "${state.members.size} member${if (state.members.size == 1) "" else "s"}") {
                IconButton(onClick = { nav.navigate(Routes.CAREGIVERS) }, modifier = Modifier.width(52.dp)) { Icon(Icons.Default.Groups, contentDescription = "Caregivers", tint = Color.White) }
            }
        },
        floatingActionButton = { if (state.canEdit) ExtendedFloatingActionButton(onClick = { nav.navigate(Routes.memberEdit()) }, icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) }, text = { Text("Add member", style = MaterialTheme.typography.labelLarge) }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loaded && state.members.isEmpty()) item {
                EmptyState("No family members yet", "Add the people whose medicines you want to manage, for example Father, Mother or Grandmother.", icon = Icons.Default.Groups)
                if (state.canEdit) BigOutlinedButton("Add family member", icon = Icons.Default.Add, onClick = { nav.navigate(Routes.memberEdit()) })
            }
            items(state.members, key = { it.member.id }) { o ->
                AppCard(modifier = Modifier.clickable { nav.navigate(Routes.memberEdit(o.member.id)) }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MemberAvatar(o.member.name, 56)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(o.member.name + if (!o.member.active) " (inactive)" else "", style = MaterialTheme.typography.titleLarge)
                                Text("${o.medicineCount} active medicine${if (o.medicineCount == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            when {
                                o.total == 0 -> StatusChip("No doses today", Tone.GREY)
                                o.missed > 0 -> StatusChip("${o.missed} missed", Tone.RED)
                                o.pending > 0 -> StatusChip("${o.pending} due", Tone.AMBER)
                                o.taken == o.total -> StatusChip("All done", Tone.GREEN)
                                else -> StatusChip("On track", Tone.BLUE)
                            }
                        }
                        if (o.total > 0) {
                            Spacer(Modifier.height(12.dp))
                            LabelledProgress("${o.taken} of ${o.total} doses taken today", o.taken.toFloat() / o.total, if (o.missed > 0) Tone.RED else Tone.GREEN)
                        }
                        if (o.lowStock > 0) {
                            Spacer(Modifier.height(8.dp))
                            StatusChip("${o.lowStock} medicine${if (o.lowStock > 1) "s" else ""} low on stock", Tone.AMBER, icon = Icons.Default.Warning)
                        }
                        if (state.canEdit) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { nav.navigate(Routes.medicineEdit(memberId = o.member.id)) }) { Icon(Icons.Default.Add, contentDescription = null); Text(" Medicine") }
                                TextButton(onClick = { nav.navigate(Routes.medicineScan(o.member.id)) }) { Icon(Icons.Default.DocumentScanner, contentDescription = null); Text(" Scan") }
                                TextButton(onClick = { nav.navigate(Routes.memberEdit(o.member.id)) }) { Icon(Icons.Default.Edit, contentDescription = null); Text(" Edit") }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
