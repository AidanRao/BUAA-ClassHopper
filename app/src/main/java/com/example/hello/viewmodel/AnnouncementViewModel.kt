package com.example.hello.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hello.data.model.Result
import com.example.hello.data.model.dto.AnnouncementDetailDto
import com.example.hello.data.model.dto.AnnouncementDto
import com.example.hello.data.repository.AnnouncementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnnouncementViewModel @Inject constructor(
    private val announcementRepository: AnnouncementRepository
) : ViewModel() {

    private val _announcements = MutableLiveData<List<AnnouncementDto>>()
    val announcements: LiveData<List<AnnouncementDto>> = _announcements

    private val _announcementDetail = MutableLiveData<AnnouncementDetailDto>()
    val announcementDetail: LiveData<AnnouncementDetailDto> = _announcementDetail

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun loadAnnouncements() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = announcementRepository.getAnnouncements()) {
                is Result.Success -> {
                    _announcements.value = result.data
                    _isLoading.value = false
                }
                is Result.Error -> {
                    _error.value = result.getErrorMessage() ?: "加载公告失败"
                    _isLoading.value = false
                }
                Result.Loading -> {}
            }
        }
    }

    fun loadAnnouncementDetail(id: String) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = announcementRepository.getAnnouncementDetail(id)) {
                is Result.Success -> {
                    _announcementDetail.value = result.data
                    _isLoading.value = false
                }
                is Result.Error -> {
                    _error.value = result.getErrorMessage() ?: "加载公告详情失败"
                    _isLoading.value = false
                }
                Result.Loading -> {}
            }
        }
    }
}
