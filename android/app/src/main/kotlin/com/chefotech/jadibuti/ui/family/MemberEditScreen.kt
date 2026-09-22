package com.chefotech.jadibuti.ui.family

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.chefotech.jadibuti.data.local.MemberEntity
import com.chefotech.jadibuti.data.repo.MemberRepository
import com.chefotech.jadibuti.ui.components.AppTopBar
import com.chefotech.jadibuti.ui.components.BigButton
import com.chefotech.jadibuti.ui.components.BigOutlinedButton
import com.chefotech.jadibuti.ui.components.DatePickerField
import com.chefotech.jadibuti.ui.components.ErrorText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberEditViewModel @Inject constructor(private val members: MemberRepository) : ViewModel() {
    val existing = MutableStateFlow<MemberEntity?>(null)
    val error = MutableStateFlow<String?>(null)
    val done = MutableStateFlow(false)

    fun load(id: String?) = viewModelScope.launch { existing.value = id?.let { members.get(it) } }

    fun save(id: String?, name: String, dob: String?, notes: String, active: Boolean) {
        if (name.isBlank()) { error.value = "Please enter a name"; return }
        viewModelScope.launch {
            runCatching { members.save(id, name, dob, notes, active) }.onFailure { error.value = it.message }.onSuccess { done.value = true }
        }
    }

    fun delete(id: String) = viewModelScope.launch { members.delete(id); done.value = true }
}

@Composable
fun MemberEditScreen(nav: NavHostController, id: String?, vm: MemberEditViewModel = hiltViewModel()) {
    val existing by vm.existing.collectAsState()
    val error by vm.error.collectAsState()
    val done by vm.done.collectAsState()
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    var loadedInto by remember { mutableStateOf(false) }

    LaunchedEffect(id) { vm.load(id) }
    LaunchedEffect(existing) { existing?.let { if (!loadedInto) { name = it.name; dob = it.dateOfBirth; notes = it.notes; active = it.active; loadedInto = true } } }
    LaunchedEffect(done) { if (done) nav.popBackStack() }

    Scaffold(topBar = { AppTopBar(if (id == null) "Add family member" else "Edit family member", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Father)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            DatePickerField(label = "Date of birth (optional)", value = dob, onChange = { dob = it }, allowClear = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            if (id != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Active (receives reminders)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(active, { active = it })
                }
            }
            ErrorText(error)
            Spacer(Modifier.height(20.dp))
            BigButton("Save", onClick = { vm.save(id, name, dob, notes, active) })
            if (id != null) {
                Spacer(Modifier.height(12.dp))
                BigOutlinedButton("Remove member", onClick = { confirmDelete = true })
            }
            Spacer(Modifier.height(12.dp))
            Text("Only the information needed for reminders is stored.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmDelete && id != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Remove ${name.ifBlank { "member" }}?") },
        text = { Text("Their medicines will stop generating reminders. Medication history is kept.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(id) }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
