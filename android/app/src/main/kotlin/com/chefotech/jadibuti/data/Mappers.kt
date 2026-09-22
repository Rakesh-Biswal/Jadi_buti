package com.chefotech.jadibuti.data

import com.chefotech.jadibuti.data.local.EventEntity
import com.chefotech.jadibuti.data.local.MedicineEntity
import com.chefotech.jadibuti.data.local.MemberEntity
import com.chefotech.jadibuti.data.local.TransactionEntity
import com.chefotech.jadibuti.data.remote.DoseTimeDto
import com.chefotech.jadibuti.data.remote.EventDto
import com.chefotech.jadibuti.data.remote.InventoryDto
import com.chefotech.jadibuti.data.remote.MedicineDto
import com.chefotech.jadibuti.data.remote.MemberDto
import com.chefotech.jadibuti.data.remote.ScheduleDto
import com.chefotech.jadibuti.data.remote.StatusChangeDto
import com.chefotech.jadibuti.data.remote.TransactionDto
import com.chefotech.jadibuti.data.remote.json
import com.chefotech.jadibuti.domain.DoseTime
import com.chefotech.jadibuti.domain.Frequency
import com.chefotech.jadibuti.domain.InventorySettings
import com.chefotech.jadibuti.domain.LedgerEntry
import com.chefotech.jadibuti.domain.LowStockType
import com.chefotech.jadibuti.domain.MedicineSchedule
import com.chefotech.jadibuti.domain.TransactionType
import kotlinx.serialization.builtins.ListSerializer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val doseListSerializer = ListSerializer(DoseTimeDto.serializer())
private val historySerializer = ListSerializer(StatusChangeDto.serializer())

fun MemberDto.toEntity() = MemberEntity(id, familyId, name, dateOfBirth, notes, photoPath, active, createdAt, updatedAt, version, deleted)
fun MemberEntity.toDto() = MemberDto(id, familyId, name, dateOfBirth, notes, photoPath, active, createdAt, updatedAt, version, deleted)

fun MedicineDto.toEntity(lowStockAlertedAt: Long? = null) = MedicineEntity(
    id = id, familyId = familyId, memberId = memberId, name = name, strength = strength, type = type, doseUnit = doseUnit,
    foodInstruction = foodInstruction, instructions = instructions, startDate = startDate, endDate = endDate,
    frequency = schedule.frequency, weekdays = schedule.weekdays.joinToString(","), intervalDays = schedule.intervalDays,
    referenceDate = schedule.referenceDate, doseTimesJson = json.encodeToString(doseListSerializer, doseTimes),
    trackInventory = inventory.track, lowStockType = inventory.lowStockType, lowStockValue = inventory.lowStockValue,
    prescriptionId = prescriptionId, active = active, createdAt = createdAt, updatedAt = updatedAt, version = version, deleted = deleted,
    lowStockAlertedAt = lowStockAlertedAt,
)

fun MedicineEntity.doseTimes(): List<DoseTimeDto> = runCatching { json.decodeFromString(doseListSerializer, doseTimesJson) }.getOrDefault(emptyList())
fun MedicineEntity.weekdayList(): List<Int> = weekdays.split(',').mapNotNull { it.trim().toIntOrNull() }

fun MedicineEntity.toDto() = MedicineDto(
    id = id, familyId = familyId, memberId = memberId, name = name, strength = strength, type = type, doseUnit = doseUnit,
    foodInstruction = foodInstruction, instructions = instructions, startDate = startDate, endDate = endDate,
    schedule = ScheduleDto(frequency, weekdayList(), intervalDays, referenceDate), doseTimes = doseTimes(),
    inventory = InventoryDto(trackInventory, lowStockType, lowStockValue), prescriptionId = prescriptionId, active = active,
    createdAt = createdAt, updatedAt = updatedAt, version = version, deleted = deleted,
)

fun MedicineEntity.toSchedule() = MedicineSchedule(
    medicineId = id, memberId = memberId, startDate = LocalDate.parse(startDate), endDate = endDate?.let(LocalDate::parse),
    rule = com.chefotech.jadibuti.domain.ScheduleRule(
        frequency = runCatching { Frequency.valueOf(frequency) }.getOrDefault(Frequency.DAILY),
        weekdays = weekdayList().mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }.toSet(),
        intervalDays = intervalDays,
        referenceDate = referenceDate?.let(LocalDate::parse),
    ),
    doseTimes = doseTimes().map { DoseTime(LocalTime.parse(it.time), it.amount) },
    doseUnit = doseUnit,
    active = active && !deleted,
)

fun MedicineEntity.inventorySettings() = InventorySettings(
    track = trackInventory,
    lowStockType = runCatching { LowStockType.valueOf(lowStockType) }.getOrDefault(LowStockType.PERCENT),
    lowStockValue = lowStockValue,
)

fun EventDto.toEntity(local: EventEntity? = null) = EventEntity(
    id = id, familyId = familyId, memberId = memberId, medicineId = medicineId, localDate = localDate, time = time, zoneId = zoneId,
    scheduledAt = scheduledAt, doseAmount = doseAmount, doseUnit = doseUnit, status = status, actualAt = actualAt,
    recordedByUserId = recordedByUserId, snoozedUntil = snoozedUntil, note = note,
    statusHistoryJson = json.encodeToString(historySerializer, statusHistory), updatedAt = updatedAt, version = version, deleted = deleted,
    notifiedAt = local?.notifiedAt, followUpNotifiedAt = local?.followUpNotifiedAt, caregiverAlertedAt = local?.caregiverAlertedAt,
)

fun EventEntity.history(): List<StatusChangeDto> = runCatching { json.decodeFromString(historySerializer, statusHistoryJson) }.getOrDefault(emptyList())

fun EventEntity.toDto() = EventDto(
    id = id, familyId = familyId, memberId = memberId, medicineId = medicineId, localDate = localDate, time = time, zoneId = zoneId,
    scheduledAt = scheduledAt, doseAmount = doseAmount, doseUnit = doseUnit, status = status, actualAt = actualAt,
    recordedByUserId = recordedByUserId, snoozedUntil = snoozedUntil, note = note, statusHistory = history(),
    updatedAt = updatedAt, version = version, deleted = deleted,
)

fun encodeHistory(list: List<StatusChangeDto>): String = json.encodeToString(historySerializer, list)

fun TransactionDto.toEntity() = TransactionEntity(id, familyId, medicineId, type, quantityDelta, eventId, note, createdAt, byUserId, updatedAt, version, deleted)
fun TransactionEntity.toDto() = TransactionDto(id, familyId, medicineId, type, quantityDelta, eventId, note, createdAt, byUserId, updatedAt, version, deleted)
fun TransactionEntity.toLedger() = LedgerEntry(runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.CORRECTION), quantityDelta, createdAt, deleted)
