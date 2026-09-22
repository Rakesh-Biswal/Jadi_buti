package com.chefotech.jadibuti.ui.inventory

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.repo.InventoryRepository
import com.chefotech.jadibuti.data.repo.StockRow
import com.chefotech.jadibuti.ui.components.AppCard
import com.chefotech.jadibuti.ui.components.AppTopBar
import com.chefotech.jadibuti.ui.components.ChoiceChips
import com.chefotech.jadibuti.ui.components.EmptyState
import com.chefotech.jadibuti.ui.components.IconBadge
import com.chefotech.jadibuti.ui.components.LabelledProgress
import com.chefotech.jadibuti.ui.components.StatusChip
import com.chefotech.jadibuti.ui.components.Tone
import com.chefotech.jadibuti.ui.format.formatDose
import com.chefotech.jadibuti.ui.format.formatQuantity
import com.chefotech.jadibuti.ui.format.typeIcon
import com.chefotech.jadibuti.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventoryViewModel @Inject constructor(private val inventory: InventoryRepository, session: SessionStore) : ViewModel() {
    val rows = session.snapshot.flatMapLatest { s -> s.activeFamilyId?.let { inventory.observeStock(it) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<StockRow>())
    val canEdit = session.snapshot
    fun refill(id: String, q: Double) = viewModelScope.launch { inventory.refill(id, q) }
    fun correct(id: String, q: Double) = viewModelScope.launch { inventory.correctTo(id, q) }
}

@Composable
fun InventoryScreen(nav: NavHostController, vm: InventoryViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsState()
    val session by vm.canEdit.collectAsState()
    var editing by remember { mutableStateOf<StockRow?>(null) }
    val low = rows.count { it.summary.lowStock }
    Scaffold(topBar = { AppTopBar("Medicine stock", subtitle = if (low > 0) "$low running low" else "All stocked", onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Stock goes down automatically each time a dose is marked as taken. Tap a medicine to add stock or correct the count.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (rows.isEmpty()) item { EmptyState("No tracked medicines", "Enable stock tracking on a medicine to see it here.", icon = Icons.Default.Inventory) }
            items(rows, key = { it.medicine.id }) { r ->
                val s = r.summary
                AppCard(modifier = Modifier.clickable { if (session.canEdit) editing = r else nav.navigate(Routes.medicineDetail(r.medicine.id)) }, container = if (s.lowStock) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(typeIcon(r.medicine.type), if (s.lowStock) Tone.RED else Tone.GREEN)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.medicine.name + if (r.medicine.strength.isNotBlank()) " ${r.medicine.strength}" else "", style = MaterialTheme.typography.titleLarge)
                                Text(r.memberName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (s.lowStock) StatusChip("Low", Tone.RED) else StatusChip("OK", Tone.GREEN)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(formatDose(s.stock.coerceAtLeast(0.0), r.medicine.doseUnit) + " remaining", style = MaterialTheme.typography.headlineSmall)
                        if (s.baseline > 0) { Spacer(Modifier.height(6.dp)); LabelledProgress("${formatQuantity((s.stock / s.baseline * 100).coerceIn(0.0, 100.0))}% of last refill", (s.stock / s.baseline).toFloat(), if (s.lowStock) Tone.RED else Tone.GREEN) }
                        s.estimatedDaysLeft?.let { Text("About $it day${if (it == 1) "" else "s"} of supply left", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp)) }
                    }
                }
            }
        }
    }
    editing?.let { r -> StockDialog(r.medicine.name, r.medicine.doseUnit, r.summary.stock, onDismiss = { editing = null }, onRefill = { vm.refill(r.medicine.id, it); editing = null }, onCorrect = { vm.correct(r.medicine.id, it); editing = null }) }
}

/** Refill (add) or correction (set exact count). */
@Composable
fun StockDialog(name: String, unit: String, current: Double, onDismiss: () -> Unit, onRefill: (Double) -> Unit, onCorrect: (Double) -> Unit) {
    var mode by remember { mutableStateOf("ADD") }
    var qty by remember { mutableStateOf("") }
    val value = qty.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current: ${formatDose(current.coerceAtLeast(0.0), unit)}", style = MaterialTheme.typography.bodyLarge)
                ChoiceChips(listOf("ADD" to "Add new stock", "SET" to "Set exact count"), mode) { mode = it }
                OutlinedTextField(qty, { qty = it }, label = { Text(if (mode == "ADD") "Quantity to add" else "Counted quantity") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                if (mode == "SET" && value != null) Text("Change: ${if (value - current >= 0) "+" else ""}${formatQuantity(value - current)}", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(enabled = value != null && value >= 0 && (mode == "SET" || value > 0), onClick = { if (mode == "ADD") onRefill(value!!) else onCorrect(value!!) }) { Text("Save", style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
