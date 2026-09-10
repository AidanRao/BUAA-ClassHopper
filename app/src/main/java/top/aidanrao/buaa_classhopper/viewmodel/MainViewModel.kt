package top.aidanrao.buaa_classhopper.viewmodel

import android.util.Log
import top.aidanrao.buaa_classhopper.data.model.dto.IclassLoginResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString
import top.aidanrao.buaa_classhopper.command.CommandDispatcher
import top.aidanrao.buaa_classhopper.data.model.Result
import top.aidanrao.buaa_classhopper.ui.HomeCourseState
import top.aidanrao.buaa_classhopper.ui.HomeIdentity
import top.aidanrao.buaa_classhopper.data.model.dto.UserInfoDto
import top.aidanrao.buaa_classhopper.data.repository.AuthRepository
import top.aidanrao.buaa_classhopper.data.repository.CourseRepository
import top.aidanrao.buaa_classhopper.data.repository.UserRepository
import top.aidanrao.buaa_classhopper.service.ChatWebSocketService
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val commandDispatcher: CommandDispatcher,
    private val chatWebSocketService: ChatWebSocketService
) : ViewModel() {

    private val _courseState = MutableLiveData<HomeCourseState>()
    val courseState: LiveData<HomeCourseState> = _courseState

    private val _userInfo = MutableLiveData<HomeIdentity>()
    val userInfo: LiveData<HomeIdentity> = _userInfo

    private val _signingIds = MutableLiveData<Set<Int>>(emptySet())
    val signingIds: LiveData<Set<Int>> = _signingIds

    var selectedDate: String = java.time.LocalDate.now().toString()
        private set

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private var isRequestInProgress = false
    
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeSerializer())
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeDeserializer())
        .create()

    private val _userProfile = MutableLiveData<UserInfoDto>()
    val userProfile: LiveData<UserInfoDto> = _userProfile

    init {
        connectWebSocket()
    }

    private fun connectWebSocket() {
        viewModelScope.launch {
            val token = authRepository.getValidToken()
            if (token != null) {
                Log.d("MainViewModel", "获取token成功: $token")
                withContext(Dispatchers.Main) {
                    chatWebSocketService.connect(token, object : ChatWebSocketService.Listener {
                        override fun onOpen() = Unit

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

                        override fun onClosing(code: Int, reason: String) = Unit

                        override fun onClosed(code: Int, reason: String) = Unit

                        override fun onFailure(error: String) = Unit

                        override fun onReconnectAttempt(attempt: Int, delayMs: Long) = Unit
                    })
                }
            } else {
                Log.e("MainViewModel", "获取token失败")
            }
        }
    }

    fun getClassInfo(date: String) {
        if (date.isEmpty()) {
            _toastMessage.postValue("请选择日期")
            return
        }

        if (isRequestInProgress) {
            _toastMessage.postValue("操作进行中，请稍候")
            return
        }

        isRequestInProgress = true
        selectedDate = date
        _courseState.value = HomeCourseState.Loading
        _userInfo.value = HomeIdentity(null, null, null)

        viewModelScope.launch {
            when (val loginResult = courseRepository.login()) {
                is Result.Success -> {
                    val loginData = loginResult.data.result
                    if (loginData != null) {
                        _userInfo.value = HomeIdentity(loginData.realName, loginData.userName, loginData.academyName)
                        
                        val dateStr = date.replace("-", "")
                        fetchCourseSchedule(loginData, dateStr)
                    } else {
                        failQuery(loginResult.data.ERRMSG ?: "登录失败")
                    }
                }
                is Result.Error -> {
                    failQuery(loginResult.getErrorMessage() ?: "登录失败")
                }
                Result.Loading -> {}
            }
        }
    }

    private suspend fun fetchCourseSchedule(loginData: IclassLoginResult, dateStr: String) {
        when (val result = courseRepository.getCourseSchedule(loginData, dateStr)) {
            is Result.Success -> {
                _courseState.value = HomeCourseState.Success(result.data)
                isRequestInProgress = false
            }
            is Result.Error -> {
                failQuery(result.getErrorMessage() ?: "获取课表失败")
            }
            Result.Loading -> {}
        }
    }

    private fun failQuery(message: String) {
        _courseState.value = HomeCourseState.Error(message)
        _error.value = message
        isRequestInProgress = false
    }

    fun signClass(courseId: Int) {
        val signing = _signingIds.value.orEmpty()
        if (isRequestInProgress || signing.isNotEmpty()) return
        _signingIds.value = signing + courseId
        viewModelScope.launch {
            try {
                when (val result = courseRepository.signClass(courseId)) {
                    is Result.Success -> {
                        _toastMessage.value = "签到成功"
                        // Refresh the currently selected day, even if it changed while signing.
                        getClassInfo(selectedDate)
                    }
                    is Result.Error -> _error.value = result.getErrorMessage() ?: "签到失败"
                    Result.Loading -> Unit
                }
            } finally {
                _signingIds.value = _signingIds.value.orEmpty() - courseId
            }
        }
    }

    fun fetchUserProfile() {
        viewModelScope.launch {
            when (val result = userRepository.getUserInfo()) {
                is Result.Success -> {
                    _userProfile.postValue(result.data)
                }
                is Result.Error -> {
                    Log.e("MainViewModel", "获取用户信息失败: ${result.getErrorMessage()}")
                }
                Result.Loading -> {}
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        chatWebSocketService.close()
    }

    private class LocalDateTimeSerializer : JsonSerializer<LocalDateTime> {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        
        override fun serialize(src: LocalDateTime?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(src?.format(formatter))
        }
    }
    
    private class LocalDateTimeDeserializer : JsonDeserializer<LocalDateTime> {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): LocalDateTime? {
            return json?.asString?.let {
                LocalDateTime.parse(it, formatter)
            }
        }
    }
}
