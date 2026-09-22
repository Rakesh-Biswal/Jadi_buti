package com.chefotech.jadibuti.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chefotech.jadibuti.R
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.repo.AuthRepository
import com.chefotech.jadibuti.ui.components.BigButton
import com.chefotech.jadibuti.ui.components.ErrorText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(val loading: Boolean = false, val error: String? = null, val registerMode: Boolean = false)

@HiltViewModel
class AuthViewModel @Inject constructor(private val auth: AuthRepository, private val session: SessionStore) : ViewModel() {
    val state = MutableStateFlow(AuthState())
    val serverUrl get() = session.apiBaseUrl

    fun toggleMode() { state.value = state.value.copy(registerMode = !state.value.registerMode, error = null) }

    fun submit(name: String, email: String, password: String) {
        if (email.isBlank() || password.isBlank() || (state.value.registerMode && name.isBlank())) {
            state.value = state.value.copy(error = "Please fill in all fields"); return
        }
        if (state.value.registerMode && password.length < 8) { state.value = state.value.copy(error = "Password must be at least 8 characters"); return }
        state.value = state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { if (state.value.registerMode) auth.register(name, email, password) else auth.login(email, password) }
                .onFailure { state.value = state.value.copy(loading = false, error = it.message ?: "Could not sign in") }
                .onSuccess { state.value = state.value.copy(loading = false) }
        }
    }

    fun setServer(url: String) { session.setApiBaseUrl(url) }
}

@Composable
fun AuthScreen(vm: AuthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showServer by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Image(painterResource(R.drawable.ic_splash_logo), contentDescription = null, modifier = Modifier.size(120.dp))
        Text("Jadi-Buti", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(stringRes(R.string.tagline), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Text(if (state.registerMode) "Create your account" else "Sign in", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        if (state.registerMode) {
            OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        ErrorText(state.error)
        Spacer(Modifier.height(16.dp))
        BigButton(if (state.loading) "Please wait…" else if (state.registerMode) "Create account" else "Sign in", enabled = !state.loading, onClick = { vm.submit(name, email, password) })
        TextButton(onClick = vm::toggleMode, modifier = Modifier.padding(top = 8.dp)) {
            Text(if (state.registerMode) "Already have an account? Sign in" else "New here? Create an account", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(24.dp))
        Text(stringRes(R.string.safety_notice), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { showServer = true }) { Text("Server: ${vm.serverUrl}", style = MaterialTheme.typography.bodySmall) }
    }
    if (showServer) ServerDialog(vm.serverUrl, onDismiss = { showServer = false }, onSave = { vm.setServer(it); showServer = false })
}

@Composable
fun ServerDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var url by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server address") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Only change this if your family administrator gave you a different Jadi-Buti server.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("https://…/api/v1/") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { if (url.startsWith("http")) onSave(url) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)
