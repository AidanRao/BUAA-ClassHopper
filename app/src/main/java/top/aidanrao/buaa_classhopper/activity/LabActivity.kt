package top.aidanrao.buaa_classhopper.activity

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import top.aidanrao.buaa_classhopper.R
import top.aidanrao.buaa_classhopper.NavigationManager
import top.aidanrao.buaa_classhopper.viewmodel.LabViewModel
import dagger.hilt.android.AndroidEntryPoint

data class LabFeature(
    val code: String,
    val name: String,
    val description: String
)

@AndroidEntryPoint
class LabActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var featureListView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyTextView: TextView
    private lateinit var featureAdapter: FeatureAdapter

    private val viewModel: LabViewModel by viewModels()
    private val features = mutableListOf<LabFeature>()

    private val featureMap = mapOf(
        "rate-monitor" to LabFeature(
            code = "rate-monitor",
            name = "汇率监控",
            description = "设置汇率阈值，超过时邮件通知"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lab)

        initViews()
        initObservers()
        loadFeatures()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        featureListView = findViewById(R.id.feature_list)
        progressBar = findViewById(R.id.progress_bar)
        emptyTextView = findViewById(R.id.empty_text)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "实验室"

        toolbar.setNavigationOnClickListener { finish() }

        featureAdapter = FeatureAdapter(features)
        featureListView.adapter = featureAdapter

        featureListView.setOnItemClickListener { _, _, position, _ ->
            val feature = features[position]
            when (feature.code) {
                "rate-monitor" -> {
                    NavigationManager.navigate(this, "/lab/rate-monitor")
                }
            }
        }
    }

    private fun initObservers() {
        viewModel.features.observe(this) { featureCodes ->
            features.clear()
            featureCodes.forEach { code ->
                featureMap[code]?.let { features.add(it) }
            }
            featureAdapter.notifyDataSetChanged()

            if (features.isEmpty()) {
                showEmptyState()
            } else {
                hideProgress()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                showProgress()
            }
        }

        viewModel.error.observe(this) { errorMsg ->
            showError(errorMsg)
        }
    }

    private fun loadFeatures() {
        viewModel.loadFeatures()
    }

    private fun showProgress() {
        progressBar.visibility = View.VISIBLE
        featureListView.visibility = View.GONE
        emptyTextView.visibility = View.GONE
    }

    private fun hideProgress() {
        progressBar.visibility = View.GONE
        featureListView.visibility = View.VISIBLE
    }

    private fun showEmptyState() {
        emptyTextView.visibility = View.VISIBLE
        featureListView.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    private fun showError(error: String) {
        Toast.makeText(this, "加载功能列表失败: $error", Toast.LENGTH_SHORT).show()
        showEmptyState()
    }

    private inner class FeatureAdapter(private val features: List<LabFeature>) : BaseAdapter() {

        override fun getCount(): Int = features.size

        override fun getItem(position: Int): Any = features[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_lab_feature, parent, false)

            val feature = features[position]

            val iconImage = view.findViewById<ImageView>(R.id.feature_icon)
            val nameTextView = view.findViewById<TextView>(R.id.feature_name)
            val descTextView = view.findViewById<TextView>(R.id.feature_description)

            nameTextView.text = feature.name
            descTextView.text = feature.description

            when (feature.code) {
                "rate-monitor" -> iconImage.setImageResource(R.drawable.ic_rate_monitor)
                else -> iconImage.setImageResource(android.R.drawable.ic_menu_manage)
            }

            return view
        }
    }
}
