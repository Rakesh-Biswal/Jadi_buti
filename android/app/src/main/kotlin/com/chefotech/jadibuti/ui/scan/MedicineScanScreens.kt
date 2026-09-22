package com.chefotech.jadibuti.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.chefotech.jadibuti.R
import com.chefotech.jadibuti.data.remote.ApiException
import com.chefotech.jadibuti.data.remote.ExtractedMedicinePhotoDto
import com.chefotech.jadibuti.data.remote.PrescriptionDto
import com.chefotech.jadibuti.data.repo.PrescriptionRepository
import com.chefotech.jadibuti.data.repo.UploadKind
import com.chefotech.jadibuti.ui.auth.stringRes
import com.chefotech.jadibuti.ui.components.AppCard
import com.chefotech.jadibuti.ui.components.AppTopBar
import com.chefotech.jadibuti.ui.components.BigButton
import com.chefotech.jadibuti.ui.components.BigOutlinedButton
import com.chefotech.jadibuti.ui.components.ChoiceChips
import com.chefotech.jadibuti.ui.components.ErrorText
import com.chefotech.jadibuti.ui.components.InfoBanner
import com.chefotech.jadibuti.ui.components.LoadingBox
import com.chefotech.jadibuti.ui.components.Tone
import com.chefotech.jadibuti.ui.format.typeLabel
import com.chefotech.jadibuti.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

// ------------------------------------------------------------------ capture

data class ScanState(val image: File? = null, val busy: Boolean = false, val error: String? = null, val uploadedId: String? = null, val notConfigured: Boolean = false)

@HiltViewModel
class MedicineScanViewModel @Inject constructor(private val uploads: PrescriptionRepository) : ViewModel() {
    val state = MutableStateFlow(ScanState())

    fun setImage(f: File?) { state.value = state.value.copy(image = f, error = null) }

    /** Upload the packaging photo, then read it. A 503 means no AI key on the server: keep the photo, offer manual entry. */
    fun uploadAndExtract(memberId: String?) {
        val s = state.value
        val file = s.image ?: run { state.value = s.copy(error = "Take or choose a photo first"); return }
        state.value = s.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val uploaded = uploads.upload(UploadKind.MEDICINE_PHOTO, file, "image/jpeg", memberId)
                try {
                    uploads.extract(UploadKind.MEDICINE_PHOTO, uploaded.id)
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
fun MedicineScanScreen(nav: NavHostController, memberId: String?, vm: MedicineScanViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state.uploadedId, state.notConfigured) {
        val id = state.uploadedId
        if (id != null && !state.notConfigured) nav.navigate(Routes.medicineScanReview(id, memberId)) { popUpTo(Routes.MEDICINE_SCAN) { inclusive = true } }
    }
    Scaffold(topBar = { AppTopBar("Scan medicine", subtitle = "Photo of the strip, box or bottle", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How it works", style = MaterialTheme.typography.titleMedium)
                    Text("1. Take a clear, well-lit photo of the medicine packaging.\n2. Jadi-Buti reads the name, strength, form and composition.\n3. You check and correct every field before anything is saved.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            state.image?.let { AsyncImage(model = it, contentDescription = "Medicine photo", modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit) }
            ImagePickerButtons(onImage = vm::setImage)
            ErrorText(state.error)
            if (state.notConfigured) {
                InfoBanner("AI reading is not enabled on this server. Your photo was saved securely; please enter the medicine details by hand.", Tone.AMBER, icon = Icons.Default.Warning)
                BigButton("Enter details manually", onClick = { nav.navigate(Routes.medicineEdit(memberId = memberId)) { popUpTo(Routes.MEDICINE_SCAN) { inclusive = true } } })
            } else {
                BigButton(if (state.busy) "Reading packaging…" else "Read this medicine", icon = Icons.Default.DocumentScanner, enabled = !state.busy && state.image != null, onClick = { vm.uploadAndExtract(memberId) })
            }
            Text(stringRes(R.string.safety_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ review

data class MedicineDraftFields(
    val key: String = UUID.randomUUID().toString(),
    val name: String = "",
    val strength: String = "",
    val form: String = "TABLET",
    val formKnown: Boolean = false,
    val composition: String = "",
    val rawText: String = "",
    val uncertain: Set<String> = emptySet(),
)

data class ScanReviewState(val upload: PrescriptionDto? = null, val image: File? = null, val items: List<MedicineDraftFields> = emptyList(), val selected: Int = 0, val busy: Boolean = true, val error: String? = null, val notes: String = "")

@HiltViewModel
class MedicineScanReviewViewModel @Inject constructor(private val uploads: PrescriptionRepository) : ViewModel() {
    val state = MutableStateFlow(ScanReviewState())

    fun load(id: String) = viewModelScope.launch {
        state.value = state.value.copy(busy = true)
        try {
            val p = uploads.get(UploadKind.MEDICINE_PHOTO, id)
            val image = runCatching { uploads.cachedImage(UploadKind.MEDICINE_PHOTO, id) }.getOrNull()
            val items = uploads.medicinePhotoItems(p).map(::toDraft).ifEmpty { listOf(MedicineDraftFields(uncertain = setOf("name", "strength", "form", "composition"))) }
            state.value = state.value.copy(upload = p, image = image, items = items, busy = false, notes = p.extraction.notes)
        } catch (e: Exception) { state.value = state.value.copy(busy = false, error = e.message) }
    }

    fun select(i: Int) { state.value = state.value.copy(selected = i) }
    fun update(transform: (MedicineDraftFields) -> MedicineDraftFields) {
        val s = state.value
        state.value = s.copy(items = s.items.mapIndexed { i, d -> if (i == s.selected) transform(d) else d })
    }

    /** Explicit confirmation of the transcribed identity; the schedule is configured next. */
    fun validate(): String? {
        val d = state.value.items.getOrNull(state.value.selected) ?: return "Nothing to confirm"
        if (d.name.isBlank()) return "Please enter the medicine name"
        return null
    }

    fun reject() = viewModelScope.launch { state.value.upload?.let { runCatching { uploads.delete(UploadKind.MEDICINE_PHOTO, it.id) } } }

    private fun toDraft(x: ExtractedMedicinePhotoDto): MedicineDraftFields {
        val uncertain = HashSet<String>()
        fun <T> f(name: String, field: com.chefotech.jadibuti.data.remote.ExtractedField<T>): T? { if (field.uncertain) uncertain += name; return field.value }
        val form = f("form", x.form)
        return MedicineDraftFields(
            name = f("name", x.name) ?: "", strength = f("strength", x.strength) ?: "", form = form ?: "TABLET", formKnown = form != null,
            composition = f("composition", x.composition) ?: "", rawText = x.rawText, uncertain = uncertain,
        )
    }
}

@Composable
fun MedicineScanReviewScreen(nav: NavHostController, id: String, memberId: String?, vm: MedicineScanReviewViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var fullImage by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) { vm.load(id) }
    val warn = "Unable to confidently read this field. Please verify."
    val d = state.items.getOrNull(state.selected)

    Scaffold(topBar = { AppTopBar("Check what was read", subtitle = "Step 1 of 2 · Medicine details", onBack = { nav.popBackStack() }) }) { padding ->
        if (state.busy && state.upload == null) { LoadingBox(Modifier.padding(padding)); return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoBanner("DRAFT read from your photo. Compare each field with the packaging and correct anything wrong. Nothing is saved yet.", Tone.AMBER, icon = Icons.Default.Warning)
            state.image?.let { AsyncImage(model = it, contentDescription = "Original photo", modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).clickable { fullImage = true }, contentScale = ContentScale.Fit) }
            if (state.notes.isNotBlank()) Text("Note: ${state.notes}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.items.size > 1) {
                AppCard {
                    Column {
                        Text("More than one medicine was found. Which one do you want to add?", style = MaterialTheme.typography.titleMedium)
                        state.items.forEachIndexed { i, item ->
                            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().clickable { vm.select(i) }.padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                RadioButton(selected = i == state.selected, onClick = { vm.select(i) })
                                Text(item.name.ifBlank { "Unnamed medicine ${i + 1}" } + if (item.strength.isNotBlank()) " ${item.strength}" else "", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            if (d != null) AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (d.rawText.isNotBlank()) Text("Read as: “${d.rawText}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(d.name, { v -> vm.update { it.copy(name = v) } }, label = { Text("Medicine name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if ("name" in d.uncertain) Text(warn, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(d.strength, { v -> vm.update { it.copy(strength = v) } }, label = { Text("Strength (e.g. 40 mg)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if ("strength" in d.uncertain) Text(warn, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Text("Form", style = MaterialTheme.typography.labelMedium)
                    ChoiceChips(listOf("TABLET", "CAPSULE", "SYRUP", "LIQUID", "DROPS", "INJECTION", "OTHER").map { it to typeLabel(it) }, d.form) { t -> vm.update { it.copy(form = t, formKnown = true) } }
                    if ("form" in d.uncertain || !d.formKnown) Text(if (!d.formKnown) "Form was not printed clearly. Please choose it." else warn, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(d.composition, { v -> vm.update { it.copy(composition = v) } }, label = { Text("Composition / active ingredient") }, modifier = Modifier.fillMaxWidth())
                    if ("composition" in d.uncertain) Text(warn, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            ErrorText(error ?: state.error)
            Text(stringRes(R.string.verify_prescription), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigButton("THIS IS CORRECT — SET UP DOSES", onClick = {
                val v = vm.validate()
                if (v != null) { error = v; return@BigButton }
                val dd = state.items[state.selected]
                nav.navigate(Routes.medicineEdit(memberId = memberId, prefill = Routes.Prefill(dd.name, dd.strength, dd.form, dd.composition, state.upload?.id))) { popUpTo(Routes.MEDICINE_SCAN_REVIEW) { inclusive = true } }
            })
            BigOutlinedButton("Discard and enter manually", onClick = { vm.reject(); nav.navigate(Routes.medicineEdit(memberId = memberId)) { popUpTo(Routes.MEDICINE_SCAN_REVIEW) { inclusive = true } } })
            TextButton(onClick = { vm.reject(); nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (fullImage && state.image != null) AlertDialog(onDismissRequest = { fullImage = false }, confirmButton = { TextButton(onClick = { fullImage = false }) { Text("Close") } }, text = { AsyncImage(model = state.image, contentDescription = null, modifier = Modifier.fillMaxWidth().height(520.dp), contentScale = ContentScale.Fit) })
}
