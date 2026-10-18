package com.example.musicapp.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AuthApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Supports adb reverse (localhost) and emulator (10.0.2.2)
    private val baseUrls = listOf(
        "http://localhost:3000",
        "http://10.0.2.2:3000",
        "http://127.0.0.1:3000"
    )

    private var activeBaseUrl: String? = null

    private suspend fun getWorkingBaseUrl(): String = withContext(Dispatchers.IO) {
        activeBaseUrl?.let { return@withContext it }
        for (url in baseUrls) {
            try {
                val req = Request.Builder().url("$url/api/health").build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        activeBaseUrl = url
                        return@withContext url
                    }
                }
            } catch (_: Exception) {
            }
        }
        // Default to localhost
        "http://localhost:3000"
    }

    suspend fun signup(username: String, passwordHash: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val base = getWorkingBaseUrl()
            val payload = json.encodeToString(mapOf("username" to username, "passwordHash" to passwordHash))
            val req = Request.Builder()
                .url("$base/api/auth/signup")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val profile = json.decodeFromString<UserProfile>(body)
                    Result.success(profile)
                } else {
                    val errorMsg = try {
                        val obj = json.decodeFromString<Map<String, String>>(body)
                        obj["error"] ?: "Signup failed (${resp.code})"
                    } catch (_: Exception) {
                        "Signup failed (${resp.code})"
                    }
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(username: String, passwordHash: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val base = getWorkingBaseUrl()
            val payload = json.encodeToString(mapOf("username" to username, "passwordHash" to passwordHash))
            val req = Request.Builder()
                .url("$base/api/auth/login")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val profile = json.decodeFromString<UserProfile>(body)
                    Result.success(profile)
                } else {
                    val errorMsg = try {
                        val obj = json.decodeFromString<Map<String, String>>(body)
                        obj["error"] ?: "Login failed (${resp.code})"
                    } catch (_: Exception) {
                        "Login failed (${resp.code})"
                    }
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateInterests(userId: String, genres: List<String>, artists: List<String>): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val base = getWorkingBaseUrl()
            @Serializable
            data class InterestsReq(val userId: String, val genres: List<String>, val artists: List<String>)
            val payload = json.encodeToString(InterestsReq(userId, genres, artists))
            val req = Request.Builder()
                .url("$base/api/auth/interests")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val profile = json.decodeFromString<UserProfile>(body)
                    Result.success(profile)
                } else {
                    Result.failure(Exception("Failed to update interests: ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllUsers(): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
        try {
            val base = getWorkingBaseUrl()
            val req = Request.Builder().url("$base/api/auth/users").build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val users = json.decodeFromString<List<UserProfile>>(body)
                    Result.success(users)
                } else {
                    Result.failure(Exception("Failed to fetch users: ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
