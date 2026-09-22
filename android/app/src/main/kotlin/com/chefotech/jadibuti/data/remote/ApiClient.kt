package com.chefotech.jadibuti.data.remote

import com.chefotech.jadibuti.BuildConfig
import com.chefotech.jadibuti.data.prefs.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown for non-2xx responses so callers can show the server's message. */
class ApiException(val status: Int, val code: String, message: String) : IOException(message)

class NetworkUnavailableException(cause: Throwable) : IOException("No connection to the server", cause)

val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/**
 * Builds the Retrofit client. The base URL is read on every request so the user can
 * change the server in Settings without restarting the app. Bearer tokens are added
 * automatically and refreshed once on 401.
 */
@Singleton
class ApiClient @Inject constructor(private val session: SessionStore) {
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val base = session.apiBaseUrl.toHttpUrlOrNull() ?: BuildConfig.DEFAULT_API_BASE_URL.toHttpUrlOrNull()!!
        // Rewrite host/scheme/port/path-prefix so the placeholder base URL is replaced by the configured one.
        val relative = original.url.encodedPath.removePrefix(PLACEHOLDER.toHttpUrlOrNull()!!.encodedPath)
        val newUrl = base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + "/" + relative.trimStart('/')).encodedQuery(original.url.encodedQuery).build()
        val builder = original.newBuilder().url(newUrl)
        session.accessToken?.let { builder.header("Authorization", "Bearer $it") }
        builder.header("Accept", "application/json")
        chain.proceed(builder.build())
    }

    private val tokenAuthenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.request.url.encodedPath.contains("/auth/")) return null
            if (responseCount(response) >= 2) return null
            val refresh = session.refreshToken ?: return null
            val failedWith = response.request.header("Authorization")
            synchronized(this) {
                val current = session.accessToken
                if (current != null && failedWith != "Bearer $current") {
                    return response.request.newBuilder().header("Authorization", "Bearer $current").build()
                }
                val refreshed = runCatching { refreshBlocking(refresh) }.getOrNull() ?: run {
                    session.clearTokens()
                    return null
                }
                session.saveTokens(refreshed.accessToken, refreshed.refreshToken)
                return response.request.newBuilder().header("Authorization", "Bearer ${refreshed.accessToken}").build()
            }
        }
    }

    private fun refreshBlocking(refreshToken: String): AuthResponse? {
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
        val base = session.apiBaseUrl.toHttpUrlOrNull() ?: return null
        val req = Request.Builder().url(base.newBuilder().addPathSegments("auth/refresh").build())
            .post(body.toRequestBody("application/json".toMediaType())).build()
        plainClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val parsed = json.decodeFromString(ApiResponse.serializer(AuthResponse.serializer()), res.body!!.string())
            return parsed.data
        }
    }

    private fun responseCount(response: Response): Int {
        var r: Response? = response
        var n = 0
        while (r != null) { n++; r = r.priorResponse }
        return n
    }

    private val plainClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .apply {
            if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(PLACEHOLDER)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)

    companion object {
        /** Retrofit needs a fixed base URL; the interceptor swaps it for the configured server. */
        const val PLACEHOLDER = "https://placeholder.jadibuti.local/api/v1/"
    }
}

/** Unwraps the `{ok,data,error}` envelope, mapping failures to [ApiException]. */
suspend fun <T> apiCall(block: suspend () -> retrofit2.Response<ApiResponse<T>>): T {
    val response = try {
        block()
    } catch (e: IOException) {
        throw NetworkUnavailableException(e)
    }
    val body = response.body()
    if (response.isSuccessful && body?.ok == true) {
        @Suppress("UNCHECKED_CAST")
        return (body.data ?: Unit) as T
    }
    val err = body?.error ?: runCatching {
        json.decodeFromString(ApiResponse.serializer(kotlinx.serialization.json.JsonElement.serializer()), response.errorBody()?.string() ?: "").error
    }.getOrNull()
    throw ApiException(response.code(), err?.code ?: "http_${response.code()}", err?.message ?: "Request failed (${response.code()})")
}
