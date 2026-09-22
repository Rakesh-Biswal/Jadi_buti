package com.chefotech.jadibuti.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.chefotech.jadibuti.R
import com.chefotech.jadibuti.ui.auth.stringRes
import com.chefotech.jadibuti.ui.components.AppCard
import com.chefotech.jadibuti.ui.components.AppTopBar
import com.chefotech.jadibuti.ui.components.IconBadge
import com.chefotech.jadibuti.ui.components.SectionHeader
import com.chefotech.jadibuti.ui.navigation.Routes

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    AppCard(modifier = Modifier.clickable(onClick = onClick), padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MoreScreen(nav: NavHostController) {
    Scaffold(topBar = { AppTopBar("More") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("Medicines")
            MenuRow(Icons.Default.Inventory, "Medicine stock", "Remaining quantities, refills and corrections") { nav.navigate(Routes.INVENTORY) }
            MenuRow(Icons.Default.DocumentScanner, "Scan a medicine", "Read name and strength from the packaging") { nav.navigate(Routes.medicineScan()) }
            MenuRow(Icons.Default.Receipt, "Scan a prescription", "Read medicines and doses from a prescription") { nav.navigate(Routes.prescriptionCapture()) }
            SectionHeader("Family")
            MenuRow(Icons.Default.Groups, "Caregivers & sharing", "Invite family, manage who can edit") { nav.navigate(Routes.CAREGIVERS) }
            SectionHeader("Reminders & settings")
            MenuRow(Icons.Default.MusicNote, "Alarm sound & vibration", "Choose tones, vibration and snooze") { nav.navigate(Routes.REMINDER_SETTINGS) }
            MenuRow(Icons.AutoMirrored.Filled.HelpOutline, "Permissions & battery help", "Make sure alarms can ring reliably") { nav.navigate(Routes.PERMISSIONS_HELP) }
            MenuRow(Icons.Default.Settings, "Settings & account", "Profile, family, server, sign out") { nav.navigate(Routes.SETTINGS) }
            Text(stringRes(R.string.safety_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
