package com.chefotech.jadibuti.data.repo

import android.content.Context
import com.chefotech.jadibuti.data.local.MedicineDao
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.remote.ApiService
import com.chefotech.jadibuti.data.remote.ConfirmPrescriptionRequest
import com.chefotech.jadibuti.data.remote.ConfirmPrescriptionResponse
import com.chefotech.jadibuti.data.remote.ExtractedMedicineDto
import com.chefotech.jadibuti.data.remote.ExtractedMedicinePhotoDto
import com.chefotech.jadibuti.data.remote.MedicineDto
import com.chefotech.jadibuti.data.remote.NetworkUnavailableException
import com.chefotech.jadibuti.data.remote.PrescriptionDto
import com.chefotech.jadibuti.data.remote.apiCall
import com.chefotech.jadibuti.data.remote.json
import com.chefotech.jadibuti.data.toEntity
import com.chefotech.jadibuti.reminders.ReminderScheduler
import com.chefotech.jadibuti.scheduling.EventGenerator
import com.chefotech.jadibuti.sync.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Which kind of image upload: a doctor's prescription or a photo of medicine packaging. */
enum class UploadKind { PRESCRIPTION, MEDICINE_PHOTO }

/**
 * Prescription and medicine-photo uploads. Both go through authenticated, family-scoped
 * endpoints; extraction results are drafts until the user confirms in the review screen.
 */
@Singleton
class PrescriptionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ApiService,
    private val session: SessionStore,
    private val medicines: MedicineDao,
    private val generator: EventGenerator,
    private val reminders: ReminderScheduler,
    private val syncScheduler: SyncScheduler,
) {
    private fun familyId() = requireNotNull(session.current.activeFamilyId) { "No active family" }

    suspend fun upload(kind: UploadKind, file: File, mimeType: String, memberId: String?): PrescriptionDto {
        val part = MultipartBody.Part.createFormData("image", file.name, file.asRequestBody(mimeType.toMediaType()))
        val member = memberId?.toRequestBody("text/plain".toMediaType())
        return when (kind) {
            UploadKind.PRESCRIPTION -> apiCall { api.uploadPrescription(familyId(), part, member) }
            UploadKind.MEDICINE_PHOTO -> apiCall { api.uploadMedicineScan(familyId(), part, member) }
        }
    }

    suspend fun extract(kind: UploadKind, id: String): PrescriptionDto = when (kind) {
        UploadKind.PRESCRIPTION -> apiCall { api.extract(familyId(), id) }
        UploadKind.MEDICINE_PHOTO -> apiCall { api.extractMedicineScan(familyId(), id) }
    }

    suspend fun get(kind: UploadKind, id: String): PrescriptionDto = when (kind) {
        UploadKind.PRESCRIPTION -> apiCall { api.prescription(familyId(), id) }
        UploadKind.MEDICINE_PHOTO -> apiCall { api.medicineScan(familyId(), id) }
    }

    suspend fun delete(kind: UploadKind, id: String) {
        when (kind) {
            UploadKind.PRESCRIPTION -> apiCall { api.deletePrescription(familyId(), id) }
            UploadKind.MEDICINE_PHOTO -> apiCall { api.deleteMedicineScan(familyId(), id) }
        }
    }

    suspend fun reject(id: String): PrescriptionDto = apiCall { api.reject(familyId(), id) }

    /** Downloads the protected image into the app cache (authenticated request). */
    suspend fun cachedImage(kind: UploadKind, id: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "uploads").apply { mkdirs() }
        val file = File(dir, "$id.img")
        if (file.exists() && file.length() > 0) return@withContext file
        val response = try {
            when (kind) {
                UploadKind.PRESCRIPTION -> api.prescriptionImage(familyId(), id)
                UploadKind.MEDICINE_PHOTO -> api.medicineScanImage(familyId(), id)
            }
        } catch (e: IOException) { throw NetworkUnavailableException(e) }
        if (!response.isSuccessful) throw IOException("Could not load image (${response.code()})")
        response.body()!!.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        file
    }

    /** Typed views of the extraction draft. */
    fun prescriptionItems(p: PrescriptionDto): List<ExtractedMedicineDto> =
        p.extraction.items.mapNotNull { runCatching { json.decodeFromJsonElement(ExtractedMedicineDto.serializer(), it) }.getOrNull() }

    fun medicinePhotoItems(p: PrescriptionDto): List<ExtractedMedicinePhotoDto> =
        p.extraction.items.mapNotNull { runCatching { json.decodeFromJsonElement(ExtractedMedicinePhotoDto.serializer(), it) }.getOrNull() }

    /** Explicit user confirmation: creates the reviewed medicines on the server and mirrors them locally. */
    suspend fun confirm(id: String, memberId: String, reviewed: List<MedicineDto>, initialStock: Map<String, Double>): ConfirmPrescriptionResponse {
        val result = apiCall { api.confirm(familyId(), id, ConfirmPrescriptionRequest(memberId, reviewed, initialStock)) }
        for (m in result.medicines) {
            val entity = m.toEntity()
            medicines.upsert(entity)
            generator.refreshMedicine(entity)
        }
        reminders.rescheduleAll()
        syncScheduler.requestSync() // pulls the initial-stock transactions created server-side
        return result
    }
}
