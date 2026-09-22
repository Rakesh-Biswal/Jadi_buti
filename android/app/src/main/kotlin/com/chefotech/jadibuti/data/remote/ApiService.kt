package com.chefotech.jadibuti.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ApiService {
    // --- auth ---
    @POST("auth/register") suspend fun register(@Body body: RegisterRequest): Response<ApiResponse<AuthResponse>>
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): Response<ApiResponse<AuthResponse>>
    @POST("auth/refresh") suspend fun refresh(@Body body: RefreshRequest): Response<ApiResponse<AuthResponse>>
    @POST("auth/logout") suspend fun logout(@Body body: RefreshRequest): Response<ApiResponse<Map<String, Boolean>>>
    @GET("auth/me") suspend fun me(): Response<ApiResponse<MeResponse>>

    // --- family ---
    @GET("families") suspend fun families(): Response<ApiResponse<List<FamilyWithMembership>>>
    @POST("families") suspend fun createFamily(@Body body: CreateFamilyRequest): Response<ApiResponse<FamilyWithMembership>>
    @POST("families/join") suspend fun joinFamily(@Body body: JoinFamilyRequest): Response<ApiResponse<FamilyWithMembership>>
    @POST("families/{familyId}/invites") suspend fun createInvite(@Path("familyId") familyId: String, @Body body: CreateInviteRequest): Response<ApiResponse<InviteDto>>
    @GET("families/{familyId}/memberships") suspend fun memberships(@Path("familyId") familyId: String): Response<ApiResponse<List<MembershipDto>>>
    @PATCH("families/{familyId}/memberships/{userId}") suspend fun updateMembership(@Path("familyId") familyId: String, @Path("userId") userId: String, @Body body: MembershipPatch): Response<ApiResponse<MembershipDto>>
    @DELETE("families/{familyId}/memberships/{userId}") suspend fun removeMembership(@Path("familyId") familyId: String, @Path("userId") userId: String): Response<ApiResponse<MembershipDto>>

    // --- sync ---
    @POST("sync/push") suspend fun push(@Body body: SyncPushRequest): Response<ApiResponse<SyncPushResponse>>
    @GET("sync/pull") suspend fun pull(@Query("familyId") familyId: String, @Query("since") since: Long, @Query("limit") limit: Int = 1000): Response<ApiResponse<SyncPullResponse>>

    // --- prescriptions ---
    @Multipart
    @POST("families/{familyId}/prescriptions")
    suspend fun uploadPrescription(@Path("familyId") familyId: String, @Part image: MultipartBody.Part, @Part("memberId") memberId: RequestBody?): Response<ApiResponse<PrescriptionDto>>
    @GET("families/{familyId}/prescriptions") suspend fun prescriptions(@Path("familyId") familyId: String): Response<ApiResponse<List<PrescriptionDto>>>
    @GET("families/{familyId}/prescriptions/{id}") suspend fun prescription(@Path("familyId") familyId: String, @Path("id") id: String): Response<ApiResponse<PrescriptionDto>>
    @Streaming @GET("families/{familyId}/prescriptions/{id}/image") suspend fun prescriptionImage(@Path("familyId") familyId: String, @Path("id") id: String): Response<ResponseBody>
    @POST("families/{familyId}/prescriptions/{id}/extract") suspend fun extract(@Path("familyId") familyId: String, @Path("id") id: String): Response<ApiResponse<PrescriptionDto>>
    @POST("families/{familyId}/prescriptions/{id}/confirm") suspend fun confirm(@Path("familyId") familyId: String, @Path("id") id: String, @Body body: ConfirmPrescriptionRequest): Response<ApiResponse<ConfirmPrescriptionResponse>>
    @POST("families/{familyId}/prescriptions/{id}/reject") suspend fun reject(@Path("familyId") familyId: String, @Path("id") id: String): Response<ApiResponse<PrescriptionDto>>
    // --- medicine packaging photos ---
    @Multipart
    @POST("families/{familyId}/medicine-scans")
    suspend fun uploadMedicineScan(@Path("familyId") familyId: String, @Part image: MultipartBody.Part, @Part("memberId") memberId: RequestBody?): Response<ApiResponse<PrescriptionDto>>
    @GET("families/{familyId}/medicine-scans/{id}") suspend fun medicineScan(@Path("familyId") familyId: String, @Path("id") id: String): Response<ApiResponse<PrescriptionDto>>
    @Streaming @GET("families/{familyId}/medicine-scans/{id}/image") suspend fun medicineScanImage(@Path("familyId") familyId: String, @Path("id") id: String): Response<ResponseBody>
    @POST("families/{familyId}/medicine-scans/{id}/extract") suspend fun extractMedicineScan(@Path("familyId") familyId: String, @Path("id") id: String): Response<ApiResponse<PrescriptionDto>>
    @DELETE("families/{familyId}/medicine-scans/{id}") suspend fun deleteMedicineScan(@Path("familyId") familyId: String, @Path("id") id: String): Response<ApiResponse<Map<String, Boolean>>>
    @DELETE("families/{familyId}/prescriptions/{id}") suspend fun deletePrescription(@Path("familyId") familyId: String, @Path("id") id: String): Response<ApiResponse<Map<String, Boolean>>>
}
