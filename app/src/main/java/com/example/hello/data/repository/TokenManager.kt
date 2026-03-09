package com.example.hello.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.hello.data.api.AuthApi
import com.example.hello.data.model.dto.AuthRequest
import com.example.hello.utils.DeviceIdUtil
import com.example.hello.utils.SignUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authApi: AuthApi
) {
    companion object {
        private const val PREFS_NAME = "AuthCache"
        private const val KEY_TOKEN = "token"
        private const val KEY_EXPIRE_AT = "expireAt"
        private const val APP_KEY = "buaa-classhopper-android"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    
    @Volatile
    private var cachedToken: String? = null
    
    @Volatile
    private var cachedExpireAt: Long? = null

    fun getCachedToken(): String? {
        if (isTokenValid(cachedToken, cachedExpireAt)) {
            return cachedToken
        }
        
        val (storedToken, storedExpireAt) = readCachedToken()
        if (isTokenValid(storedToken, storedExpireAt)) {
            cachedToken = storedToken
            cachedExpireAt = storedExpireAt
            return storedToken
        }
        
        return null
    }

    suspend fun getValidToken(): String? {
        mutex.withLock {
            if (isTokenValid(cachedToken, cachedExpireAt)) {
                return cachedToken
            }
            
            val (storedToken, storedExpireAt) = readCachedToken()
            if (isTokenValid(storedToken, storedExpireAt)) {
                cachedToken = storedToken
                cachedExpireAt = storedExpireAt
                return storedToken
            }
            
            return refreshToken()
        }
    }

    suspend fun refreshToken(): String? {
        return try {
            val timestamp = System.currentTimeMillis()
            val appUUID = DeviceIdUtil.getPersistentUUID(context)
            
            val params = mapOf(
                "appKey" to APP_KEY,
                "timestamp" to timestamp.toString(),
                "appUUID" to appUUID
            )
            val signature = SignUtils.generateSignature(params)
            
            val request = AuthRequest(
                appKey = APP_KEY,
                timestamp = timestamp,
                signature = signature,
                appUUID = appUUID
            )
            
            val response = authApi.authenticate(request)
            if (response.isSuccess && response.data != null) {
                val token = response.data.token
                val expireAt = parseExpireAt(response.data.expireAt)
                
                cachedToken = token
                cachedExpireAt = expireAt
                saveCachedToken(token, expireAt)
                
                token
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("TokenManager", "Failed to refresh token", e)
            null
        }
    }

    fun invalidateToken() {
        cachedToken = null
        cachedExpireAt = null
        prefs.edit { remove(KEY_TOKEN).remove(KEY_EXPIRE_AT) }
    }

    private fun isTokenValid(token: String?, expireAt: Long?): Boolean {
        if (token.isNullOrEmpty() || expireAt == null) return false
        return System.currentTimeMillis() < expireAt
    }

    private fun readCachedToken(): Pair<String?, Long?> {
        val token = prefs.getString(KEY_TOKEN, null)
        val expireAt = if (prefs.contains(KEY_EXPIRE_AT)) prefs.getLong(KEY_EXPIRE_AT, 0L) else null
        return Pair(token, expireAt)
    }

    private fun saveCachedToken(token: String, expireAt: Long) {
        prefs.edit {
            putString(KEY_TOKEN, token)
            putLong(KEY_EXPIRE_AT, expireAt)
        }
    }

    private fun parseExpireAt(expireAtStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
            val date = sdf.parse(expireAtStr.substringBeforeLast('Z'))
            date?.time ?: (System.currentTimeMillis() + 2 * 60 * 60 * 1000)
        } catch (e: Exception) {
            System.currentTimeMillis() + 2 * 60 * 60 * 1000
        }
    }
}
