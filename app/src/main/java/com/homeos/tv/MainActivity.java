package com.homeos.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "homeos_tv";
    private static final String DEFAULT_SERVER = "http://192.168.254.54:8081";
    private static final String SHELL_VERSION = "1.0.0";
    private WebView webView;
    private File runtimeDir;
    private RuntimeUpdater updater;
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable periodicUpdate = new Runnable() { @Override public void run() { if (updater != null) updater.checkForUpdate((changed, message) -> { if (changed && webView != null) runOnUiThread(() -> webView.reload()); }); updateHandler.postDelayed(this, 5 * 60 * 1000L); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(9, 13, 22));
        getWindow().setNavigationBarColor(Color.rgb(9, 13, 22));
        runtimeDir = new File(getFilesDir(), "runtime/current");
        ensureInitialRuntime();
        updater = new RuntimeUpdater(this, runtimeDir);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(9, 13, 22));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setDatabaseEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/runtime/", new RuntimePathHandler(runtimeDir))
                .addPathHandler("/api/", new HomeOSApiPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equals(uri.getScheme()) || !"appassets.androidplatform.net".equals(uri.getHost())) {
                    return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", Collections.emptyMap(), null);
                }
                return assetLoader.shouldInterceptRequest(uri);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !"https".equals(uri.getScheme()) || !"appassets.androidplatform.net".equals(uri.getHost());
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new HomeOSBridge(), "HomeOSNative");
        setContentView(webView);
        webView.loadUrl("https://appassets.androidplatform.net/runtime/index.html");

        // Check for a runtime update after the local launcher is already visible.
        updater.checkForUpdate(new RuntimeUpdater.Callback() {
            @Override public void onFinished(boolean changed, String message) {
                if (changed && webView != null) runOnUiThread(() -> webView.reload());
            }
        });
    }

    private void ensureInitialRuntime() {
        if (new File(runtimeDir, "index.html").exists()) return;
        File backup = new File(runtimeDir.getParentFile(), "previous");
        if (new File(backup, "index.html").exists()) {
            // Recover the last known runtime if an update was interrupted between directory swaps.
            //noinspection ResultOfMethodCallIgnored
            backup.renameTo(runtimeDir);
            if (new File(runtimeDir, "index.html").exists()) return;
        }
        copyAssetTree("runtime", runtimeDir);
    }

    private void copyAssetTree(String assetPath, File destination) {
        try {
            if (!destination.exists() && !destination.mkdirs()) return;
            String[] children = getAssets().list(assetPath);
            if (children == null || children.length == 0) {
                try (InputStream in = getAssets().open(assetPath); FileOutputStream out = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                }
                return;
            }
            for (String child : children) copyAssetTree(assetPath + "/" + child, new File(destination, child));
        } catch (Exception ignored) { }
    }

    @Override protected void onResume() { super.onResume(); updateHandler.removeCallbacks(periodicUpdate); updateHandler.postDelayed(periodicUpdate, 5 * 60 * 1000L); }

    @Override protected void onPause() { updateHandler.removeCallbacks(periodicUpdate); super.onPause(); }

    @Override public void onBackPressed() {
        if (webView != null) webView.evaluateJavascript("window.HomeOS && HomeOS.handleBack && HomeOS.handleBack()", null);
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        updateHandler.removeCallbacks(periodicUpdate);
        if (webView != null) { webView.removeJavascriptInterface("HomeOSNative"); webView.destroy(); webView = null; }
        super.onDestroy();
    }

    private String serverBase() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String value = p.getString("server", DEFAULT_SERVER);
        return value == null ? DEFAULT_SERVER : value.replaceAll("/+$", "");
    }

    public final class HomeOSBridge {
        @JavascriptInterface public String getShellVersion() { return SHELL_VERSION; }
        @JavascriptInterface public String getServerBase() { return serverBase(); }
        @JavascriptInterface public String getRuntimeVersion() { return RuntimeUpdater.readRuntimeVersion(runtimeDir); }
        @JavascriptInterface public String getAppsJson() { return installedAppsJson(); }
        @JavascriptInterface public void launchApp(String packageName) {
            if (packageName == null || packageName.length() > 200) return;
            runOnUiThread(() -> {
                try {
                    Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
                    if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launch != null) { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launch); }
                } catch (Exception ignored) { }
            });
        }
        @JavascriptInterface public void setServerBase(String value) {
            if (value == null || value.length() > 300 || !(value.startsWith("http://") || value.startsWith("https://"))) return;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("server", value.replaceAll("/+$", "")).apply();
        }
        @JavascriptInterface public void refreshRuntime() {
            updater.checkForUpdate((changed, message) -> { if (changed && webView != null) runOnUiThread(() -> webView.reload()); });
        }
    }

    private String installedAppsJson() {
        JSONArray out = new JSONArray();
        try {
            PackageManager pm = getPackageManager();
            java.util.LinkedHashMap<String, ResolveInfo> unique = new java.util.LinkedHashMap<>();
            Intent tvQuery = new Intent(Intent.ACTION_MAIN, null); tvQuery.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo info : pm.queryIntentActivities(tvQuery, 0)) unique.put(info.activityInfo.packageName, info);
            Intent phoneQuery = new Intent(Intent.ACTION_MAIN, null); phoneQuery.addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo info : pm.queryIntentActivities(phoneQuery, 0)) unique.putIfAbsent(info.activityInfo.packageName, info);
            List<ResolveInfo> apps = new java.util.ArrayList<>(unique.values());
            Collections.sort(apps, Comparator.comparing(a -> String.valueOf(a.loadLabel(pm)).toLowerCase(Locale.ROOT)));
            for (ResolveInfo info : apps) {
                JSONObject item = new JSONObject();
                item.put("label", String.valueOf(info.loadLabel(pm)));
                item.put("packageName", info.activityInfo.packageName);
                out.put(item);
            }
        } catch (Exception ignored) { }
        return out.toString();
    }


    private static final class HomeOSApiPathHandler implements WebViewAssetLoader.PathHandler {
        private final Context context;
        HomeOSApiPathHandler(Context context) { this.context = context.getApplicationContext(); }
        @Override public WebResourceResponse handle(String path) {
            if (!"health".equals(path)) return new WebResourceResponse("application/json", "UTF-8", 404, "Not Found", Collections.emptyMap(), null);
            HttpURLConnection connection = null;
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
                String base = prefs.getString("server", DEFAULT_SERVER).replaceAll("/+$", "");
                connection = (HttpURLConnection) new java.net.URL(base + "/health").openConnection();
                connection.setConnectTimeout(2500); connection.setReadTimeout(2500); connection.setRequestMethod("GET");
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
                if (stream == null) throw new Exception("Empty health response");
                byte[] bytes;
                try (InputStream in = stream; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[2048]; int n; int total = 0;
                    while ((n = in.read(buffer)) != -1) { total += n; if (total > 65536) throw new Exception("Health response too large"); out.write(buffer, 0, n); }
                    bytes = out.toByteArray();
                }
                return new WebResourceResponse("application/json", "UTF-8", status, status >= 200 && status < 400 ? "OK" : "Upstream Error", Collections.emptyMap(), new java.io.ByteArrayInputStream(bytes));
            } catch (Exception e) {
                byte[] body = "{\"status\":\"offline\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return new WebResourceResponse("application/json", "UTF-8", 502, "Bad Gateway", Collections.emptyMap(), new java.io.ByteArrayInputStream(body));
            } finally { if (connection != null) connection.disconnect(); }
        }
    }

    private static final class RuntimePathHandler implements WebViewAssetLoader.PathHandler {
        private final File root;
        RuntimePathHandler(File root) { this.root = root; }
        @Override public WebResourceResponse handle(String path) {
            try {
                File file = new File(root, path).getCanonicalFile();
                String rootPath = root.getCanonicalPath() + File.separator;
                if (!file.getPath().startsWith(rootPath) || !file.isFile()) return notFound();
                String mime = URLConnection.guessContentTypeFromName(file.getName());
                if (mime == null) {
                    String n = file.getName().toLowerCase(Locale.ROOT);
                    if (n.endsWith(".js")) mime = "application/javascript";
                    else if (n.endsWith(".css")) mime = "text/css";
                    else if (n.endsWith(".json")) mime = "application/json";
                    else if (n.endsWith(".svg")) mime = "image/svg+xml";
                    else if (n.endsWith(".html")) mime = "text/html";
                    else mime = "application/octet-stream";
                }
                return new WebResourceResponse(mime, "UTF-8", new FileInputStream(file));
            } catch (Exception e) { return notFound(); }
        }
        private WebResourceResponse notFound() { return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", Collections.emptyMap(), null); }
    }
}
