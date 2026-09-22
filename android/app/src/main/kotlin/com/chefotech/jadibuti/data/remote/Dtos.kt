package com.chefotech.jadibuti.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Wire types matching backend/app/api/v1. Field names are the JSON names. */

@Serializable
data class ApiErrorBody(val code: String = "error", val message: String = "Request failed", val details: JsonElement? = null)

@Serializable
data class ApiResponse<T>(val ok: Boolean, val data: T? = null, val error: ApiErrorBody? = null)

@Serializable
data class UserDto(val id: String, val email: String, val name: String, val createdAt: Long = 0)

@Serializable
data class AuthResponse(val user: UserDto, val accessToken: String, val refreshToken: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val name: String, val deviceName: String? = null)

@Serializable
data class LoginRequest(val email: String, val password: String, val deviceName: String? = null)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class FamilyDto(val id: String, val name: String, val ownerUserId: String, val createdAt: Long = 0, val updatedAt: Long = 0)

@Serializable
data class PermissionsDto(val canEdit: Boolean = true, val receiveMissedDoseAlerts: Boolean = false)

@Serializable
data class MembershipDto(
    val id: String,
    val familyId: String,
    val userId: String,
    val role: String,
    val status: String = "ACTIVE",
    val permissions: PermissionsDto = PermissionsDto(),
    val user: UserSummaryDto? = null,
)

@Serializable
data class UserSummaryDto(val id: String, val name: String, val email: String)

@Serializable
data class FamilyWithMembership(val family: FamilyDto, val membership: MembershipDto)

@Serializable
data class MeResponse(val user: UserDto, val families: List<FamilyWithMembership>)

@Serializable
data class CreateFamilyRequest(val name: String)

@Serializable
data class JoinFamilyRequest(val code: String)

@Serializable
data class CreateInviteRequest(val canEdit: Boolean = true, val expiresInHours: Int = 72)

@Serializable
data class InviteDto(val code: String, val familyId: String, val canEdit: Boolean = true, val expiresAt: Long)

@Serializable
data class MembershipPatch(val canEdit: Boolean? = null, val receiveMissedDoseAlerts: Boolean? = null)

@Serializable
data class MemberDto(
    val id: String,
    val familyId: String,
    val name: String,
    val dateOfBirth: String? = null,
    val notes: String = "",
    val photoPath: String? = null,
    val active: Boolean = true,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val version: Int = 0,
    val deleted: Boolean = false,
)

@Serializable
data class ScheduleDto(
    val frequency: String,
    val weekdays: List<Int> = emptyList(),
    val intervalDays: Int = 1,
    val referenceDate: String? = null,
)

@Serializable
data class DoseTimeDto(val time: String, val amount: Double, val meal: String? = null)

@Serializable
data class InventoryDto(val track: Boolean = true, val lowStockType: String = "PERCENT", val lowStockValue: Double = 5.0)

@Serializable
data class MedicineDto(
    val id: String,
    val familyId: String,
    val memberId: String,
    val name: String,
    val strength: String = "",
    val type: String = "TABLET",
    val doseUnit: String = "tablet",
    val foodInstruction: String = "NONE",
    val instructions: String = "",
    val startDate: String,
    val endDate: String? = null,
    val schedule: ScheduleDto,
    val doseTimes: List<DoseTimeDto>,
    val inventory: InventoryDto = InventoryDto(),
    val prescriptionId: String? = null,
    val active: Boolean = true,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val version: Int = 0,
    val deleted: Boolean = false,
)

@Serializable
data class StatusChangeDto(val status: String, val at: Long, val byUserId: String? = null, val actualAt: Long? = null)

@Serializable
data class EventDto(
    val id: String,
    val familyId: String,
    val memberId: String,
    val medicineId: String,
    val localDate: String,
    val time: String,
    val zoneId: String = "UTC",
    val scheduledAt: Long,
    val doseAmount: Double,
    val doseUnit: String = "tablet",
    val status: String,
    val actualAt: Long? = null,
    val recordedByUserId: String? = null,
    val snoozedUntil: Long? = null,
    val note: String = "",
    val statusHistory: List<StatusChangeDto> = emptyList(),
    val updatedAt: Long = 0,
    val version: Int = 0,
    val deleted: Boolean = false,
)

@Serializable
data class TransactionDto(
    val id: String,
    val familyId: String,
    val medicineId: String,
    val type: String,
    val quantityDelta: Double,
    val eventId: String? = null,
    val note: String = "",
    val createdAt: Long = 0,
    val byUserId: String? = null,
    val updatedAt: Long = 0,
    val version: Int = 0,
    val deleted: Boolean = false,
)

@Serializable
data class SyncChange(val entity: String, val payload: JsonObject)

@Serializable
data class SyncPushRequest(val familyId: String, val changes: List<SyncChange>)

@Serializable
data class SyncPushResult(val entity: String, val id: String? = null, val ok: Boolean, val applied: Boolean = false, val doc: JsonObject? = null, val error: String? = null)

@Serializable
data class SyncPushResponse(val results: List<SyncPushResult>, val serverTime: Long)

@Serializable
data class SyncPullResponse(
    val cursor: Long,
    val hasMore: Boolean = false,
    val members: List<MemberDto> = emptyList(),
    val medicines: List<MedicineDto> = emptyList(),
    val events: List<EventDto> = emptyList(),
    val transactions: List<TransactionDto> = emptyList(),
    val serverTime: Long = 0,
)

@Serializable
data class ExtractedField<T>(val value: T? = null, val uncertain: Boolean = true)

@Serializable
data class ExtractedMedicineDto(
    val name: ExtractedField<String> = ExtractedField(),
    val strength: ExtractedField<String> = ExtractedField(),
    val type: ExtractedField<String> = ExtractedField(),
    val doseAmount: ExtractedField<Double> = ExtractedField(),
    val doseUnit: ExtractedField<String> = ExtractedField(),
    val frequency: ExtractedField<String> = ExtractedField(),
    val timesPerDay: ExtractedField<Int> = ExtractedField(),
    val times: ExtractedField<List<String>> = ExtractedField(),
    val foodInstruction: ExtractedField<String> = ExtractedField(),
    val durationDays: ExtractedField<Int> = ExtractedField(),
    val additionalInstructions: ExtractedField<String> = ExtractedField(),
    val rawText: String = "",
)

@Serializable
data class ExtractedMedicinePhotoDto(
    val name: ExtractedField<String> = ExtractedField(),
    val strength: ExtractedField<String> = ExtractedField(),
    val form: ExtractedField<String> = ExtractedField(),
    val composition: ExtractedField<String> = ExtractedField(),
    val rawText: String = "",
)

/** Extraction payload; items are decoded per kind by the client. */
@Serializable
data class ExtractionDto(
    val provider: String? = null,
    val model: String? = null,
    val extractedAt: Long? = null,
    val notes: String = "",
    val items: List<JsonObject> = emptyList(),
    val error: String? = null,
)

@Serializable
data class PrescriptionDto(
    val id: String,
    val kind: String = "PRESCRIPTION",
    val familyId: String,
    val memberId: String? = null,
    val mimeType: String = "image/jpeg",
    val status: String,
    val extraction: ExtractionDto = ExtractionDto(),
    val confirmedMedicineIds: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class ConfirmPrescriptionRequest(
    val memberId: String,
    val medicines: List<MedicineDto>,
    val initialStock: Map<String, Double> = emptyMap(),
)

@Serializable
data class ConfirmPrescriptionResponse(val prescription: PrescriptionDto, val medicines: List<MedicineDto>)

@Serializable
data class HealthDto(val service: String, val version: String, val db: String, @SerialName("aiExtraction") val aiExtraction: String)
