package com.example.hello

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.hello.activity.*
import java.util.concurrent.ConcurrentHashMap
import androidx.core.net.toUri
import androidx.core.app.TaskStackBuilder

@SuppressLint("StaticFieldLeak")
object NavigationManager {
    private lateinit var context: Context
    private val pathMap = ConcurrentHashMap<String, Class<*>>()
    private const val TAG = "NavigationManager"

    /**
     * 初始化导航管理器
     */
    fun init(application: Application) {
        context = application.applicationContext
        registerDefaultPaths()
    }

    /**
     * 注册默认页面路径
     */
    private fun registerDefaultPaths() {
        registerPath("/main", MainActivity::class.java)
        registerPath("/announcement", AnnouncementActivity::class.java)
        registerPath("/announcement/detail", AnnouncementDetailActivity::class.java)
        registerPath("/lab", LabActivity::class.java)
        registerPath("/lab/rate-monitor", RateMonitorActivity::class.java)
        registerPath("/settings", SettingsActivity::class.java)
        registerPath("/verification", VerificationActivity::class.java)
        registerPath("/about", AboutActivity::class.java)
        registerPath("/scan", ScanLoginActivity::class.java)
    }

    /**
     * 注册页面路径
     * @param path 路径，如 "/main"
     * @param activityClass Activity类
     */
    fun registerPath(path: String, activityClass: Class<*>) {
        pathMap[path] = activityClass
        Log.d(TAG, "Registered path: $path -> ${activityClass.simpleName}")
    }

    /**
     * 根据路径跳转页面
     * @param context 上下文
     * @param path 路径，如 "/main?param1=value1&param2=value2" 或 "classhopper://announcement" 或 "https://example.com"
     * @return 是否跳转成功
     */
    fun navigate(context: Context, path: String): Boolean {
        Log.d(TAG, "Navigating to path: $path")

        val appBase = "https://classhopper.com/app/"
        val normalizedPath = if (path.startsWith(appBase)) {
            val raw = path.removePrefix(appBase)
            val normalized = if (raw.startsWith("/")) raw else "/$raw"
            if (normalized == "/") "/main" else normalized
        } else {
            path
        }

        // 处理http/https URL
        if (normalizedPath.startsWith("http://") || normalizedPath.startsWith("https://")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = normalizedPath.toUri()
                // 如果上下文不是Activity，需要添加FLAG_ACTIVITY_NEW_TASK
                if (context !is android.app.Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to navigate to web URL $path: ${e.message}", e)
                return false
            }
        }

        // 处理自定义协议URL
        val processedPath = if (normalizedPath.startsWith("classhopper://")) {
            // 提取协议后面的路径部分，如 "classhopper://announcement" -> "/announcement"
            "/" + normalizedPath.substring("classhopper://".length)
        } else {
            normalizedPath
        }

        // 解析路径和参数
        val (pathWithoutParams, params) = parsePathAndParams(processedPath)

        // 获取对应的Activity类
        val activityClass = pathMap[pathWithoutParams]
        if (activityClass == null) {
            Log.e(TAG, "No activity registered for path: $pathWithoutParams")
            return false
        }

        val intent = Intent(context, activityClass)
        params.forEach { (key, value) ->
            intent.putExtra(key, value)
        }

        try {
            val needsBackStack = activityClass != MainActivity::class.java &&
                (context !is android.app.Activity || context.isTaskRoot)
            if (needsBackStack) {
                val stackBuilder = TaskStackBuilder.create(context)
                    .addNextIntent(Intent(context, MainActivity::class.java))
                    .addNextIntent(intent)
                stackBuilder.startActivities()
                return true
            }
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to $path: ${e.message}", e)
            return false
        }
    }

    /**
     * 解析路径和参数
     * @param path 完整路径，如 "/main?param1=value1&param2=value2"
     * @return Pair(路径, 参数映射)
     */
    private fun parsePathAndParams(path: String): Pair<String, Map<String, String>> {
        val paramsMap = mutableMapOf<String, String>()
        val splitIndex = path.indexOf('?')

        if (splitIndex == -1) {
            return Pair(path, emptyMap())
        }

        val pathWithoutParams = path.take(splitIndex)
        val paramsString = path.substring(splitIndex + 1)

        paramsString.split('&').forEach { paramPair ->
            val (key, value) = paramPair.split('=', limit = 2)
            paramsMap[key] = value
        }

        return Pair(pathWithoutParams, paramsMap)
    }

    /**
     * 获取所有已注册的路径
     */
    fun getAllRegisteredPaths(): List<String> {
        return pathMap.keys.toList()
    }
}
