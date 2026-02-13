package com.example.hello.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.example.hello.service.ApiService
import com.example.hello.R

class AnnouncementActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var announcementListView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyTextView: TextView
    private lateinit var apiService: ApiService
    private lateinit var announcementAdapter: AnnouncementAdapter

    private val announcements = mutableListOf<ApiService.AnnouncementData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcement)

        initViews()
        initApiService()
        loadAnnouncements()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        announcementListView = findViewById(R.id.announcement_list)
        progressBar = findViewById(R.id.progress_bar)
        emptyTextView = findViewById(R.id.empty_text)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "公告"

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        announcementAdapter = AnnouncementAdapter(announcements)
        announcementListView.adapter = announcementAdapter

        announcementListView.setOnItemClickListener { _, _, position, _ ->
            // 点击公告项，跳转到详情页
            val announcement = announcements[position]
            val intent = Intent(this, AnnouncementDetailActivity::class.java)
            intent.putExtra("ANNOUNCEMENT_ID", announcement.id)
            startActivity(intent)
        }
    }

    private fun initApiService() {
        apiService = ApiService(this)
    }

    private fun loadAnnouncements() {
        showProgress()
        
        // 获取token并加载公告
        apiService.getValidToken(object : ApiService.OnAuthListener {
            override fun onSuccess(token: String, expireAt: Long) {
                apiService.getAnnouncements(token, object : ApiService.OnAnnouncementsListener {
                    override fun onSuccess(announcementList: List<ApiService.AnnouncementData>) {
                        runOnUiThread {
                            hideProgress()
                            announcements.clear()
                            announcements.addAll(announcementList)
                            announcementAdapter.notifyDataSetChanged()
                            
                            if (announcements.isEmpty()) {
                                showEmptyState()
                            }
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

    private fun showProgress() {
        progressBar.visibility = View.VISIBLE
        announcementListView.visibility = View.GONE
        emptyTextView.visibility = View.GONE
    }

    private fun hideProgress() {
        progressBar.visibility = View.GONE
        announcementListView.visibility = View.VISIBLE
    }

    private fun showEmptyState() {
        emptyTextView.visibility = View.VISIBLE
        announcementListView.visibility = View.GONE
    }

    private fun showError(error: String) {
        runOnUiThread {
            Toast.makeText(this, "加载公告失败: $error", Toast.LENGTH_SHORT).show()
            showEmptyState()
        }
    }

    override fun onBackPressed() {
        // 检查当前Activity是否是任务栈中的根Activity
        if (isTaskRoot) {
            // 如果是根Activity，跳转到MainActivity而不是退出
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            // 否则正常返回上一级
            super.onBackPressed()
        }
    }

    private inner class AnnouncementAdapter(private val announcements: List<ApiService.AnnouncementData>) : BaseAdapter() {

        override fun getCount(): Int {
            return announcements.size
        }

        override fun getItem(position: Int): Any {
            return announcements[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_announcement, parent, false)
            
            val announcement = announcements[position]
            
            val coverImage = view.findViewById<ImageView>(R.id.announcement_cover)
            val titleTextView = view.findViewById<TextView>(R.id.announcement_title)
            val timeTextView = view.findViewById<TextView>(R.id.announcement_time)
            
            titleTextView.text = announcement.title
            timeTextView.text = announcement.createTime.substringBefore('T')
            
            // 加载封面图片
            if (!announcement.cover.isNullOrEmpty()) {
                try {
                    Glide.with(this@AnnouncementActivity)
                        .load(announcement.cover)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(coverImage)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            return view
        }
    }
}
