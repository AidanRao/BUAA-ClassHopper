package com.example.hello.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.example.hello.service.ApiService
import com.example.hello.R

class AnnouncementDetailActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var titleTextView: TextView
    private lateinit var timeTextView: TextView
    private lateinit var coverImageView: ImageView
    private lateinit var contentTextView: TextView
    private lateinit var authorNameTextView: TextView
    private lateinit var authorAvatarImageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcement_detail)

        initViews()
        initApiService()
        
        // 获取从Intent传递过来的公告ID
        val announcementId = intent.getStringExtra("ANNOUNCEMENT_ID")
        if (announcementId != null) {
            loadAnnouncementDetail(announcementId)
        } else {
            showError("未获取到公告ID")
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        titleTextView = findViewById(R.id.announcement_title)
        timeTextView = findViewById(R.id.announcement_time)
        coverImageView = findViewById(R.id.announcement_cover)
        contentTextView = findViewById(R.id.announcement_content)
        authorNameTextView = findViewById(R.id.author_name)
        authorAvatarImageView = findViewById(R.id.author_avatar)
        progressBar = findViewById(R.id.progress_bar)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "公告详情"

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun initApiService() {
        apiService = ApiService(this)
    }

    private fun loadAnnouncementDetail(announcementId: String) {
        showProgress()
        
        // 获取token并加载公告详情
        apiService.getAuthToken(object : ApiService.OnAuthListener {
            override fun onSuccess(token: String, expireAt: Long) {
                apiService.getAnnouncementDetail(token, announcementId, object : ApiService.OnAnnouncementDetailListener {
                    override fun onSuccess(announcementDetail: ApiService.AnnouncementDetailData) {
                        runOnUiThread {
                            hideProgress()
                            displayAnnouncementDetail(announcementDetail)
                        }
                    }

                    override fun onFailure(error: String) {
                        runOnUiThread {
                            hideProgress()
                            showError(error)
                        }
                    }
                })
            }

            override fun onFailure(error: String) {
                runOnUiThread {
                    hideProgress()
                    showError(error)
                }
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun displayAnnouncementDetail(announcementDetail: ApiService.AnnouncementDetailData) {
        // 设置标题
        titleTextView.text = announcementDetail.title
        
        // 设置时间
        timeTextView.text = announcementDetail.createTime.substringBefore('T')
        
        // 设置封面图
        if (!announcementDetail.cover.isNullOrEmpty()) {
            try {
                Glide.with(this)
                    .load(announcementDetail.cover)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(coverImageView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 设置内容
        contentTextView.text = announcementDetail.content ?: ""
        
        // 设置作者信息
        println("DEBUG: posterUsername = ${announcementDetail.posterUsername}")
        println("DEBUG: posterAvatar = ${announcementDetail.posterAvatar}")
        
        authorNameTextView.text = "发布者: ${announcementDetail.posterUsername ?: "未知"}"
        authorNameTextView.visibility = View.VISIBLE
        
        authorAvatarImageView.visibility = View.VISIBLE
        if (!announcementDetail.posterAvatar.isNullOrEmpty()) {
            try {
                Glide.with(this)
                    .load(announcementDetail.posterAvatar)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(authorAvatarImageView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showProgress() {
        progressBar.visibility = View.VISIBLE
        findViewById<ScrollView>(R.id.scroll_view).visibility = View.GONE
    }

    private fun hideProgress() {
        progressBar.visibility = View.GONE
        findViewById<ScrollView>(R.id.scroll_view).visibility = View.VISIBLE
    }

    private fun showError(error: String) {
        runOnUiThread {
            Toast.makeText(this, "加载公告详情失败: $error", Toast.LENGTH_SHORT).show()
            // 可以添加空状态布局
        }
    }
}
