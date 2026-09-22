package com.chefotech.jadibuti.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chefotech.jadibuti.data.prefs.SessionSnapshot
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.ui.auth.AuthScreen
import com.chefotech.jadibuti.ui.family.CaregiversScreen
import com.chefotech.jadibuti.ui.family.FamilyScreen
import com.chefotech.jadibuti.ui.family.FamilySetupScreen
import com.chefotech.jadibuti.ui.family.MemberEditScreen
import com.chefotech.jadibuti.ui.history.HistoryScreen
import com.chefotech.jadibuti.ui.home.HomeScreen
import com.chefotech.jadibuti.ui.inventory.InventoryScreen
import com.chefotech.jadibuti.ui.medicines.MedicineDetailScreen
import com.chefotech.jadibuti.ui.medicines.MedicineEditScreen
import com.chefotech.jadibuti.ui.medicines.MedicinesScreen
import com.chefotech.jadibuti.ui.more.MoreScreen
import com.chefotech.jadibuti.ui.prescription.PrescriptionCaptureScreen
import com.chefotech.jadibuti.ui.prescription.PrescriptionReviewScreen
import com.chefotech.jadibuti.ui.scan.MedicineScanReviewScreen
import com.chefotech.jadibuti.ui.scan.MedicineScanScreen
import com.chefotech.jadibuti.ui.settings.PermissionsHelpScreen
import com.chefotech.jadibuti.ui.settings.ReminderSettingsScreen
import com.chefotech.jadibuti.ui.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

object Routes {
    const val AUTH = "auth"
    const val FAMILY_SETUP = "family_setup"
    const val HOME = "home"
    const val FAMILY = "family"
    const val MEDICINES = "medicines"
    const val HISTORY = "history"
    const val MORE = "more"
    const val MEMBER_EDIT = "member_edit?id={id}"
    const val MEDICINE_EDIT = "medicine_edit?id={id}&memberId={memberId}&name={name}&strength={strength}&type={type}&composition={composition}&scanId={scanId}"
    const val MEDICINE_DETAIL = "medicine/{id}"
    const val MEDICINE_SCAN = "medicine_scan?memberId={memberId}"
    const val MEDICINE_SCAN_REVIEW = "medicine_scan_review/{id}?memberId={memberId}"
    const val CAREGIVERS = "caregivers"
    const val INVENTORY = "inventory"
    const val SETTINGS = "settings"
    const val REMINDER_SETTINGS = "reminder_settings"
    const val PERMISSIONS_HELP = "permissions_help"
    const val PRESCRIPTION_CAPTURE = "prescription_capture?memberId={memberId}"
    const val PRESCRIPTION_REVIEW = "prescription_review/{id}?memberId={memberId}"

    /** Values carried from the scan review into the medicine form. */
    data class Prefill(val name: String, val strength: String, val type: String, val composition: String, val scanId: String?)

    private fun enc(s: String?) = Uri.encode(s ?: "")
    fun memberEdit(id: String? = null) = "member_edit?id=${enc(id)}"
    fun medicineEdit(id: String? = null, memberId: String? = null, prefill: Prefill? = null) =
        "medicine_edit?id=${enc(id)}&memberId=${enc(memberId)}&name=${enc(prefill?.name)}&strength=${enc(prefill?.strength)}&type=${enc(prefill?.type)}&composition=${enc(prefill?.composition)}&scanId=${enc(prefill?.scanId)}"
    fun medicineDetail(id: String) = "medicine/$id"
    fun medicineScan(memberId: String? = null) = "medicine_scan?memberId=${enc(memberId)}"
    fun medicineScanReview(id: String, memberId: String?) = "medicine_scan_review/$id?memberId=${enc(memberId)}"
    fun prescriptionCapture(memberId: String? = null) = "prescription_capture?memberId=${enc(memberId)}"
    fun prescriptionReview(id: String, memberId: String?) = "prescription_review/$id?memberId=${enc(memberId)}"
}

@HiltViewModel
class SessionViewModel @Inject constructor(session: SessionStore) : ViewModel() {
    val snapshot = session.snapshot
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Default.Home),
    Tab(Routes.FAMILY, "Family", Icons.Default.Groups),
    Tab(Routes.MEDICINES, "Medicines", Icons.Default.Medication),
    Tab(Routes.HISTORY, "History", Icons.Default.History),
    Tab(Routes.MORE, "More", Icons.Default.MoreHoriz),
)

private fun optString(name: String) = navArgument(name) { type = NavType.StringType; defaultValue = "" }
private fun androidx.navigation.NavBackStackEntry.opt(name: String): String? = arguments?.getString(name)?.takeIf { it.isNotBlank() }

@Composable
fun AppNavHost(launchEventId: String?, openStock: Boolean, onLaunchHandled: () -> Unit, sessionVm: SessionViewModel = hiltViewModel()) {
    val session by sessionVm.snapshot.collectAsState()
    val nav = rememberNavController()
    val start = startDestination(session)

    // React to login/logout/family selection by moving to the right root.
    LaunchedEffect(start) {
        val current = nav.currentBackStackEntry?.destination?.route
        if (current != null && current != start && (start == Routes.AUTH || start == Routes.FAMILY_SETUP || current == Routes.AUTH || current == Routes.FAMILY_SETUP)) {
            nav.navigate(start) { popUpTo(0) { inclusive = true } }
        }
    }
    LaunchedEffect(launchEventId, openStock, session.hasFamily) {
        if (session.isLoggedIn && session.hasFamily) {
            if (openStock) { nav.navigate(Routes.INVENTORY); onLaunchHandled() } else if (launchEventId != null) { nav.navigate(Routes.HOME) { launchSingleTop = true }; onLaunchHandled() }
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showTabs = tabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showTabs) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { nav.navigate(tab.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, indicatorColor = com.chefotech.jadibuti.ui.theme.HeaderGreen, selectedTextColor = MaterialTheme.colorScheme.primary),
                    )
                }
            }
        },
    ) { padding ->
        // Only consume the bottom (navigation bar) inset here; each screen's own top bar handles the status bar.
        NavHost(nav, startDestination = start, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Routes.AUTH) { AuthScreen() }
            composable(Routes.FAMILY_SETUP) { FamilySetupScreen() }
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.FAMILY) { FamilyScreen(nav) }
            composable(Routes.MEDICINES) { MedicinesScreen(nav) }
            composable(Routes.HISTORY) { HistoryScreen(nav) }
            composable(Routes.MORE) { MoreScreen(nav) }
            composable(Routes.MEMBER_EDIT, arguments = listOf(optString("id"))) { MemberEditScreen(nav, it.opt("id")) }
            composable(
                Routes.MEDICINE_EDIT,
                arguments = listOf(optString("id"), optString("memberId"), optString("name"), optString("strength"), optString("type"), optString("composition"), optString("scanId")),
            ) {
                val prefill = it.opt("name")?.let { n -> Routes.Prefill(n, it.opt("strength") ?: "", it.opt("type") ?: "TABLET", it.opt("composition") ?: "", it.opt("scanId")) }
                MedicineEditScreen(nav, it.opt("id"), it.opt("memberId"), prefill)
            }
            composable(Routes.MEDICINE_DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { MedicineDetailScreen(nav, it.arguments!!.getString("id")!!) }
            composable(Routes.MEDICINE_SCAN, arguments = listOf(optString("memberId"))) { MedicineScanScreen(nav, it.opt("memberId")) }
            composable(Routes.MEDICINE_SCAN_REVIEW, arguments = listOf(navArgument("id") { type = NavType.StringType }, optString("memberId"))) { MedicineScanReviewScreen(nav, it.arguments!!.getString("id")!!, it.opt("memberId")) }
            composable(Routes.CAREGIVERS) { CaregiversScreen(nav) }
            composable(Routes.INVENTORY) { InventoryScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.REMINDER_SETTINGS) { ReminderSettingsScreen(nav) }
            composable(Routes.PERMISSIONS_HELP) { PermissionsHelpScreen(nav) }
            composable(Routes.PRESCRIPTION_CAPTURE, arguments = listOf(optString("memberId"))) { PrescriptionCaptureScreen(nav, it.opt("memberId")) }
            composable(Routes.PRESCRIPTION_REVIEW, arguments = listOf(navArgument("id") { type = NavType.StringType }, optString("memberId"))) { PrescriptionReviewScreen(nav, it.arguments!!.getString("id")!!, it.opt("memberId")) }
        }
    }
}

private fun startDestination(s: SessionSnapshot): String = when {
    !s.isLoggedIn -> Routes.AUTH
    !s.hasFamily -> Routes.FAMILY_SETUP
    else -> Routes.HOME
}
