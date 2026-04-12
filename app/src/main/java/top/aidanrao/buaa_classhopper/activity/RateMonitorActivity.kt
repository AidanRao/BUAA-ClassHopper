package top.aidanrao.buaa_classhopper.activity

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import top.aidanrao.buaa_classhopper.R
import top.aidanrao.buaa_classhopper.data.api.RateMonitorRuleRequest
import top.aidanrao.buaa_classhopper.data.model.dto.RateMonitorRuleDto
import top.aidanrao.buaa_classhopper.viewmodel.RateMonitorViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RateMonitorActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var ruleListView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyTextView: TextView
    private lateinit var fabAdd: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var ruleAdapter: RuleAdapter

    private val viewModel: RateMonitorViewModel by viewModels()
    private val rules = mutableListOf<RateMonitorRuleDto>()
    private val currencyMap = mutableMapOf<String, String>()
    private var editingRule: RateMonitorRuleDto? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rate_monitor)

        initViews()
        initObservers()
        loadData()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        ruleListView = findViewById(R.id.rule_list)
        progressBar = findViewById(R.id.progress_bar)
        emptyTextView = findViewById(R.id.empty_text)
        fabAdd = findViewById(R.id.fab_add)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "汇率监控"

        toolbar.setNavigationOnClickListener { finish() }

        ruleAdapter = RuleAdapter(rules)
        ruleListView.adapter = ruleAdapter

        fabAdd.setOnClickListener {
            editingRule = null
            showAddEditDialog()
        }
    }

    private fun initObservers() {
        viewModel.currencies.observe(this) { currencies ->
            currencyMap.clear()
            currencyMap.putAll(currencies)
        }

        viewModel.rules.observe(this) { ruleList ->
            rules.clear()
            rules.addAll(ruleList)
            ruleAdapter.notifyDataSetChanged()

            if (rules.isEmpty()) {
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

        viewModel.operationSuccess.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "操作成功", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadData() {
        viewModel.loadCurrencies()
        viewModel.loadRules()
    }

    private fun showProgress() {
        progressBar.visibility = View.VISIBLE
        ruleListView.visibility = View.GONE
        emptyTextView.visibility = View.GONE
    }

    private fun hideProgress() {
        progressBar.visibility = View.GONE
        ruleListView.visibility = View.VISIBLE
    }

    private fun showEmptyState() {
        emptyTextView.visibility = View.VISIBLE
        ruleListView.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    private fun showError(error: String) {
        Toast.makeText(this, "操作失败: $error", Toast.LENGTH_SHORT).show()
    }

    private fun showAddEditDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_edit_rule, null)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)
        val editEmail = dialogView.findViewById<EditText>(R.id.edit_email)
        val spinnerBaseCurrency = dialogView.findViewById<Spinner>(R.id.spinner_base_currency)
        val spinnerQuoteCurrency = dialogView.findViewById<Spinner>(R.id.spinner_quote_currency)
        val editThresholdLower = dialogView.findViewById<EditText>(R.id.edit_threshold_lower)
        val editThresholdUpper = dialogView.findViewById<EditText>(R.id.edit_threshold_upper)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btn_confirm)

        val currencyCodes = currencyMap.keys.toList()
        val currencyNames = currencyCodes.map { "${currencyMap[it] ?: it} ($it)" }
        val currencyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencyNames)
        currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBaseCurrency.adapter = currencyAdapter
        spinnerQuoteCurrency.adapter = currencyAdapter

        if (editingRule != null) {
            dialogTitle.text = "编辑监控规则"
            editEmail.setText(editingRule!!.email)
            editThresholdLower.setText(editingRule!!.thresholds.absoluteLower.toString())
            editThresholdUpper.setText(editingRule!!.thresholds.absoluteUpper.toString())

            val baseIndex = currencyCodes.indexOf(editingRule!!.baseCurrency)
            val quoteIndex = currencyCodes.indexOf(editingRule!!.quoteCurrency)
            if (baseIndex >= 0) spinnerBaseCurrency.setSelection(baseIndex)
            if (quoteIndex >= 0) spinnerQuoteCurrency.setSelection(quoteIndex)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val email = editEmail.text.toString().trim()
            val baseCurrency = currencyCodes.getOrNull(spinnerBaseCurrency.selectedItemPosition) ?: ""
            val quoteCurrency = currencyCodes.getOrNull(spinnerQuoteCurrency.selectedItemPosition) ?: ""
            val thresholdLower = editThresholdLower.text.toString().trim()
            val thresholdUpper = editThresholdUpper.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "请输入邮箱地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (baseCurrency.isEmpty() || quoteCurrency.isEmpty()) {
                Toast.makeText(this, "请选择货币", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (thresholdLower.isEmpty()) {
                Toast.makeText(this, "请输入下限阈值", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = RateMonitorRuleRequest(
                email = email,
                baseCurrency = baseCurrency,
                quoteCurrency = quoteCurrency,
                absoluteLower = thresholdLower,
                absoluteUpper = if (thresholdUpper.isNotEmpty()) thresholdUpper else null
            )

            if (editingRule != null) {
                viewModel.updateRule(editingRule!!.id, request)
            } else {
                viewModel.addRule(request)
            }

            dialog.dismiss()
        }

        dialog.show()
    }

    private inner class RuleAdapter(private val rules: List<RateMonitorRuleDto>) : BaseAdapter() {

        override fun getCount(): Int = rules.size

        override fun getItem(position: Int): Any = rules[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_rate_monitor_rule, parent, false)

            val rule = rules[position]

            val currencyPairText = view.findViewById<TextView>(R.id.currency_pair)
            val statusBadge = view.findViewById<TextView>(R.id.status_badge)
            val emailText = view.findViewById<TextView>(R.id.email_text)
            val thresholdUpper = view.findViewById<TextView>(R.id.threshold_upper)
            val thresholdLower = view.findViewById<TextView>(R.id.threshold_lower)
            val btnEdit = view.findViewById<Button>(R.id.btn_edit)
            val btnToggleStatus = view.findViewById<Button>(R.id.btn_toggle_status)

            val baseName = currencyMap[rule.baseCurrency] ?: rule.baseCurrency
            val quoteName = currencyMap[rule.quoteCurrency] ?: rule.quoteCurrency
            currencyPairText.text = "${rule.baseCurrency}/${rule.quoteCurrency} ($baseName/$quoteName)"

            statusBadge.text = if (rule.active) "活跃" else "停用"
            statusBadge.setBackgroundColor(
                if (rule.active) resources.getColor(android.R.color.holo_green_light)
                else resources.getColor(android.R.color.darker_gray)
            )

            emailText.text = "通知邮箱: ${rule.email}"
            thresholdUpper.text = "上限: ${rule.thresholds.absoluteUpper}"
            thresholdLower.text = "下限: ${rule.thresholds.absoluteLower}"

            btnToggleStatus.text = if (rule.active) "停用" else "启用"
            btnToggleStatus.setBackgroundColor(
                if (rule.active) resources.getColor(android.R.color.holo_orange_dark)
                else resources.getColor(android.R.color.holo_green_light)
            )

            btnToggleStatus.setOnClickListener {
                viewModel.toggleRuleStatus(rule)
            }

            btnEdit.setOnClickListener {
                editingRule = rule
                showAddEditDialog()
            }

            return view
        }
    }
}
