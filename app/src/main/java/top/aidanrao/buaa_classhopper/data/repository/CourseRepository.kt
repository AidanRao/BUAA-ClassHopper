package top.aidanrao.buaa_classhopper.data.repository

import top.aidanrao.buaa_classhopper.data.model.IclassAccessException
import top.aidanrao.buaa_classhopper.data.model.dto.IclassLoginResult
import android.content.Context
import android.util.Log
import top.aidanrao.buaa_classhopper.data.api.FallbackApi
import top.aidanrao.buaa_classhopper.data.api.IclassAuthApi
import top.aidanrao.buaa_classhopper.data.api.IclassApi
import top.aidanrao.buaa_classhopper.data.model.Result
import top.aidanrao.buaa_classhopper.data.model.dto.CourseDto
import top.aidanrao.buaa_classhopper.data.model.dto.FallbackCourseDto
import top.aidanrao.buaa_classhopper.data.model.dto.IclassLoginResponse
import top.aidanrao.buaa_classhopper.data.vpn.VpnPreferences
import top.aidanrao.buaa_classhopper.data.vpn.IclassSessionExpiredException
import top.aidanrao.buaa_classhopper.data.vpn.IclassSession
import top.aidanrao.buaa_classhopper.data.vpn.IclassLoginFailure
import top.aidanrao.buaa_classhopper.data.vpn.IclassNetworkSelector
import kotlinx.coroutines.CancellationException
import top.aidanrao.buaa_classhopper.di.NetworkModule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class CourseRepository @Inject constructor(
    @Named(NetworkModule.API_ICLASS_DIRECT) private val iclassDirectApi: IclassApi,
    @Named(NetworkModule.API_ICLASS_VPN) private val iclassVpnApi: IclassApi,
    @Named(NetworkModule.AUTH_ICLASS_DIRECT) private val iclassDirectAuth: IclassAuthApi,
    @Named(NetworkModule.AUTH_ICLASS_VPN) private val iclassVpnAuth: IclassAuthApi,
    private val fallbackApi: FallbackApi,
    private val tokenManager: TokenManager,
    private val vpnPreferences: VpnPreferences,
    private val networkSelector: IclassNetworkSelector,
    private val accessPolicy: IclassAccessPolicyRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CourseRepository"
        private const val PREFS_NAME = "course_checkin_settings"
        private const val KEY_FALLBACK_ENABLED = "fallback_enabled"
        const val VPN_SESSION_EXPIRED_MESSAGE = IclassSessionExpiredException.VPN_MESSAGE
        const val DIRECT_SESSION_EXPIRED_MESSAGE = IclassSessionExpiredException.DIRECT_MESSAGE
    }

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun iclassApi(vpn: Boolean): IclassApi =
        if (vpn) iclassVpnApi else iclassDirectApi

    suspend fun login(): Result<IclassLoginResponse> = login(networkSelector.useVpn())

    suspend fun login(vpn: Boolean, loginName: String? = null): Result<IclassLoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = IclassSession.login(
                    if (vpn) iclassVpnAuth else iclassDirectAuth, vpn,
                    suppliedLoginName = loginName ?: vpnPreferences.getLoginName(vpn),
                    onAuthenticated = { vpnPreferences.saveLoginName(vpn, it) }
                )
                if (response.result != null) {
                    vpnPreferences.setSessionReady(vpn, true)
                    accessPolicy.requireAllowed(response.result.userName, response.result.realName)
                    Result.success(response)
                } else {
                    vpnPreferences.setSessionReady(vpn, false)
                    val error = IclassLoginFailure.rejected(response, vpn)
                    Result.error(error, error.message)
                }
            } catch (e: IclassAccessException) {
                Result.error(e, e.message)
            } catch (e: IclassSessionExpiredException) {
                vpnPreferences.setSessionReady(e.vpn, false)
                Result.error(e, e.message ?: "请重新通过 SSO 登录")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.error(e, if (e is IclassLoginFailure) e.message
                    else "iClass 登录失败：${IclassLoginFailure.reason(e)}")
            }
        }
    }

    suspend fun getCourseSchedule(
        loginData: IclassLoginResult,
        dateStr: String
    ): Result<List<CourseDto>> {
        return withContext(Dispatchers.IO) {
            try {
                accessPolicy.requireAllowed(loginData.userName, loginData.realName)
                val response = iclassApi(loginData.vpnMode).getCourseSchedule(dateStr, loginData.id, loginData.sessionId)
                if (response.status == "2" || response.result.isNullOrEmpty()) {
                    Result.success(emptyList())
                } else {
                    Result.success(response.result)
                }
            } catch (e: IclassAccessException) {
                Result.error(e, e.message)
            } catch (e: IclassSessionExpiredException) {
                vpnPreferences.setSessionReady(e.vpn, false)
                Result.error(e, e.message ?: "请重新通过 SSO 登录")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isFallbackEnabled()) {
                    getCourseScheduleFallback(loginData, dateStr)
                } else {
                    Result.error(e, "获取课表失败: ${e.message}")
                }
            }
        }
    }

    private suspend fun getCourseScheduleFallback(loginData: IclassLoginResult, dateStr: String): Result<List<CourseDto>> {
        return withContext(Dispatchers.IO) {
            try {
                accessPolicy.requireAllowed(loginData.userName, loginData.realName)
                val token = tokenManager.getValidToken() ?: return@withContext Result.error(
                    Exception("No token"),
                    "未获取到授权令牌"
                )

                val formattedDate = if (dateStr.length == 8) {
                    "${dateStr.take(4)}-${dateStr.substring(4, 6)}-${dateStr.substring(6, 8)}"
                } else {
                    dateStr
                }
                
                accessPolicy.requireAllowed(loginData.userName, loginData.realName)
                val response = fallbackApi.getCourseSchedule(formattedDate, token)
                if (response.code != 1) {
                    return@withContext Result.error(Exception(response.msg), response.msg)
                }
                
                val courses = response.data.result.mapNotNull { convertFallbackCourse(it) }
                Result.success(courses)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IclassAccessException) {
                Result.error(e, e.message)
            } catch (e: Exception) {
                Log.e(TAG, "Fallback API failed", e)
                Result.error(e, "Fallback接口失败: ${e.message}")
            }
        }
    }

    suspend fun signClass(courseId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            var authenticated: IclassLoginResult? = null
            try {
                val vpn = networkSelector.useVpn()
                val loginResult = login(vpn)
                if (loginResult.isError) {
                    return@withContext loginResult.map { Unit }
                }

                val loginData = loginResult.getOrThrow().result ?: return@withContext Result.error(
                    Exception("登录失败"),
                    "登录失败"
                )
                authenticated = loginData
                val timestamp = System.currentTimeMillis()
                accessPolicy.requireAllowed(loginData.userName, loginData.realName)
                
                val response = iclassApi(vpn).signClass(courseId, timestamp, loginData.id)
                if (response.result != null) {
                    Result.success(Unit)
                } else {
                    if (isFallbackEnabled()) {
                        signClassFallback(loginData, courseId)
                    } else {
                        Result.error(Exception(response.msg), response.msg ?: "签到失败")
                    }
                }
            } catch (e: IclassAccessException) {
                Result.error(e, e.message)
            } catch (e: IclassSessionExpiredException) {
                vpnPreferences.setSessionReady(e.vpn, false)
                Result.error(e, e.message ?: "请重新通过 SSO 登录")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isFallbackEnabled() && authenticated != null) {
                    signClassFallback(authenticated, courseId)
                } else {
                    Result.error(e, "签到失败: ${e.message}")
                }
            }
        }
    }

    private suspend fun signClassFallback(loginData: IclassLoginResult, courseId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                accessPolicy.requireAllowed(loginData.userName, loginData.realName)
                val token = tokenManager.getValidToken() ?: return@withContext Result.error(
                    Exception("No token"),
                    "未获取到授权令牌"
                )

                accessPolicy.requireAllowed(loginData.userName, loginData.realName)
                val response = fallbackApi.signClass(courseId, token)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.error(Exception("HTTP ${response.code()}"), "签到失败: ${response.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IclassAccessException) {
                Result.error(e, e.message)
            } catch (e: Exception) {
                Log.e(TAG, "Fallback sign failed", e)
                Result.error(e, "Fallback签到失败: ${e.message}")
            }
        }
    }

    private fun isFallbackEnabled(): Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(KEY_FALLBACK_ENABLED, false)
    }

    private fun convertFallbackCourse(fallback: FallbackCourseDto): CourseDto? {
        return try {
            CourseDto(
                id = fallback.id,
                courseId = fallback.courseId,
                courseName = fallback.courseName,
                courseType = fallback.courseType,
                weekDay = fallback.weekDay,
                courseNum = fallback.courseNum,
                teacherName = fallback.teacherName,
                classroomName = fallback.classroomName,
                signStatus = if (fallback.signStatus == "已签到") 1 else 0,
                classBeginTime = LocalDateTime.parse(fallback.classBeginTime, dateTimeFormatter),
                classEndTime = LocalDateTime.parse(fallback.classEndTime, dateTimeFormatter)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert fallback course", e)
            null
        }
    }
}
