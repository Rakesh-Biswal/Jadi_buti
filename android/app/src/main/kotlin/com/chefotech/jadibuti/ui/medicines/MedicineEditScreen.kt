package com.chefotech.jadibuti.ui.medicines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.chefotech.jadibuti.R
import com.chefotech.jadibuti.data.doseTimes
import com.chefotech.jadibuti.data.local.MedicineEntity
import com.chefotech.jadibuti.data.local.MemberEntity
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.remote.DoseTimeDto
import com.chefotech.jadibuti.data.repo.MedicineDraft
import com.chefotech.jadibuti.data.repo.MedicineRepository
import com.chefotech.jadibuti.data.repo.MemberRepository
import com.chefotech.jadibuti.data.weekdayList
import com.chefotech.jadibuti.domain.MealSlot
import com.chefotech.jadibuti.ui.auth.stringRes
import com.chefotech.jadibuti.ui.components.AppCard
import com.chefotech.jadibuti.ui.components.AppTopBar
import com.chefotech.jadibuti.ui.components.BigButton
import com.chefotech.jadibuti.ui.components.BigOutlinedButton
import com.chefotech.jadibuti.ui.components.ChoiceChips
import com.chefotech.jadibuti.ui.components.DatePickerField
import com.chefotech.jadibuti.ui.components.ErrorText
import com.chefotech.jadibuti.ui.components.InfoBanner
import com.chefotech.jadibuti.ui.components.MultiChoiceChips
import com.chefotech.jadibuti.ui.components.SectionHeader
import com.chefotech.jadibuti.ui.components.SelectField
import com.chefotech.jadibuti.ui.components.TimeButton
import com.chefotech.jadibuti.ui.components.Tone
import com.chefotech.jadibuti.ui.format.defaultUnitFor
import com.chefotech.jadibuti.ui.format.formatQuantity
import com.chefotech.jadibuti.ui.format.typeLabel
import com.chefotech.jadibuti.ui.format.weekdayName
import com.chefotech.jadibuti.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** One dose row in the form: clock time, amount, and the meal it belongs to (auto from time unless chosen). */
data class DoseTimeInput(val time: LocalTime, val amount: String, val meal: MealSlot? = null) {
    val slot: MealSlot get() = meal ?: MealSlot.infer(time)
}

data class MedicineForm(
    val memberId: String? = null,
    val name: String = "",
    val strength: String = "",
    val type: String = "TABLET",
    val doseUnit: String = "tablet",
    val food: String = "NONE",
    val instructions: String = "",
    val startDate: String = LocalDate.now().toString(),
    val ongoing: Boolean = true,
    val endDate: String? = null,
    val frequency: String = "DAILY",
    val weekdays: Set<Int> = emptySet(),
    val intervalDays: String = "3",
    val doseTimes: List<DoseTimeInput> = listOf(DoseTimeInput(LocalTime.of(8, 0), "1", MealSlot.BREAKFAST)),
    val trackInventory: Boolean = true,
    val initialStock: String = "",
    val lowStockType: String = "PERCENT",
    val lowStockValue: String = "5",
    val scanId: String? = null,
    val fromScan: Boolean = false,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class MedicineEditViewModel @Inject constructor(private val medicines: MedicineRepository, members: MemberRepository, session: SessionStore) : ViewModel() {
    val members = session.snapshot.flatMapLatest { s -> s.activeFamilyId?.let { members.observe(it) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<MemberEntity>())
    val form = MutableStateFlow(MedicineForm())
    val error = MutableStateFlow<String?>(null)
    val saving = MutableStateFlow(false)
    val savedId = MutableStateFlow<String?>(null)
    private var loadedFor: String? = null

    fun load(id: String?, memberId: String?, prefill: Routes.Prefill?) = viewModelScope.launch {
        val key = "$id|$memberId|${prefill?.scanId}|${prefill?.name}"
        if (loadedFor == key) return@launch
        loadedFor = key
        if (id != null) {
            val m = medicines.get(id) ?: return@launch
            form.value = MedicineForm(
                memberId = m.memberId, name = m.name, strength = m.strength, type = m.type, doseUnit = m.doseUnit, food = m.foodInstruction,
                instructions = m.instructions, startDate = m.startDate, ongoing = m.endDate == null, endDate = m.endDate, frequency = m.frequency,
                weekdays = m.weekdayList().toSet(), intervalDays = m.intervalDays.toString(),
                doseTimes = m.doseTimes().map { DoseTimeInput(LocalTime.parse(it.time), formatQuantity(it.amount), it.meal?.let { s -> runCatching { MealSlot.valueOf(s) }.getOrNull() }) },
                trackInventory = m.trackInventory, lowStockType = m.lowStockType, lowStockValue = formatQuantity(m.lowStockValue),
            )
        } else {
            form.value = form.value.copy(
                memberId = memberId ?: form.value.memberId,
                name = prefill?.name ?: form.value.name,
                strength = prefill?.strength ?: form.value.strength,
                type = prefill?.type ?: form.value.type,
                doseUnit = prefill?.type?.let(::defaultUnitFor) ?: form.value.doseUnit,
                instructions = prefill?.composition?.takeIf { it.isNotBlank() }?.let { "Composition: $it" } ?: form.value.instructions,
                scanId = prefill?.scanId,
                fromScan = prefill != null,
            )
        }
    }

    fun update(transform: (MedicineForm) -> MedicineForm) { form.value = transform(form.value) }

    fun save(existingId: String?) {
        val f = form.value
        val validation = validate(f)
        if (validation != null) { error.value = validation; return }
        saving.value = true
        viewModelScope.launch {
            runCatching {
                medicines.save(
                    MedicineDraft(
                        id = existingId, memberId = f.memberId!!, name = f.name, strength = f.strength, type = f.type, doseUnit = f.doseUnit,
                        foodInstruction = f.food, instructions = f.instructions, startDate = f.startDate, endDate = if (f.ongoing) null else f.endDate,
                        frequency = f.frequency, weekdays = f.weekdays.toList(), intervalDays = f.intervalDays.toIntOrNull() ?: 1,
                        doseTimes = f.doseTimes.map { DoseTimeDto(it.time.toString().take(5), it.amount.toDouble(), it.meal?.name) },
                        trackInventory = f.trackInventory, lowStockType = f.lowStockType, lowStockValue = f.lowStockValue.toDoubleOrNull() ?: 5.0,
                        initialStock = f.initialStock.toDoubleOrNull(), prescriptionId = f.scanId,
                    ),
                )
            }.onSuccess { savedId.value = it.id }.onFailure { error.value = it.message }
            saving.value = false
        }
    }

    private fun validate(f: MedicineForm): String? = when {
        f.memberId == null -> "Choose who this medicine is for"
        f.name.isBlank() -> "Enter the medicine name"
        f.doseTimes.isEmpty() -> "Add at least one dose time"
        f.doseTimes.any { (it.amount.toDoubleOrNull() ?: 0.0) <= 0 } -> "Each dose amount must be greater than zero"
        f.doseTimes.map { it.time }.toSet().size != f.doseTimes.size -> "Two doses have the same time"
        (f.frequency == "SPECIFIC_WEEKDAYS" || f.frequency == "WEEKLY") && f.weekdays.isEmpty() -> "Choose at least one day of the week"
        f.frequency == "CUSTOM_INTERVAL" && (f.intervalDays.toIntOrNull() ?: 0) < 1 -> "Enter how many days between doses"
        !f.ongoing && f.endDate == null -> "Choose an end date or mark as ongoing"
        !f.ongoing && f.endDate!! < f.startDate -> "End date is before the start date"
        f.trackInventory && f.lowStockType == "PERCENT" && (f.lowStockValue.toDoubleOrNull() ?: -1.0) !in 0.0..100.0 -> "Low-stock percentage must be between 0 and 100"
        f.trackInventory && f.lowStockType == "QUANTITY" && (f.lowStockValue.toDoubleOrNull() ?: -1.0) < 0 -> "Low-stock quantity cannot be negative"
        else -> null
    }
}

@Composable
fun MedicineEditScreen(nav: NavHostController, id: String?, memberId: String?, prefill: Routes.Prefill? = null, vm: MedicineEditViewModel = hiltViewModel()) {
    val form by vm.form.collectAsState()
    val members by vm.members.collectAsState()
    val error by vm.error.collectAsState()
    val saving by vm.saving.collectAsState()
    val savedId by vm.savedId.collectAsState()
    LaunchedEffect(id, memberId, prefill) { vm.load(id, memberId, prefill) }
    LaunchedEffect(savedId) { if (savedId != null) nav.popBackStack() }
    LaunchedEffect(members) { if (form.memberId == null && members.size == 1) vm.update { it.copy(memberId = members.first().id) } }

    val title = if (id == null) "Add medicine" else "Edit medicine"
    val subtitle = if (form.fromScan) "Step 2 of 2 · Doses & schedule" else null

    Scaffold(topBar = { AppTopBar(title, subtitle = subtitle, onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (id == null && !form.fromScan) {
                BigOutlinedButton("Scan medicine packaging instead", icon = Icons.Default.DocumentScanner, onClick = { nav.navigate(Routes.medicineScan(form.memberId)) })
            }
            if (form.fromScan) InfoBanner("Details were read from your photo and confirmed. Now set up when and how much to take.", Tone.GREEN, icon = Icons.Default.DocumentScanner)

            SectionHeader("Who", icon = Icons.Default.Person)
            SelectField("Who is this medicine for?", members.map { it.id to it.name }, form.memberId, { m -> vm.update { it.copy(memberId = m) } })
            if (members.isEmpty()) Text("Add a family member first (Family tab).", color = MaterialTheme.colorScheme.error)

            SectionHeader("Medicine", icon = Icons.Default.Medication)
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(form.name, { v -> vm.update { it.copy(name = v) } }, label = { Text("Medicine name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(form.strength, { v -> vm.update { it.copy(strength = v) } }, label = { Text("Strength (e.g. 40 mg)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Type", style = MaterialTheme.typography.labelMedium)
                    ChoiceChips(listOf("TABLET", "CAPSULE", "SYRUP", "LIQUID", "DROPS", "INJECTION", "OTHER").map { it to typeLabel(it) }, form.type) { t -> vm.update { it.copy(type = t, doseUnit = defaultUnitFor(t)) } }
                    OutlinedTextField(form.doseUnit, { v -> vm.update { it.copy(doseUnit = v) } }, label = { Text("Dose unit (tablet, ml…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }

            SectionHeader("Dose times", icon = Icons.Default.Schedule)
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Each dose can have its own time, amount and meal.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    form.doseTimes.forEachIndexed { i, d ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TimeButton(d.time, { t -> vm.update { f -> f.copy(doseTimes = f.doseTimes.toMutableList().also { l -> l[i] = l[i].copy(time = t) }) } }, Modifier.weight(1.2f))
                                OutlinedTextField(d.amount, { v -> vm.update { f -> f.copy(doseTimes = f.doseTimes.toMutableList().also { l -> l[i] = l[i].copy(amount = v) }) } }, label = { Text("Amount") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                                Text(form.doseUnit, style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { vm.update { f -> f.copy(doseTimes = f.doseTimes.filterIndexed { j, _ -> j != i }) } }, enabled = form.doseTimes.size > 1) { Icon(Icons.Default.Delete, contentDescription = "Remove dose") }
                            }
                            ChoiceChips(MealSlot.entries.map { it to it.label }, d.slot) { slot ->
                                vm.update { f -> f.copy(doseTimes = f.doseTimes.toMutableList().also { l -> l[i] = l[i].copy(meal = slot, time = if (MealSlot.infer(l[i].time) == slot) l[i].time else MealSlot.defaultTime(slot)) }) }
                            }
                        }
                    }
                    TextButton(onClick = {
                        vm.update { f ->
                            val used = f.doseTimes.map { it.slot }.toSet()
                            val next = MealSlot.entries.firstOrNull { it !in used } ?: MealSlot.DINNER
                            f.copy(doseTimes = f.doseTimes + DoseTimeInput(MealSlot.defaultTime(next), f.doseTimes.lastOrNull()?.amount ?: "1", next))
                        }
                    }) { Text("+ Add another dose time") }
                }
            }

            SectionHeader("How often", icon = Icons.Default.Schedule)
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceChips(listOf("DAILY" to "Every day", "ALTERNATE_DAYS" to "Alternate days", "SPECIFIC_WEEKDAYS" to "Specific days", "WEEKLY" to "Once a week", "CUSTOM_INTERVAL" to "Every N days"), form.frequency) { fq -> vm.update { it.copy(frequency = fq) } }
                    if (form.frequency == "SPECIFIC_WEEKDAYS" || form.frequency == "WEEKLY") {
                        MultiChoiceChips((1..7).map { it to weekdayName(it) }, form.weekdays) { d -> vm.update { f -> f.copy(weekdays = if (form.frequency == "WEEKLY") setOf(d) else if (d in f.weekdays) f.weekdays - d else f.weekdays + d) } }
                    }
                    if (form.frequency == "CUSTOM_INTERVAL") {
                        OutlinedTextField(form.intervalDays, { v -> vm.update { it.copy(intervalDays = v.filter(Char::isDigit)) } }, label = { Text("Every how many days?") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    }
                    if (form.frequency == "ALTERNATE_DAYS") Text("Alternate days are counted from the start date (take, skip, take…).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DatePickerField("Start date", form.startDate, { d -> vm.update { it.copy(startDate = d ?: it.startDate) } })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ongoing (no end date)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(form.ongoing, { o -> vm.update { it.copy(ongoing = o) } })
                    }
                    if (!form.ongoing) DatePickerField("End date", form.endDate, { d -> vm.update { it.copy(endDate = d) } })
                }
            }

            SectionHeader("Food", icon = Icons.Default.Restaurant)
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceChips(listOf("BEFORE_FOOD" to "Before food", "AFTER_FOOD" to "After food", "WITH_FOOD" to "With food", "EMPTY_STOMACH" to "Empty stomach", "NONE" to "No instruction"), form.food) { fo -> vm.update { it.copy(food = fo) } }
                    OutlinedTextField(form.instructions, { v -> vm.update { it.copy(instructions = v) } }, label = { Text("Additional instructions (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            }

            SectionHeader("Stock", icon = Icons.Default.Inventory)
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Track stock and warn when low", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(form.trackInventory, { t -> vm.update { it.copy(trackInventory = t) } })
                    }
                    if (form.trackInventory) {
                        if (id == null) OutlinedTextField(form.initialStock, { v -> vm.update { it.copy(initialStock = v) } }, label = { Text("Current quantity (${form.doseUnit}s)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        Text("Warn me when stock reaches", style = MaterialTheme.typography.labelMedium)
                        ChoiceChips(listOf("PERCENT" to "Percentage", "QUANTITY" to "Quantity"), form.lowStockType) { t -> vm.update { it.copy(lowStockType = t, lowStockValue = "5") } }
                        if (form.lowStockType == "PERCENT") ChoiceChips(listOf("3" to "3%", "5" to "5%", "10" to "10%", "20" to "20%"), form.lowStockValue) { v -> vm.update { it.copy(lowStockValue = v) } }
                        OutlinedTextField(form.lowStockValue, { v -> vm.update { it.copy(lowStockValue = v) } }, label = { Text(if (form.lowStockType == "PERCENT") "Custom percentage" else "Quantity (${form.doseUnit}s)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            ErrorText(error)
            Text(stringRes(R.string.verify_prescription), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigButton(if (saving) "Saving…" else if (form.fromScan) "CONFIRM & ACTIVATE MEDICINE" else "Save medicine", enabled = !saving, onClick = { vm.save(id) })
            BigOutlinedButton("Cancel", onClick = { nav.popBackStack() })
            Spacer(Modifier.height(24.dp))
        }
    }
}
