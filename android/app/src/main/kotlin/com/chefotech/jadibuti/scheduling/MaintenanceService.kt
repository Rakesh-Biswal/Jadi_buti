package com.chefotech.jadibuti.scheduling

import com.chefotech.jadibuti.data.inventorySettings
import com.chefotech.jadibuti.data.local.EventDao
import com.chefotech.jadibuti.data.local.MedicineDao
import com.chefotech.jadibuti.data.local.MemberDao
import com.chefotech.jadibuti.data.local.TransactionDao
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.prefs.SettingsStore
import com.chefotech.jadibuti.data.repo.EventRepository
import com.chefotech.jadibuti.data.toLedger
import com.chefotech.jadibuti.data.toSchedule
import com.chefotech.jadibuti.domain.InventoryMath
import com.chefotech.jadibuti.reminders.ReminderNotifier
import com.chefotech.jadibuti.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Housekeeping that must be correct regardless of how the app was (re)started:
 * event window, overdue -> MISSED, low-stock alerts, caregiver alerts, alarm rescheduling.
 * Runs on app foreground, after each sync, from the periodic worker and after boot/time changes.
 */
@Singleton
class MaintenanceService @Inject constructor(
    private val generator: EventGenerator,
    private val events: EventDao,
    private val medicines: MedicineDao,
    private val members: MemberDao,
    private val transactions: TransactionDao,
    private val eventRepo: EventRepository,
    private val settings: SettingsStore,
    private val session: SessionStore,
    private val notifier: ReminderNotifier,
    private val reminders: ReminderScheduler,
    private val syncScheduler: com.chefotech.jadibuti.sync.SyncScheduler,
) {
    suspend fun runAll(recomputeZone: Boolean = false) {
        val familyId = session.current.activeFamilyId ?: return
        if (recomputeZone) generator.recomputeZone()
        generator.ensureWindow(familyId)
        markOverdueMissed()
        checkLowStock(familyId)
        checkCaregiverAlerts(familyId)
        reminders.rescheduleAll()
    }

    private suspend fun markOverdueMissed() {
        val s = settings.current()
        val now = System.currentTimeMillis()
        val cutoff = now - s.missedAfterMinutes * 60_000L
        var changed = false
        for (e in events.pendingBetweenAllFamilies(now - 30L * 24 * 3600_000, cutoff)) {
            if (e.snoozedUntil != null && e.snoozedUntil > now) continue
            eventRepo.markMissed(e.id)
            notifier.cancel(e.id)
            changed = true
        }
        if (changed) syncScheduler.requestSync()
    }

    /** One alert per low-stock episode; cleared automatically when stock recovers. */
    suspend fun checkLowStock(familyId: String) {
        val txs = transactions.listFamily(familyId).groupBy { it.medicineId }
        for (m in medicines.listActiveFamily(familyId)) {
            if (!m.active || !m.trackInventory) continue
            val ledger = (txs[m.id] ?: emptyList()).map { it.toLedger() }
            val summary = InventoryMath.summary(m.toSchedule(), m.inventorySettings(), ledger)
            if (summary.lowStock && m.lowStockAlertedAt == null) {
                val memberName = members.get(m.memberId)?.name ?: ""
                notifier.showLowStock(m, memberName, summary)
                medicines.setLowStockAlertedAt(m.id, System.currentTimeMillis())
            } else if (!summary.lowStock && m.lowStockAlertedAt != null) {
                medicines.setLowStockAlertedAt(m.id, null)
            }
        }
    }

    /** Local caregiver escalation: surface doses another person missed (opt-in, once per dose). */
    private suspend fun checkCaregiverAlerts(familyId: String) {
        val s = settings.current()
        if (!s.caregiverAlerts) return
        val now = System.currentTimeMillis()
        val since = now - 24L * 3600_000
        val today = java.time.LocalDate.now()
        val snapshot = events.observeRange(familyId, today.minusDays(1).toString(), today.toString(), null, null).first()
        for (row in snapshot) {
            val e = row.event
            if (e.status == "MISSED" && e.caregiverAlertedAt == null && e.scheduledAt >= since) {
                notifier.showCaregiverAlert(row)
                events.setCaregiverAlertedAt(e.id, now)
            }
        }
    }
}
