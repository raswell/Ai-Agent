package com.homeos.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var appGrid: LinearLayout
    private lateinit var widgetWeb: WebView
    private lateinit var statusText: TextView
    private val prefs by lazy { getSharedPreferences("homeos", MODE_PRIVATE) }
    private var apps: List<LaunchableApp> = emptyList()
    private var currentConfig = JSONObject()
    private var currentPageId = "home"

    data class LaunchableApp(val label: String, val packageName: String, val activityName: String, val icon: Drawable)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { renderApps() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        buildLayout()
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageReceiver, packageFilter)
        }
        loadCachedConfig()
        renderApps()
        syncConfig()
    }

    private fun buildLayout() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(42, 24, 42, 22); setBackgroundColor(Color.rgb(11, 20, 32)); isFocusable = true }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply { text = "HOMEOS"; textSize = 25f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }
        statusText = TextView(this).apply { text = "LOCAL MODE"; textSize = 12f; setTextColor(Color.rgb(139, 190, 220)); gravity = Gravity.CENTER_VERTICAL }
        val settings = makeButton("SETTINGS") { showSettings() }
        val refresh = makeButton("SYNC") { syncConfig() }
        header.addView(brand, LinearLayout.LayoutParams(0, 64, 1f)); header.addView(statusText); header.addView(settings); header.addView(refresh)
        root.addView(header)
        root.addView(sectionTitle("TV APPLICATIONS"))
        val appScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS }
        appGrid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isFocusable = true }
        appScroll.addView(appGrid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(appScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150))
        root.addView(sectionTitle("HOMEOS PAGES"))
        val pageBar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val pageRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; tag = "page_bar" }
        pageBar.addView(pageRow)
        root.addView(pageBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 58))
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        widgetWeb = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = true
            isFocusableInTouchMode = true
            this.settings.javaScriptEnabled = true
            this.settings.domStorageEnabled = false
            this.settings.allowFileAccess = false
            this.settings.allowContentAccess = false
            this.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?) =
                    assetLoader.shouldInterceptRequest(request?.url ?: Uri.EMPTY)
                override fun onPageFinished(view: WebView?, url: String?) { renderWidgets() }
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
            }
            loadUrl("https://appassets.androidplatform.net/assets/widgets.html")
        }
        root.addView(widgetWeb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        root.isFocusableInTouchMode = true
        root.requestFocus()
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value; textSize = 13f; letterSpacing = 0.12f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.rgb(157, 190, 213)); setPadding(2, 14, 0, 8)
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 10f; isFocusable = true; setOnClickListener { action() }
    }

    private fun renderApps() {
        apps = discoverApps()
        appGrid.removeAllViews()
        val hidden = currentConfig.optJSONObject("app_preferences")?.optJSONArray("hidden")
        val hiddenSet = (0 until (hidden?.length() ?: 0)).mapNotNull { hidden?.optString(it) }.toSet()
        val favorites = currentConfig.optJSONObject("app_preferences")?.optJSONArray("favorites")
        val favoriteSet = (0 until (favorites?.length() ?: 0)).mapNotNull { favorites?.optString(it) }.toSet()
        val visible = apps.filter { it.packageName !in hiddenSet }.sortedWith(compareByDescending<LaunchableApp> { it.packageName in favoriteSet }.thenBy { it.label.lowercase() })
        visible.forEach { app ->
            val tile = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isFocusable = true; isFocusableInTouchMode = true; setPadding(10, 6, 10, 6); background = focusBackground() }
            val icon = ImageView(this).apply { setImageDrawable(app.icon); scaleType = ImageView.ScaleType.FIT_CENTER }
            tile.addView(icon, LinearLayout.LayoutParams(64, 64))
            val name = TextView(this).apply { text = app.label; textSize = 12f; maxLines = 1; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
            tile.addView(name, LinearLayout.LayoutParams(108, 28))
            tile.setOnClickListener { launchApp(app) }
            tile.contentDescription = "Open ${app.label}"
            appGrid.addView(tile, LinearLayout.LayoutParams(126, 126).apply { setMargins(5, 3, 5, 3) })
        }
        if (visible.isEmpty()) {
            val empty = TextView(this).apply { text = "No launchable apps found"; setTextColor(Color.LTGRAY); textSize = 16f }
            appGrid.addView(empty)
        }
    }

    private fun focusBackground() = android.graphics.drawable.StateListDrawable().apply {
        val focused = android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(42, 75, 101)); cornerRadius = 18f; setStroke(3, Color.rgb(85, 194, 255)) }
        val normal = android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(25, 43, 60)); cornerRadius = 18f }
        addState(intArrayOf(android.R.attr.state_focused), focused); addState(intArrayOf(), normal)
    }

    @Suppress("DEPRECATION")
    private fun discoverApps(): List<LaunchableApp> {
        val result = LinkedHashMap<String, LaunchableApp>()
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        categories.forEach { category ->
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            val matches = if (Build.VERSION.SDK_INT >= 33) packageManager.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())) else packageManager.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
            matches.forEach { info ->
                val activity = info.activityInfo ?: return@forEach
                if (activity.packageName == packageName) return@forEach
                val key = "${activity.packageName}/${activity.name}"
                if (!result.containsKey(key)) result[key] = LaunchableApp(info.loadLabel(packageManager).toString(), activity.packageName, activity.name, info.loadIcon(packageManager))
            }
        }
        return result.values.toList()
    }

    private fun launchApp(app: LaunchableApp) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(app.packageName, app.activityName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            try { startActivity(packageManager.getLaunchIntentForPackage(app.packageName) ?: return) } catch (_: Exception) { statusText.text = "APP UNAVAILABLE" }
        }
    }

    private fun loadCachedConfig() {
        val raw = prefs.getString("config", null) ?: assets.open("sample_config.json").bufferedReader().use { it.readText() }
        try { currentConfig = JSONObject(raw); renderApps(); renderPageTabs(); renderWidgets() } catch (_: Exception) { currentConfig = JSONObject() }
    }

    private fun syncConfig() {
        val base = prefs.getString("server_url", "http://192.168.254.54:8787")!!.trimEnd('/')
        val token = prefs.getString("api_token", "change-me-before-network")!!
        statusText.text = "SYNCING…"
        io.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("$base/api/v1/device/config").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500; readTimeout = 4500; requestMethod = "GET"; setRequestProperty("Authorization", "Bearer $token")
                }
                if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = JSONObject(body)
                require(parsed.optInt("schema_version", 0) == 1) { "Unsupported schema" }
                val pages = parsed.optJSONArray("pages") ?: throw IllegalArgumentException("Missing pages")
                require(pages.length() > 0) { "No pages configured" }
                val supportedWidgets = setOf("text", "status", "clock", "weather", "button", "spacer")
                for (i in 0 until pages.length()) {
                    val widgets = pages.optJSONObject(i)?.optJSONArray("widgets") ?: continue
                    for (j in 0 until widgets.length()) {
                        val type = widgets.optJSONObject(j)?.optString("type") ?: ""
                        require(type in supportedWidgets) { "Unsupported widget: $type" }
                    }
                }
                prefs.edit().putString("config", parsed.toString()).apply()
                runOnUiThread {
                    currentConfig = parsed; renderApps(); renderPageTabs(); renderWidgets(); statusText.text = "PI ONLINE"
                }
            } catch (_: Exception) {
                runOnUiThread { statusText.text = "OFFLINE · CACHED" }
            } finally { connection?.disconnect() }
        }
    }

    private fun renderPageTabs() {
        val pageBar = findViewWithTag<LinearLayout>(root, "page_bar") ?: return
        pageBar.removeAllViews()
        val pages = currentConfig.optJSONArray("pages") ?: return
        for (i in 0 until pages.length()) {
            val page = pages.optJSONObject(i) ?: continue
            val id = page.optString("id", "home")
            val label = page.optString("title", id)
            val button = makeButton(label) { currentPageId = id; renderWidgets() }
            pageBar.addView(button)
        }
    }

    private fun renderWidgets() {
        if (!::widgetWeb.isInitialized) return
        val pages = currentConfig.optJSONArray("pages") ?: return
        var selected: JSONObject? = null
        for (i in 0 until pages.length()) if (pages.optJSONObject(i)?.optString("id") == currentPageId) selected = pages.optJSONObject(i)
        if (selected == null) { currentPageId = "home"; for (i in 0 until pages.length()) if (pages.optJSONObject(i)?.optString("id") == "home") selected = pages.optJSONObject(i) }
        val payload = JSONObject().put("theme", currentConfig.optJSONObject("theme") ?: JSONObject()).put("page", selected ?: JSONObject()).toString()
        widgetWeb.post { widgetWeb.evaluateJavascript("window.HomeOS && window.HomeOS.render(${JSONObject.quote(payload)});", null) }
    }

    private fun showSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 8, 32, 0) }
        val url = EditText(this).apply { hint = "http://192.168.254.54:8787"; setSingleLine(true); setText(prefs.getString("server_url", "http://192.168.254.54:8787")) }
        val token = EditText(this).apply { hint = "API token"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD; setSingleLine(true); setText(prefs.getString("api_token", "change-me-before-network")) }
        box.addView(url); box.addView(token)
        AlertDialog.Builder(this).setTitle("HomeOS Server").setView(box)
            .setPositiveButton("SAVE & SYNC") { _, _ -> prefs.edit().putString("server_url", url.text.toString().trim()).putString("api_token", token.text.toString()).apply(); syncConfig() }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun <T : View> findViewWithTag(parent: ViewGroup, tag: String): T? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.tag == tag) @Suppress("UNCHECKED_CAST") return child as T
            if (child is ViewGroup) findViewWithTag<T>(child, tag)?.let { return it }
        }
        return null
    }

    override fun onResume() { super.onResume(); if (::root.isInitialized) renderApps() }
    override fun onDestroy() { try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}; io.shutdownNow(); if (::widgetWeb.isInitialized) widgetWeb.destroy(); super.onDestroy() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { showSettings(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
