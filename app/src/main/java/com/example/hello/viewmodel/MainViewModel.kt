package com.example.hello.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.hello.command.CommandDispatcher
import com.example.hello.command.GetScheduleCommandHandler
import com.example.hello.command.SignCourseCommandHandler
import com.example.hello.model.Course
import com.example.hello.service.ApiService
import com.example.hello.service.ChatWebSocketService
import com.example.hello.service.IclassApiService
import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import okio.ByteString

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // UI State
    private val _courses = MutableLiveData<List<Course>>()
    val courses: LiveData<List<Course>> = _courses

    private val _userInfo = MutableLiveData<String>()
    val userInfo: LiveData<String> = _userInfo

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isEmpty = MutableLiveData<Boolean>()
    val isEmpty: LiveData<Boolean> = _isEmpty

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    // 防止并发请求的标志
    private var isRequestInProgress = false

    // WebSocket State
    private val _webSocketStatus = MutableLiveData<WebSocketStatus>()
    val webSocketStatus: LiveData<WebSocketStatus> = _webSocketStatus

    enum class WebSocketStatus {
        CONNECTED, CONNECTING, DISCONNECTED
    }

    // Services
    val apiService = ApiService(application)
    private val iclassApiService = IclassApiService(application)
    // 临时跳过 TLS 证书校验（仅用于测试环境，正式环境务必改回 false）
    private val ALLOW_INSECURE_TLS = true
    private val chatWebSocketService = ChatWebSocketService(allowInsecureForDebug = ALLOW_INSECURE_TLS)

    private val commandDispatcher = CommandDispatcher()

    // Data
    private var currentUserId: String? = null
    private var currentSessionId: String? = null
    var token: String? = null
    var expireAt: Long? = null
    // 创建支持LocalDateTime的Gson实例
    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeSerializer())
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeDeserializer())
        .create()
    
    init {
        // 注册命令处理器
        commandDispatcher.registerHandler(GetScheduleCommandHandler(application))
        commandDispatcher.registerHandler(SignCourseCommandHandler(application))
        _webSocketStatus.value = WebSocketStatus.CONNECTING
        connectWebSocket()
    }
    
    // LocalDateTime序列化器
    private class LocalDateTimeSerializer : JsonSerializer<LocalDateTime> {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        
        override fun serialize(src: LocalDateTime?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(src?.format(formatter))
        }
    }
    
    // LocalDateTime反序列化器
    private class LocalDateTimeDeserializer : JsonDeserializer<LocalDateTime> {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): LocalDateTime? {
            return json?.asString?.let {
                LocalDateTime.parse(it, formatter)
            }
        }
    }

    private fun connectWebSocket() {
        apiService.getValidToken(object : ApiService.OnAuthListener {
            override fun onSuccess(token: String, expireAt: Long) {
                Log.d("MainViewModel", "自动获取token成功")
                viewModelScope.launch(Dispatchers.Main) {
                    chatWebSocketService.connect(token, object : ChatWebSocketService.Listener {
                        override fun onOpen() {
                            _webSocketStatus.postValue(WebSocketStatus.CONNECTED)
                        }

                        override fun onMessage(text: String) {
                            Log.d("ChatWS", "Text message: $text")
                            viewModelScope.launch(Dispatchers.IO) {
                                val result = commandDispatcher.dispatch(text)
                                if (result != null) {
                                    val responseJson = gson.toJson(result)
                                    chatWebSocketService.send(responseJson)
                                }
                            }
                        }

                        override fun onMessage(bytes: ByteString) {
                            Log.d("ChatWS", "Binary message: ${bytes.hex()}")
                        }

                        override fun onClosing(code: Int, reason: String) {
                            _webSocketStatus.postValue(WebSocketStatus.DISCONNECTED)
                        }

                        override fun onClosed(code: Int, reason: String) {
                            _webSocketStatus.postValue(WebSocketStatus.DISCONNECTED)
                        }

                        override fun onFailure(error: String) {
                            _webSocketStatus.postValue(WebSocketStatus.DISCONNECTED)
                        }

                        override fun onReconnectAttempt(attempt: Int, delayMs: Long) {
                            _webSocketStatus.postValue(WebSocketStatus.CONNECTING)
                        }
                    })
                }
            }

            override fun onFailure(error: String) {
                Log.e("MainViewModel", "获取token失败: $error")
                _webSocketStatus.postValue(WebSocketStatus.DISCONNECTED)
            }
        })
    }

    fun getClassInfo(studentId: String, date: String) {
        if (studentId.isEmpty() || date.isEmpty()) {
            _toastMessage.postValue("请输入学号和日期")
            return
        }

        if (isRequestInProgress) {
            _toastMessage.postValue("操作进行中，请稍候")
            return
        }

        isRequestInProgress = true
        _isLoading.postValue(true)
        _isEmpty.postValue(false)

        iclassApiService.login(studentId, object : IclassApiService.OnLoginListener {
            override fun onSuccess(userId: String, sessionId: String, realName: String, academyName: String) {
                currentUserId = userId
                currentSessionId = sessionId
                _userInfo.postValue("$realName - $academyName")

                val dateStr = date.replace("-", "")
                iclassApiService.getCourseSchedule(userId, sessionId, dateStr, object : IclassApiService.OnCourseScheduleListener {
                    override fun onSuccess(courses: List<Course>) {
                        _isLoading.postValue(false)
                        _courses.postValue(courses)
                        isRequestInProgress = false
                    }

                    override fun onEmpty() {
                        _isLoading.postValue(false)
                        _isEmpty.postValue(true)
                        isRequestInProgress = false
                    }

                    override fun onFailure(error: String) {
                        _isLoading.postValue(false)
                        _error.postValue(error)
                        isRequestInProgress = false
                    }
                })
            }

            override fun onFailure(error: String) {
                val cachedToken = apiService.getCachedToken()
                if (cachedToken != null) {
                    val dateStr = date.replace("-", "")
                    iclassApiService.getCourseSchedule("", "", dateStr, object : IclassApiService.OnCourseScheduleListener {
                        override fun onSuccess(courses: List<Course>) {
                            _isLoading.postValue(false)
                            _courses.postValue(courses)
                            isRequestInProgress = false
                        }

                        override fun onEmpty() {
                            _isLoading.postValue(false)
                            _isEmpty.postValue(true)
                            isRequestInProgress = false
                        }

                        override fun onFailure(error: String) {
                            _isLoading.postValue(false)
                            _error.postValue(error)
                            isRequestInProgress = false
                        }
                    }, cachedToken)
                } else {
                    _isLoading.postValue(false)
                    _error.postValue(error)
                    isRequestInProgress = false
                }
            }
        })
    }

    fun signClass(studentId: String, courseId: Int, date: String) {
        apiService.getValidToken(object : ApiService.OnAuthListener {
            override fun onSuccess(token: String, expireAt: Long) {
                iclassApiService.signClass(studentId, courseId, object : IclassApiService.OnSignListener {
                    override fun onSuccess() {
                        _toastMessage.postValue("签到成功")
                        getClassInfo(studentId, date)
                    }

                    override fun onFailure(error: String) {
                        _error.postValue(error)
                    }
                }, token)
            }

            override fun onFailure(error: String) {
                _error.postValue(error)
            }
        })
    }

    override fun onCleared() {
        super.onCleared()
        chatWebSocketService.close()
    }
    
    // 用户信息相关
    private val _userProfile = MutableLiveData<ApiService.UserInfoResponse.UserInfoData>()
    val userProfile: LiveData<ApiService.UserInfoResponse.UserInfoData> = _userProfile
    
    // 获取用户信息
    fun fetchUserProfile() {
        apiService.getValidToken(object : ApiService.OnAuthListener {
            override fun onSuccess(token: String, expireAt: Long) {
                this@MainViewModel.token = token
                this@MainViewModel.expireAt = expireAt
                apiService.getUserInfo(token, object : ApiService.OnUserInfoListener {
                    override fun onSuccess(userInfo: ApiService.UserInfoResponse.UserInfoData) {
                        _userProfile.postValue(userInfo)
                    }

                    override fun onFailure(error: String) {
                        Log.e("MainViewModel", "获取用户信息失败: $error")
                    }
                })
            }

            override fun onFailure(error: String) {
                Log.e("MainViewModel", "获取token失败: $error")
            }
        })
    }
}
