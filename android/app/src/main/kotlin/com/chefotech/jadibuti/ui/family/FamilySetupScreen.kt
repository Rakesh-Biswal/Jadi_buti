package com.chefotech.jadibuti.ui.family

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chefotech.jadibuti.data.remote.FamilyWithMembership
import com.chefotech.jadibuti.data.repo.AuthRepository
import com.chefotech.jadibuti.data.repo.FamilyRepository
import com.chefotech.jadibuti.sync.SyncScheduler
import com.chefotech.jadibuti.ui.components.AppCard
import com.chefotech.jadibuti.ui.components.BigButton
import com.chefotech.jadibuti.ui.components.BigOutlinedButton
import com.chefotech.jadibuti.ui.components.ErrorText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FamilySetupState(val loading: Boolean = false, val error: String? = null, val families: List<FamilyWithMembership> = emptyList())

@HiltViewModel
class FamilySetupViewModel @Inject constructor(
    private val families: FamilyRepository,
    private val auth: AuthRepository,
    private val sync: SyncScheduler,
) : ViewModel() {
    val state = MutableStateFlow(FamilySetupState())

    fun load() = viewModelScope.launch {
        runCatching { families.families() }.onSuccess { state.value = state.value.copy(families = it) }
    }

    fun create(name: String) = run("Give your family a name", name.isBlank()) { families.createFamily(name) }
    fun join(code: String) = run("Enter the invite code", code.isBlank()) { families.joinFamily(code) }
    fun select(f: FamilyWithMembership) { families.select(f); sync.requestSync() }
    fun logout() = viewModelScope.launch { auth.logout() }

    private fun run(validation: String, invalid: Boolean, block: suspend () -> Unit) {
        if (invalid) { state.value = state.value.copy(error = validation); return }
        state.value = state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { state.value = state.value.copy(loading = false, error = it.message) }
                .onSuccess { state.value = state.value.copy(loading = false); sync.requestSync() }
        }
    }
}

@Composable
fun FamilySetupScreen(vm: FamilySetupViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Set up your family", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text("Medicines, reminders and stock are shared with everyone in the family.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
        if (state.families.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Your families", style = MaterialTheme.typography.titleMedium)
            state.families.forEach { f ->
                Spacer(Modifier.height(8.dp))
                BigOutlinedButton("${f.family.name} (${if (f.membership.role == "OWNER") "owner" else "caregiver"})", onClick = { vm.select(f) })
            }
            HorizontalDivider(Modifier.padding(vertical = 20.dp))
        }
        AppCard {
            Column {
                Text("Create a new family", style = MaterialTheme.typography.titleLarge)
                Text("You will be the owner and can invite caregivers.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Family name, e.g. Patel Family") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Spacer(Modifier.height(12.dp))
                BigButton("Create family", enabled = !state.loading, onClick = { vm.create(name) })
            }
        }
        Spacer(Modifier.height(16.dp))
        AppCard {
            Column {
                Text("Join with an invite code", style = MaterialTheme.typography.titleLarge)
                Text("Ask the family owner for the 8-letter code from their Jadi-Buti app.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Invite code") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                Spacer(Modifier.height(12.dp))
                BigOutlinedButton("Join family", enabled = !state.loading, onClick = { vm.join(code) })
            }
        }
        ErrorText(state.error)
        TextButton(onClick = vm::logout, modifier = Modifier.padding(top = 16.dp)) { Text("Sign out") }
    }
}
