package com.chefotech.jadibuti.ui.family

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.prefs.SettingsStore
import com.chefotech.jadibuti.data.remote.InviteDto
import com.chefotech.jadibuti.data.remote.MembershipDto
import com.chefotech.jadibuti.data.remote.MembershipPatch
import com.chefotech.jadibuti.data.repo.FamilyRepository
import com.chefotech.jadibuti.ui.components.AppCard
import com.chefotech.jadibuti.ui.components.AppTopBar
import com.chefotech.jadibuti.ui.components.BigButton
import com.chefotech.jadibuti.ui.components.ErrorText
import com.chefotech.jadibuti.ui.components.StatusChip
import com.chefotech.jadibuti.ui.components.Tone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CaregiversState(val members: List<MembershipDto> = emptyList(), val invite: InviteDto? = null, val error: String? = null, val loading: Boolean = true, val caregiverAlerts: Boolean = false)

@HiltViewModel
class CaregiversViewModel @Inject constructor(private val families: FamilyRepository, val session: SessionStore, private val settings: SettingsStore) : ViewModel() {
    val state = MutableStateFlow(CaregiversState())
    private val familyId get() = session.current.activeFamilyId!!

    fun load() = viewModelScope.launch {
        state.value = state.value.copy(loading = true, error = null, caregiverAlerts = settings.current().caregiverAlerts)
        runCatching { families.memberships(familyId) }
            .onSuccess { state.value = state.value.copy(members = it, loading = false) }
            .onFailure { state.value = state.value.copy(error = it.message, loading = false) }
    }

    fun invite(canEdit: Boolean) = viewModelScope.launch {
        runCatching { families.createInvite(familyId, canEdit) }.onSuccess { state.value = state.value.copy(invite = it) }.onFailure { state.value = state.value.copy(error = it.message) }
    }

    fun setCanEdit(userId: String, canEdit: Boolean) = viewModelScope.launch {
        runCatching { families.updateMembership(familyId, userId, MembershipPatch(canEdit = canEdit)) }.onSuccess { load() }.onFailure { state.value = state.value.copy(error = it.message) }
    }

    fun remove(userId: String) = viewModelScope.launch {
        runCatching { families.removeMembership(familyId, userId) }.onSuccess {
            if (userId == session.current.userId) session.setActiveFamily(null, null, null, true) else load()
        }.onFailure { state.value = state.value.copy(error = it.message) }
    }

    fun setCaregiverAlerts(enabled: Boolean) = viewModelScope.launch {
        settings.update { it.copy(caregiverAlerts = enabled) }
        runCatching { families.updateMembership(familyId, session.current.userId!!, MembershipPatch(receiveMissedDoseAlerts = enabled)) }
        state.value = state.value.copy(caregiverAlerts = enabled)
    }
}

@Composable
fun CaregiversScreen(nav: NavHostController, vm: CaregiversViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val me = vm.session.current
    LaunchedEffect(Unit) { vm.load() }

    Scaffold(topBar = { AppTopBar("Caregivers & sharing", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Everyone below can see this family's medicines and mark doses.", style = MaterialTheme.typography.bodyLarge)
            ErrorText(state.error)
            state.members.forEach { m ->
                AppCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text((m.user?.name ?: "Member") + if (m.userId == me.userId) " (you)" else "", style = MaterialTheme.typography.titleMedium)
                                Text(m.user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusChip(if (m.role == "OWNER") "Owner" else if (m.permissions.canEdit) "Caregiver" else "View only", if (m.role == "OWNER") Tone.GREEN else Tone.BLUE)
                        }
                        if (me.isOwner && m.role != "OWNER") {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Can add and edit medicines", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(m.permissions.canEdit, { vm.setCanEdit(m.userId, it) })
                            }
                            TextButton(onClick = { vm.remove(m.userId) }) { Text("Remove access", color = MaterialTheme.colorScheme.error) }
                        } else if (m.userId == me.userId && m.role != "OWNER") {
                            TextButton(onClick = { vm.remove(m.userId) }) { Text("Leave this family", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Alert me about missed doses", style = MaterialTheme.typography.titleMedium)
                        Text("On this phone, show a notification when a family member's dose is marked missed.", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(state.caregiverAlerts, { vm.setCaregiverAlerts(it) })
                }
            }
            if (me.isOwner) {
                AppCard {
                    Column {
                        Text("Invite a caregiver", style = MaterialTheme.typography.titleLarge)
                        Text("Create a one-time code. The caregiver enters it in Jadi-Buti under \"Join family\". Codes expire in 3 days.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        val inv = state.invite
                        if (inv != null) {
                            Text(inv.code, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            TextButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Join our family on Jadi-Buti with invite code ${inv.code} (valid 3 days).")
                                context.startActivity(Intent.createChooser(send, "Share invite code"))
                            }, modifier = Modifier.fillMaxWidth()) { Text("Share code") }
                        }
                        BigButton(if (inv == null) "Create invite code" else "Create another code", onClick = { vm.invite(true) })
                        TextButton(onClick = { vm.invite(false) }, modifier = Modifier.fillMaxWidth()) { Text("Create view-only code") }
                    }
                }
            }
        }
    }
}
