package top.aidanrao.buaa_classhopper.data.repository

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.aidanrao.buaa_classhopper.BuildConfig
import top.aidanrao.buaa_classhopper.data.api.IclassAccessPolicyApi
import top.aidanrao.buaa_classhopper.data.model.IclassAccessException
import top.aidanrao.buaa_classhopper.data.model.IclassAccessPolicy
import top.aidanrao.buaa_classhopper.data.model.dto.IclassAccessPolicyDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IclassAccessPolicyRepository @Inject constructor(
    private val api: IclassAccessPolicyApi,
    private val gson: Gson,
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "iclass_access_policy"
        private const val KEY_POLICY = "policy"
        private const val KEY_FETCHED_AT = "fetchedAt"
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var snapshot: IclassAccessPolicy? = null
    private var started = false

    @Synchronized fun start() {
        if (started) return
        started = true
        loadLocal()
        scope.launch { refresh() }
    }

    internal fun loadLocal(builtIn: String = BuildConfig.ICLASS_ACCESS_POLICY_JSON) {
        snapshot = runCatching {
            preferences.getString(KEY_POLICY, null)?.let(::parsePolicy)
        }.getOrNull() ?: runCatching { parsePolicy(builtIn) }.getOrNull()
    }

    internal suspend fun refresh(): Boolean {
        return try {
            val response = api.getAccessPolicy()
            val next = try {
                require(response.code() == 200) { "Policy HTTP failure" }
                IclassAccessPolicy.fromDto(requireNotNull(response.body()).getDataOrThrow())
            } finally {
                response.errorBody()?.close()
            }
            // Publish only after the complete snapshot is durably saved.
            val saved = preferences.edit()
                .putString(KEY_POLICY, gson.toJson(next))
                .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                .commit()
            if (!saved) return false
            snapshot = next
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun parsePolicy(json: String): IclassAccessPolicy =
        IclassAccessPolicy.fromDto(requireNotNull(gson.fromJson(json, IclassAccessPolicyDto::class.java)))

    fun requireAllowed(studentId: String?, name: String?) {
        val current = snapshot ?: throw IclassAccessException(unavailable = true)
        if (!current.allows(studentId, name)) throw IclassAccessException()
    }
}
