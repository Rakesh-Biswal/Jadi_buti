package com.chefotech.jadibuti.ui.prescription

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.chefotech.jadibuti.R
import com.chefotech.jadibuti.data.local.MemberEntity
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.remote.ApiException
import com.chefotech.jadibuti.data.remote.DoseTimeDto
import com.chefotech.jadibuti.data.remote.ExtractedMedicineDto
import com.chefotech.jadibuti.data.remote.InventoryDto
import com.chefotech.jadibuti.data.remote.MedicineDto
import com.chefotech.jadibuti.data.remote.PrescriptionDto
import com.chefotech.jadibuti.data.remote.ScheduleDto
import com.chefotech.jadibuti.data.repo.MemberRepository
import com.chefotech.jadibuti.data.repo.PrescriptionRepository
import com.chefotech.jadibuti.data.repo.UploadKind
import com.chefotech.jadibuti.domain.MealSlot
import com.chefotech.jadibuti.ui.auth.stringRes
import com.chefotech.jadibuti.ui.components.AppCard
import com.chefotech.jadibuti.ui.components.AppTopBar
import com.chefotech.jadibuti.ui.components.BigButton
import com.chefotech.jadibuti.ui.components.BigOutlinedButton
import com.chefotech.jadibuti.ui.components.ChoiceChips
import com.chefotech.jadibuti.ui.components.ErrorText
import com.chefotech.jadibuti.ui.components.InfoBanner
import com.chefotech.jadibuti.ui.components.LoadingBox
import com.chefotech.jadibuti.ui.components.SelectField
import com.chefotech.jadibuti.ui.components.TimeButton
import com.chefotech.jadibuti.ui.components.Tone
import com.chefotech.jadibuti.ui.format.defaultUnitFor
import com.chefotech.jadibuti.ui.format.formatQuantity
import com.chefotech.jadibuti.ui.format.typeLabel
import com.chefotech.jadibuti.ui.navigation.Routes
import com.chefotech.jadibuti.ui.scan.ImagePickerButtons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

// ------------------------------------------------------------------ capture

data class CaptureState(val memberId: String? = null, val image: File? = null, val busy: Boolean = false, val error: String? = null, val uploadedId: String? = null, val notConfigured: Boolean = false)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PrescriptionCaptureViewModel @Inject constructor(private val prescriptions: PrescriptionRepository, members: MemberRepository, session: SessionStore) : ViewModel() {
    val members = session.snapshot.flatMapLatest { s -> s.activeFamilyId?.let { members.observe(it) } ?: flowOf(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<MemberEntity>())
    val state = MutableStateFlow(CaptureState())

    fun setMember(id: String?) { state.value = state.value.copy(memberId = id) }
    fun setImage(f: File?) { state.value = state.value.copy(image = f, error = null) }

    /** Upload then run extraction. A 503 means the server has no AI key: keep the image, offer manual entry. */
    fun uploadAndExtract() {
        val s = state.value
        val file = s.image ?: run { state.value = s.copy(error = "Take or choose a photo first"); return }
        if (s.memberId == null) { state.value = s.copy(error = "Choose who this prescription is for"); return }
        state.value = s.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val uploaded = prescriptions.upload(UploadKind.PRESCRIPTION, file, "image/jpeg", s.memberId)
                try {
                    prescriptions.extract(UploadKind.PRESCRIPTION, uploaded.id)
                    state.value = state.value.copy(busy = false, uploadedId = uploaded.id)
                } catch (e: ApiException) {
                    if (e.code == "extraction_not_configured") state.value = state.value.copy(busy = false, notConfigured = true, uploadedId = uploaded.id)
                    else state.value = state.value.copy(busy = false, error = e.message)
                }
            } catch (e: Exception) {
                state.value = state.value.copy(busy = false, error = e.message ?: "Upload failed")
            }
        }
    }
}

@Composable
fun PrescriptionCaptureScreen(nav: NavHostController, memberId: String?, vm: PrescriptionCaptureViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val members by vm.members.collectAsState()
    LaunchedEffect(memberId) { if (memberId != null) vm.setMember(memberId) }
    LaunchedEffect(members) { if (state.memberId == null && members.size == 1) vm.setMember(members.first().id) }
    LaunchedEffect(state.uploadedId, state.notConfigured) {
        val id = state.uploadedId
        if (id != null && !state.notConfigured) nav.navigate(Routes.prescriptionReview(id, state.memberId)) { popUpTo(Routes.PRESCRIPTION_CAPTURE) { inclusive = true } }
    }

    Scaffold(topBar = { AppTopBar("Scan prescription", subtitle = "Doctor's prescription sheet", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How it works", style = MaterialTheme.typography.titleMedium)
                    Text("1. Take a clear photo of the prescription.\n2. Jadi-Buti reads the medicines and doses.\n3. You check every field next to the photo before anything is added.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            SelectField("Who is this prescription for?", members.map { it.id to it.name }, state.memberId, { vm.setMember(it) })
            state.image?.let { AsyncImage(model = it, contentDescription = "Prescription photo", modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit) }
            ImagePickerButtons(onImage = vm::setImage)
            ErrorText(state.error)
            if (state.notConfigured) {
                InfoBanner("AI prescription reading is not enabled on this server. Your photo was saved securely; you can add the medicines by hand.", Tone.AMBER, icon = Icons.Default.Warning)
                BigButton("Add medicine manually", onClick = { nav.navigate(Routes.medicineEdit(memberId = state.memberId)) { popUpTo(Routes.PRESCRIPTION_CAPTURE) { inclusive = true } } })
            } else {
                BigButton(if (state.busy) "Reading prescription…" else "Upload & read prescription", icon = Icons.Default.Receipt, enabled = !state.busy && state.image != null, onClick = vm::uploadAndExtract)
            }
            Text(stringRes(R.string.safety_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ review

/** One editable draft medicine. `uncertain` lists the fields the AI could not read confidently. */
data class DraftMedicine(
    val key: String = UUID.randomUUID().toString(),
    val name: String = "",
    val strength: String = "",
    val type: String = "TABLET",
    val doseAmount: String = "",
    val doseUnit: String = "tablet",
    val frequency: String = "DAILY",
    val times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val food: String = "NONE",
    val durationDays: String = "",
    val instructions: String = "",
    val initialStock: String = "",
    val rawText: String = "",
    val uncertain: Set<String> = emptySet(),
    val include: Boolean = true,
)

data class ReviewState(val prescription: PrescriptionDto? = null, val image: File? = null, val drafts: List<DraftMedicine> = emptyList(), val memberId: String? = null, val busy: Boolean = true, val error: String? = null, val done: Boolean = false, val notes: String = "")

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PrescriptionReviewViewModel @Inject constructor(private val prescriptions: PrescriptionRepository, members: MemberRepository, private val session: SessionStore) : ViewModel() {
    val members = session.snapshot.flatMapLatest { s -> s.activeFamilyId?.let { members.observe(it) } ?: flowOf(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<MemberEntity>())
    val state = MutableStateFlow(ReviewState())

    fun load(id: String, memberId: String?) = viewModelScope.launch {
        state.value = state.value.copy(busy = true, memberId = memberId)
        try {
            val p = prescriptions.get(UploadKind.PRESCRIPTION, id)
            val image = runCatching { prescriptions.cachedImage(UploadKind.PRESCRIPTION, id) }.getOrNull()
            state.value = state.value.copy(prescription = p, image = image, drafts = prescriptions.prescriptionItems(p).map(::toDraft), busy = false, notes = p.extraction.notes, memberId = memberId ?: p.memberId)
        } catch (e: Exception) { state.value = state.value.copy(busy = false, error = e.message) }
    }

    fun update(key: String, transform: (DraftMedicine) -> DraftMedicine) { state.value = state.value.copy(drafts = state.value.drafts.map { if (it.key == key) transform(it) else it }) }
    fun remove(key: String) { state.value = state.value.copy(drafts = state.value.drafts.filter { it.key != key }) }
    fun addBlank() { state.value = state.value.copy(drafts = state.value.drafts + DraftMedicine()) }
    fun setMember(id: String) { state.value = state.value.copy(memberId = id) }

    fun confirm() {
        val s = state.value
        val p = s.prescription ?: return
        val memberId = s.memberId ?: run { state.value = s.copy(error = "Choose who this prescription is for"); return }
        val included = s.drafts.filter { it.include }
        if (included.isEmpty()) { state.value = s.copy(error = "Nothing to add. Add a medicine or reject this prescription."); return }
        for (d in included) {
            if (d.name.isBlank()) { state.value = s.copy(error = "Every medicine needs a name"); return }
            if ((d.doseAmount.toDoubleOrNull() ?: 0.0) <= 0) { state.value = s.copy(error = "Enter the dose amount for ${d.name}"); return }
            if (d.times.isEmpty() || d.times.toSet().size != d.times.size) { state.value = s.copy(error = "Check the dose times for ${d.name}"); return }
        }
        state.value = s.copy(busy = true, error = null)
        viewModelScope.launch {
            val familyId = session.current.activeFamilyId!!
            val today = LocalDate.now()
            val stock = HashMap<String, Double>()
            val dtos = included.map { d ->
                val id = UUID.randomUUID().toString()
                d.initialStock.toDoubleOrNull()?.let { stock[id] = it }
                val days = d.durationDays.toIntOrNull()
                MedicineDto(
                    id = id, familyId = familyId, memberId = memberId, name = d.name.trim(), strength = d.strength.trim(), type = d.type, doseUnit = d.doseUnit.trim().ifBlank { "dose" },
                    foodInstruction = d.food, instructions = d.instructions.trim(), startDate = today.toString(), endDate = days?.takeIf { it > 0 }?.let { today.plusDays(it - 1L).toString() },
                    schedule = ScheduleDto(frequency = d.frequency, weekdays = if (d.frequency == "WEEKLY") listOf(today.dayOfWeek.value) else emptyList(), intervalDays = if (d.frequency == "ALTERNATE_DAYS") 2 else 1, referenceDate = today.toString()),
                    doseTimes = d.times.sorted().map { DoseTimeDto(it.toString().take(5), d.doseAmount.toDouble(), MealSlot.infer(it).name) },
                    inventory = InventoryDto(track = d.initialStock.toDoubleOrNull() != null), prescriptionId = p.id, updatedAt = System.currentTimeMillis(),
                )
            }
            runCatching { prescriptions.confirm(p.id, memberId, dtos, stock) }
                .onSuccess { state.value = state.value.copy(busy = false, done = true) }
                .onFailure { state.value = state.value.copy(busy = false, error = it.message) }
        }
    }

    fun reject() = viewModelScope.launch {
        state.value.prescription?.let { runCatching { prescriptions.reject(it.id) } }
        state.value = state.value.copy(done = true)
    }

    private fun toDraft(x: ExtractedMedicineDto): DraftMedicine {
        val uncertain = HashSet<String>()
        fun <T> f(name: String, field: com.chefotech.jadibuti.data.remote.ExtractedField<T>): T? { if (field.uncertain) uncertain += name; return field.value }
        val type = f("type", x.type) ?: "TABLET"
        val perDay = f("timesPerDay", x.timesPerDay) ?: 1
        val explicit = f("times", x.times)?.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }
        val times = if (!explicit.isNullOrEmpty()) explicit else defaultTimes(perDay)
        if (explicit.isNullOrEmpty()) uncertain += "times"
        return DraftMedicine(
            name = f("name", x.name) ?: "", strength = f("strength", x.strength) ?: "", type = type,
            doseAmount = f("doseAmount", x.doseAmount)?.let(::formatQuantity) ?: "", doseUnit = f("doseUnit", x.doseUnit) ?: defaultUnitFor(type),
            frequency = f("frequency", x.frequency) ?: "DAILY", times = times, food = f("foodInstruction", x.foodInstruction) ?: "NONE",
            durationDays = f("durationDays", x.durationDays)?.toString() ?: "", instructions = f("additionalInstructions", x.additionalInstructions) ?: "",
            rawText = x.rawText, uncertain = uncertain,
        )
    }

    private fun defaultTimes(n: Int): List<LocalTime> = when (n.coerceIn(1, 4)) {
        1 -> listOf(LocalTime.of(8, 0)); 2 -> listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)); 3 -> listOf(LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(20, 0))
        else -> listOf(LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(18, 0), LocalTime.of(22, 0))
    }
}

@Composable
fun PrescriptionReviewScreen(nav: NavHostController, id: String, memberId: String?, vm: PrescriptionReviewViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val members by vm.members.collectAsState()
    var fullImage by remember { mutableStateOf(false) }
    LaunchedEffect(id) { vm.load(id, memberId) }
    LaunchedEffect(state.done) { if (state.done) nav.navigate(Routes.MEDICINES) { popUpTo(Routes.HOME) } }

    Scaffold(topBar = { AppTopBar("Review prescription", subtitle = "Check every field before adding", onBack = { nav.popBackStack() }) }) { padding ->
        if (state.busy && state.prescription == null) { LoadingBox(Modifier.padding(padding)); return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoBanner("DRAFT — read by AI. Compare every field with the prescription image and correct anything wrong before confirming.", Tone.AMBER, icon = Icons.Default.Warning)
            state.image?.let { AsyncImage(model = it, contentDescription = "Original prescription", modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)).clickable { fullImage = true }, contentScale = ContentScale.Fit) }
            if (state.notes.isNotBlank()) Text("Note: ${state.notes}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectField("Who is this prescription for?", members.map { it.id to it.name }, state.memberId, { vm.setMember(it) })
            if (state.drafts.isEmpty()) InfoBanner("No medicines could be read from this image. You can add them by hand below or reject the prescription.", Tone.RED)
            state.drafts.forEach { d -> DraftCard(d, vm) }
            TextButton(onClick = vm::addBlank) { Text("+ Add a medicine that was not read") }
            ErrorText(state.error)
            Text(stringRes(R.string.verify_prescription), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigButton(if (state.busy) "Adding…" else "CONFIRM & ADD", enabled = !state.busy, onClick = vm::confirm)
            BigOutlinedButton("Reject this draft", onClick = vm::reject)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (fullImage && state.image != null) AlertDialog(onDismissRequest = { fullImage = false }, confirmButton = { TextButton(onClick = { fullImage = false }) { Text("Close") } }, text = { AsyncImage(model = state.image, contentDescription = null, modifier = Modifier.fillMaxWidth().height(520.dp), contentScale = ContentScale.Fit) })
}

@Composable
private fun DraftCard(d: DraftMedicine, vm: PrescriptionReviewViewModel) {
    val warn = "Unable to confidently read this field. Please verify."
    @Composable fun Uncertain(field: String) { if (field in d.uncertain) Text(warn, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    AppCard(container = if (d.include) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.name.ifBlank { "Medicine" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.remove(d.key) }) { Icon(Icons.Default.Delete, contentDescription = "Remove") }
            }
            if (d.rawText.isNotBlank()) Text("Read as: “${d.rawText}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(d.name, { v -> vm.update(d.key) { it.copy(name = v) } }, label = { Text("Medicine name") }, singleLine = true, modifier = Modifier.fillMaxWidth()); Uncertain("name")
            OutlinedTextField(d.strength, { v -> vm.update(d.key) { it.copy(strength = v) } }, label = { Text("Strength") }, singleLine = true, modifier = Modifier.fillMaxWidth()); Uncertain("strength")
            ChoiceChips(listOf("TABLET", "CAPSULE", "SYRUP", "LIQUID", "DROPS", "INJECTION", "OTHER").map { it to typeLabel(it) }, d.type) { t -> vm.update(d.key) { it.copy(type = t, doseUnit = defaultUnitFor(t)) } }; Uncertain("type")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(d.doseAmount, { v -> vm.update(d.key) { it.copy(doseAmount = v) } }, label = { Text("Dose") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(d.doseUnit, { v -> vm.update(d.key) { it.copy(doseUnit = v) } }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Uncertain("doseAmount"); Uncertain("doseUnit")
            Text("Frequency", style = MaterialTheme.typography.labelMedium)
            ChoiceChips(listOf("DAILY" to "Every day", "ALTERNATE_DAYS" to "Alternate days", "WEEKLY" to "Once a week"), d.frequency) { f -> vm.update(d.key) { it.copy(frequency = f) } }; Uncertain("frequency")
            Text("Times", style = MaterialTheme.typography.labelMedium)
            d.times.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeButton(t, { nt -> vm.update(d.key) { x -> x.copy(times = x.times.toMutableList().also { l -> l[i] = nt }) } }, Modifier.weight(1f))
                    Text(MealSlot.infer(t).label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { vm.update(d.key) { x -> x.copy(times = x.times.filterIndexed { j, _ -> j != i }) } }, enabled = d.times.size > 1) { Icon(Icons.Default.Delete, contentDescription = "Remove time") }
                }
            }
            TextButton(onClick = { vm.update(d.key) { x -> x.copy(times = x.times + (x.times.lastOrNull() ?: LocalTime.of(8, 0)).plusHours(6)) } }) { Text("+ Add time") }
            Uncertain("times"); Uncertain("timesPerDay")
            ChoiceChips(listOf("BEFORE_FOOD" to "Before food", "AFTER_FOOD" to "After food", "WITH_FOOD" to "With food", "EMPTY_STOMACH" to "Empty stomach", "NONE" to "No instruction"), d.food) { f -> vm.update(d.key) { it.copy(food = f) } }; Uncertain("foodInstruction")
            OutlinedTextField(d.durationDays, { v -> vm.update(d.key) { it.copy(durationDays = v.filter(Char::isDigit)) } }, label = { Text("Duration in days (blank = ongoing)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()); Uncertain("durationDays")
            OutlinedTextField(d.instructions, { v -> vm.update(d.key) { it.copy(instructions = v) } }, label = { Text("Additional instructions") }, modifier = Modifier.fillMaxWidth()); Uncertain("additionalInstructions")
            OutlinedTextField(d.initialStock, { v -> vm.update(d.key) { it.copy(initialStock = v) } }, label = { Text("Current stock (optional, ${d.doseUnit}s)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        }
    }
}
