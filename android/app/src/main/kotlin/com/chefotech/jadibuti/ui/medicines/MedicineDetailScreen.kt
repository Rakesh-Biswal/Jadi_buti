package com.chefotech.jadibuti.ui.medicines

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.chefotech.jadibuti.data.doseTimes
import com.chefotech.jadibuti.data.local.MedicineEntity
import com.chefotech.jadibuti.data.local.TransactionEntity
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.repo.InventoryRepository
import com.chefotech.jadibuti.data.repo.MedicineRepository
import com.chefotech.jadibuti.data.repo.MemberRepository
import com.chefotech.jadibuti.data.weekdayList
import com.chefotech.jadibuti.domain.MealSlot
import com.chefotech.jadibuti.domain.StockSummary
import com.chefotech.jadibuti.ui.components.AppCard
import com.chefotech.jadibuti.ui.components.AppTopBar
import com.chefotech.jadibuti.ui.components.BigButton
import com.chefotech.jadibuti.ui.components.BigOutlinedButton
import com.chefotech.jadibuti.ui.components.IconBadge
import com.chefotech.jadibuti.ui.components.IconLabel
import com.chefotech.jadibuti.ui.components.LabelledProgress
import com.chefotech.jadibuti.ui.components.LoadingBox
import com.chefotech.jadibuti.ui.components.SectionHeader
import com.chefotech.jadibuti.ui.components.StatusChip
import com.chefotech.jadibuti.ui.components.Tone
import com.chefotech.jadibuti.ui.format.foodIcon
import com.chefotech.jadibuti.ui.format.foodLabel
import com.chefotech.jadibuti.ui.format.formatDate
import com.chefotech.jadibuti.ui.format.formatDose
import com.chefotech.jadibuti.ui.format.formatQuantity
import com.chefotech.jadibuti.ui.format.formatTime
import com.chefotech.jadibuti.ui.format.frequencyLabel
import com.chefotech.jadibuti.ui.format.mealIcon
import com.chefotech.jadibuti.ui.format.typeIcon
import com.chefotech.jadibuti.ui.format.typeLabel
import com.chefotech.jadibuti.ui.inventory.StockDialog
import com.chefotech.jadibuti.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class MedicineDetailState(val medicine: MedicineEntity? = null, val memberName: String = "", val summary: StockSummary? = null, val ledger: List<TransactionEntity> = emptyList(), val canEdit: Boolean = true)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class MedicineDetailViewModel @Inject constructor(
    private val medicines: MedicineRepository,
    private val members: MemberRepository,
    private val inventory: InventoryRepository,
    private val session: SessionStore,
) : ViewModel() {
    private val id = MutableStateFlow<String?>(null)
    val deleted = MutableStateFlow(false)

    val state = id.flatMapLatest { mid ->
        if (mid == null) return@flatMapLatest kotlinx.coroutines.flow.flowOf(MedicineDetailState())
        combine(medicines.observeOne(mid), inventory.observeLedger(mid), session.snapshot) { m, ledger, snap ->
            if (m == null) MedicineDetailState()
            else MedicineDetailState(m, members.get(m.memberId)?.name ?: "", if (m.trackInventory) inventory.summary(m) else null, ledger, snap.canEdit)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicineDetailState())

    fun load(medicineId: String) { id.value = medicineId }
    fun setActive(active: Boolean) = viewModelScope.launch { id.value?.let { medicines.setActive(it, active) } }
    fun delete() = viewModelScope.launch { id.value?.let { medicines.delete(it) }; deleted.value = true }
    fun refill(q: Double) = viewModelScope.launch { id.value?.let { inventory.refill(it, q) } }
    fun correct(q: Double) = viewModelScope.launch { id.value?.let { inventory.correctTo(it, q) } }
}

@Composable
fun MedicineDetailScreen(nav: NavHostController, id: String, vm: MedicineDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val deleted by vm.deleted.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var stockDialog by remember { mutableStateOf(false) }
    LaunchedEffect(id) { vm.load(id) }
    LaunchedEffect(deleted) { if (deleted) nav.popBackStack() }
    val m = state.medicine

    Scaffold(topBar = { AppTopBar(m?.name ?: "Medicine", subtitle = state.memberName.takeIf { it.isNotBlank() }?.let { "For $it" }, onBack = { nav.popBackStack() }) }) { padding ->
        if (m == null) { LoadingBox(Modifier.padding(padding)); return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(typeIcon(m.type), if (m.active) Tone.GREEN else Tone.GREY, 52)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name + if (m.strength.isNotBlank()) " ${m.strength}" else "", style = MaterialTheme.typography.headlineSmall)
                            Text(typeLabel(m.type), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!m.active) StatusChip("Paused", Tone.GREY) else StatusChip("Active", Tone.GREEN)
                    }
                    IconLabel(foodIcon(m.foodInstruction), foodLabel(m.foodInstruction))
                    IconLabel(Icons.Default.Schedule, frequencyLabel(m.frequency, m.weekdayList(), m.intervalDays))
                    Text("From ${formatDate(m.startDate)}" + (m.endDate?.let { " to ${formatDate(it)}" } ?: " · ongoing"), style = MaterialTheme.typography.bodyMedium)
                    if (m.instructions.isNotBlank()) Text(m.instructions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionHeader("Dose times", icon = Icons.Default.Schedule)
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    m.doseTimes().forEach { d ->
                        val slot = MealSlot.forDose(d.meal, LocalTime.parse(d.time))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(mealIcon(slot), Tone.BLUE, 40)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("${slot.label} · ${formatTime(d.time)}", style = MaterialTheme.typography.titleMedium)
                                Text(formatDose(d.amount, m.doseUnit), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            val s = state.summary
            if (s != null) {
                SectionHeader("Stock", icon = Icons.Default.Inventory)
                AppCard(container = if (s.lowStock) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatDose(s.stock.coerceAtLeast(0.0), m.doseUnit) + " remaining", style = MaterialTheme.typography.headlineSmall)
                        if (s.baseline > 0) LabelledProgress("${formatQuantity((s.stock / s.baseline * 100).coerceIn(0.0, 100.0))}% of last refill", (s.stock / s.baseline).toFloat(), if (s.lowStock) Tone.RED else Tone.GREEN)
                        s.estimatedDaysLeft?.let { Text("Estimated supply: approximately $it day${if (it == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge) }
                        if (s.lowStock) Text("Running low — consider arranging a refill.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        Text("Low-stock alert at ${formatQuantity(m.lowStockValue)}${if (m.lowStockType == "PERCENT") "%" else " " + m.doseUnit}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.canEdit) BigOutlinedButton("Add stock / correct count", icon = Icons.Default.Inventory, onClick = { stockDialog = true })
                    }
                }
                if (state.ledger.isNotEmpty()) {
                    SectionHeader("Stock history")
                    val fmt = DateTimeFormatter.ofPattern("d MMM, h:mm a")
                    AppCard {
                        Column {
                            state.ledger.take(30).forEach { t ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t.note.ifBlank { t.type.lowercase().replace('_', ' ') }, style = MaterialTheme.typography.bodyLarge)
                                        Text(Instant.ofEpochMilli(t.createdAt).atZone(ZoneId.systemDefault()).format(fmt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text((if (t.quantityDelta >= 0) "+" else "") + formatQuantity(t.quantityDelta), style = MaterialTheme.typography.titleMedium, color = if (t.quantityDelta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
            if (state.canEdit) {
                Spacer(Modifier.height(8.dp))
                BigButton("Edit medicine", icon = Icons.Default.Edit, onClick = { nav.navigate(Routes.medicineEdit(m.id)) })
                BigOutlinedButton(if (m.active) "Pause reminders" else "Resume reminders", icon = if (m.active) Icons.Default.Pause else Icons.Default.PlayArrow, onClick = { vm.setActive(!m.active) })
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { androidx.compose.material3.Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error); Text(" Delete medicine", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${m?.name}?") },
        text = { Text("Future reminders will stop. Past medication history is kept.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
    if (stockDialog && m != null) StockDialog(m.name, m.doseUnit, state.summary?.stock ?: 0.0, onDismiss = { stockDialog = false }, onRefill = { vm.refill(it); stockDialog = false }, onCorrect = { vm.correct(it); stockDialog = false })
}
