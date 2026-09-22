package com.chefotech.jadibuti.ui.medicines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import com.chefotech.jadibuti.data.doseTimes
import com.chefotech.jadibuti.data.local.MedicineEntity
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.repo.InventoryRepository
import com.chefotech.jadibuti.data.repo.MedicineRepository
import com.chefotech.jadibuti.data.repo.MemberRepository
import com.chefotech.jadibuti.data.weekdayList
import com.chefotech.jadibuti.domain.MealSlot
import com.chefotech.jadibuti.ui.components.AppCard
import com.chefotech.jadibuti.ui.components.AppTopBar
import com.chefotech.jadibuti.ui.components.BigOutlinedButton
import com.chefotech.jadibuti.ui.components.EmptyState
import com.chefotech.jadibuti.ui.components.IconBadge
import com.chefotech.jadibuti.ui.components.IconLabel
import com.chefotech.jadibuti.ui.components.StatusChip
import com.chefotech.jadibuti.ui.components.Tone
import com.chefotech.jadibuti.ui.format.foodIcon
import com.chefotech.jadibuti.ui.format.foodLabel
import com.chefotech.jadibuti.ui.format.formatDose
import com.chefotech.jadibuti.ui.format.formatTime
import com.chefotech.jadibuti.ui.format.frequencyLabel
import com.chefotech.jadibuti.ui.format.mealIcon
import com.chefotech.jadibuti.ui.format.typeIcon
import com.chefotech.jadibuti.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime
import javax.inject.Inject

data class MedicineRow(val medicine: MedicineEntity, val memberName: String, val lowStock: Boolean, val stock: Double?)
data class MedicinesState(val rows: List<MedicineRow> = emptyList(), val canEdit: Boolean = true, val loaded: Boolean = false)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class MedicinesViewModel @Inject constructor(session: SessionStore, medicines: MedicineRepository, members: MemberRepository, inventory: InventoryRepository) : ViewModel() {
    val state = session.snapshot.flatMapLatest { snap ->
        val familyId = snap.activeFamilyId ?: return@flatMapLatest flowOf(MedicinesState(loaded = true))
        combine(medicines.observe(familyId), members.observe(familyId), inventory.observeStock(familyId)) { meds, mems, stock ->
            val names = mems.associate { it.id to it.name }
            val stockById = stock.associateBy { it.medicine.id }
            MedicinesState(meds.map { MedicineRow(it, names[it.memberId] ?: "", stockById[it.id]?.summary?.lowStock == true, stockById[it.id]?.summary?.stock) }.sortedWith(compareBy({ !it.medicine.active }, { it.memberName }, { it.medicine.name })), snap.canEdit, true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicinesState())
}

@Composable
fun MedicinesScreen(nav: NavHostController, vm: MedicinesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    Scaffold(
        topBar = { AppTopBar("Medicines", subtitle = "${state.rows.count { it.medicine.active }} active") },
        floatingActionButton = { if (state.canEdit) ExtendedFloatingActionButton(onClick = { nav.navigate(Routes.medicineEdit()) }, icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text("Add medicine", style = MaterialTheme.typography.labelLarge) }, containerColor = MaterialTheme.colorScheme.primary, contentColor = androidx.compose.ui.graphics.Color.White) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.canEdit) item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigOutlinedButton("Scan medicine", icon = Icons.Default.DocumentScanner, modifier = Modifier.weight(1f), onClick = { nav.navigate(Routes.medicineScan()) })
                    BigOutlinedButton("Prescription", icon = Icons.Default.Receipt, modifier = Modifier.weight(1f), onClick = { nav.navigate(Routes.prescriptionCapture()) })
                }
            }
            if (state.loaded && state.rows.isEmpty()) item {
                EmptyState("No medicines yet", "Add a medicine by hand, scan its packaging, or scan a prescription and review what was read.", icon = Icons.Default.Medication)
            }
            items(state.rows, key = { it.medicine.id }) { row -> MedicineListCard(row) { nav.navigate(Routes.medicineDetail(row.medicine.id)) } }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun MedicineListCard(row: MedicineRow, onClick: () -> Unit) {
    val m = row.medicine
    AppCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(typeIcon(m.type), if (m.active) Tone.GREEN else Tone.GREY)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name + if (m.strength.isNotBlank()) " ${m.strength}" else "", style = MaterialTheme.typography.titleLarge)
                    Text(row.memberName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!m.active) StatusChip("Paused", Tone.GREY) else if (row.lowStock) StatusChip("Low stock", Tone.AMBER)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconLabel(foodIcon(m.foodInstruction), foodLabel(m.foodInstruction))
                IconLabel(androidx.compose.material.icons.Icons.Default.Medication, frequencyLabel(m.frequency, m.weekdayList(), m.intervalDays))
            }
            Spacer(Modifier.height(6.dp))
            m.doseTimes().forEach { d ->
                val slot = MealSlot.forDose(d.meal, LocalTime.parse(d.time))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(mealIcon(slot), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${slot.label} · ${formatTime(d.time)} → ${formatDose(d.amount, m.doseUnit)}", style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (row.stock != null) Text("Stock: ${formatDose(row.stock, m.doseUnit)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
