package com.homeos.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "homeos_tv_settings";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_PATH = "path";
    private static final String DEFAULT_HOST = "192.168.254.54";
    private static final String DEFAULT_PORT = "8081";
    private static final String DEFAULT_PATH = "/health";
    private static final int BG = Color.rgb(10, 15, 24);
    private static final int PANEL = Color.rgb(22, 31, 45);
    private static final int PANEL_FOCUS = Color.rgb(35, 58, 68);
    private static final int FG = Color.rgb(238, 243, 250);
    private static final int MUTED = Color.rgb(151, 166, 187);
    private static final int ACCENT = Color.rgb(88, 190, 166);
    private static final int WARN = Color.rgb(238, 174, 100);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private TextView clock, date, state, endpoint, response, details;
    private String host, port, path;
    private boolean requestRunning = false;
    private boolean destroyed = false;
    private long lastSuccess = 0L;

    private final Runnable clockTask = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            java.text.SimpleDateFormat tf = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("EEEE، d MMMM yyyy", new Locale("fa"));
            clock.setText(tf.format(new java.util.Date()));
            date.setText(df.format(new java.util.Date()));
            main.postDelayed(this, 1000);
        }
    };

    private final Runnable healthTask = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            checkHealth(false);
            main.postDelayed(this, 15000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        host = prefs.getString(KEY_HOST, DEFAULT_HOST);
        port = prefs.getString(KEY_PORT, DEFAULT_PORT);
        path = prefs.getString(KEY_PATH, DEFAULT_PATH);
        buildUi();
        main.post(clockTask);
        main.postDelayed(healthTask, 700);
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable bg(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(16));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView label(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setIncludeFontPadding(true);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout page = column();
        page.setPadding(dp(42), dp(28), dp(42), dp(32));
        scroll.addView(page);
        setContentView(scroll);

        LinearLayout brand = row();
        brand.addView(label("HOMEOS", 20, ACCENT, true));
        brand.addView(label("   TV CONTROL CENTER", 13, MUTED, true));
        page.addView(brand);

        LinearLayout hero = row();
        LinearLayout time = column();
        clock = label("--:--", 58, FG, true);
        date = label("در حال آماده‌سازی", 16, MUTED, false);
        time.addView(clock);
        time.addView(date);
        hero.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout status = column();
        status.setGravity(Gravity.END);
        state = label("●  در انتظار اتصال", 18, WARN, true);
        endpoint = label("", 14, MUTED, false);
        status.addView(state);
        LinearLayout.LayoutParams ep = lp(-2, -2); ep.topMargin = dp(8);
        status.addView(endpoint, ep);
        hero.addView(status);
        LinearLayout.LayoutParams hp = lp(-1, -2); hp.topMargin = dp(34);
        page.addView(hero, hp);

        TextView section = label("وضعیت ارتباط با Hermes Raspberry", 21, FG, true);
        LinearLayout.LayoutParams sh = lp(-1, -2); sh.topMargin = dp(30);
        page.addView(section, sh);

        LinearLayout panel = column();
        panel.setPadding(dp(22), dp(20), dp(22), dp(20));
        panel.setBackground(bg(PANEL, Color.rgb(43, 58, 77)));
        response = label("هنوز پاسخی دریافت نشده است", 20, FG, true);
        details = label("پس از تنظیم سرور، آزمایش اتصال را اجرا کنید.", 15, MUTED, false);
        panel.addView(response);
        LinearLayout.LayoutParams dl = lp(-1, -2); dl.topMargin = dp(10);
        panel.addView(details, dl);
        LinearLayout.LayoutParams pp = lp(-1, -2); pp.topMargin = dp(14);
        page.addView(panel, pp);

        TextView modules = label("ماژول‌های HomeOS", 20, FG, true);
        LinearLayout.LayoutParams mp = lp(-1, -2); mp.topMargin = dp(24);
        page.addView(modules, mp);
        LinearLayout cards = row();
        addCard(cards, "HERMES CORE", "Raspberry Pi");
        addCard(cards, "SMART HOME", "دستگاه‌ها و سنسورها");
        addCard(cards, "DEVICES", "مدیریت تجهیزات");
        LinearLayout.LayoutParams cp = lp(-1, 112); cp.topMargin = dp(8);
        page.addView(cards, cp);

        LinearLayout actions = row();
        Button settings = button("تنظیم سرور", this::showSettings);
        Button test = button("آزمایش اتصال", () -> checkHealth(true));
        Button about = button("درباره", this::showAbout);
        actions.addView(settings, new LinearLayout.LayoutParams(0, dp(56), 1));
        LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(0, dp(56), 1); a2.leftMargin = dp(12);
        actions.addView(test, a2);
        LinearLayout.LayoutParams a3 = new LinearLayout.LayoutParams(0, dp(56), 1); a3.leftMargin = dp(12);
        actions.addView(about, a3);
        LinearLayout.LayoutParams ap = lp(-1, -2); ap.topMargin = dp(20);
        page.addView(actions, ap);
        TextView footer = label("HomeOS TV  •  نسخه 0.2.0  •  Android 9+  •  بدون وابستگی به ABI بومی", 13, MUTED, false);
        LinearLayout.LayoutParams fp = lp(-1, -2); fp.topMargin = dp(22);
        page.addView(footer, fp);
        settings.requestFocus();
        refreshEndpoint();
    }

    private void addCard(LinearLayout parent, String title, String sub) {
        LinearLayout c = column();
        c.setPadding(dp(15), dp(15), dp(12), dp(12));
        c.setBackground(bg(PANEL, Color.rgb(43, 58, 77)));
        c.setFocusable(true);
        c.setOnFocusChangeListener((v, f) -> v.setBackground(bg(f ? PANEL_FOCUS : PANEL, f ? ACCENT : Color.rgb(43, 58, 77))));
        c.addView(label(title, 14, ACCENT, true));
        TextView s = label(sub, 15, FG, true);
        LinearLayout.LayoutParams sp = lp(-1, -2); sp.topMargin = dp(10);
        c.addView(s, sp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1);
        cp.setMargins(dp(5), 0, dp(5), 0);
        parent.addView(c, cp);
    }

    private Button button(String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setTextColor(FG);
        b.setBackground(bg(PANEL, Color.rgb(58, 78, 100)));
        b.setFocusable(true);
        b.setOnClickListener(v -> action.run());
        b.setOnFocusChangeListener((v, f) -> v.setBackground(bg(f ? PANEL_FOCUS : PANEL, f ? ACCENT : Color.rgb(58, 78, 100))));
        return b;
    }

    private EditText field(String value, String hint, int inputType) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(FG);
        e.setHintTextColor(MUTED);
        e.setTextSize(18);
        e.setInputType(inputType);
        e.setSelectAllOnFocus(true);
        e.setPadding(dp(12), dp(6), dp(12), dp(6));
        e.setBackground(bg(Color.rgb(14, 21, 32), Color.rgb(58, 78, 100)));
        return e;
    }

    private void showSettings() {
        LinearLayout form = column();
        form.setPadding(dp(8), dp(8), dp(8), dp(4));
        form.addView(label("IP یا نام میزبان Raspberry Pi", 14, MUTED, true));
        EditText hostInput = field(host, "192.168.254.54",  android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams f1 = lp(-1, 54); f1.topMargin = dp(6); form.addView(hostInput, f1);
        TextView portLabel = label("Port", 14, MUTED, true);
        LinearLayout.LayoutParams p1 = lp(-1, -2); p1.topMargin = dp(12); form.addView(portLabel, p1);
        EditText portInput = field(port, "8081", android.text.InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams f2 = lp(-1, 54); f2.topMargin = dp(6); form.addView(portInput, f2);
        TextView pathLabel = label("مسیر Health", 14, MUTED, true);
        LinearLayout.LayoutParams p2 = lp(-1, -2); p2.topMargin = dp(12); form.addView(pathLabel, p2);
        EditText pathInput = field(path, "/health", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams f3 = lp(-1, 54); f3.topMargin = dp(6); form.addView(pathInput, f3);
        form.addView(label("مقدار پیش‌فرض با سرویس فعلی HomeOS سازگار است.", 12, MUTED, false));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("تنظیم اتصال HomeOS")
                .setView(form)
                .setNegativeButton("لغو", null)
                .setNeutralButton("بازنشانی", (d, w) -> {
                    host = DEFAULT_HOST; port = DEFAULT_PORT; path = DEFAULT_PATH;
                    persistSettings(); refreshEndpoint(); checkHealth(true);
                })
                .setPositiveButton("ذخیره و آزمایش", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String h = hostInput.getText().toString().trim();
            String p = portInput.getText().toString().trim();
            String pa = pathInput.getText().toString().trim();
            if (h.startsWith("http://")) h = h.substring(7);
            if (h.startsWith("https://")) h = h.substring(8);
            int slash = h.indexOf('/');
            if (slash >= 0) h = h.substring(0, slash);
            if (h.isEmpty() || h.contains(" ")) { hostInput.setError("IP یا نام میزبان معتبر وارد کنید"); return; }
            int portNumber;
            try { portNumber = Integer.parseInt(p); } catch (Exception ex) { portNumber = -1; }
            if (portNumber < 1 || portNumber > 65535) { portInput.setError("Port باید بین 1 تا 65535 باشد"); return; }
            if (pa.isEmpty()) pa = "/health";
            if (!pa.startsWith("/")) pa = "/" + pa;
            if (pa.contains(" ") || pa.contains("?")) { pathInput.setError("مسیر معتبر نیست"); return; }
            host = h; port = String.valueOf(portNumber); path = pa;
            persistSettings(); refreshEndpoint(); dialog.dismiss(); checkHealth(true);
        }));
        dialog.show();
    }

    private void persistSettings() {
        prefs.edit().putString(KEY_HOST, host).putString(KEY_PORT, port).putString(KEY_PATH, path).apply();
    }

    private String endpointUrl() {
        String scheme = "http";
        String h = host;
        if (h.startsWith("https://")) { scheme = "https"; h = h.substring(8); }
        else if (h.startsWith("http://")) h = h.substring(7);
        return scheme + "://" + h + ":" + port + path;
    }

    private void refreshEndpoint() {
        if (endpoint != null) endpoint.setText(endpointUrl());
    }

    private void checkHealth(boolean manual) {
        if (destroyed || requestRunning) return;
        if (host == null || host.trim().isEmpty()) { showResult(false, "سرور تنظیم نشده", "IP سرور را وارد کنید."); return; }
        final String requestUrl = endpointUrl();
        requestRunning = true;
        if (manual) showResult(false, "در حال بررسی اتصال…", requestUrl);
        network.execute(() -> {
            HttpURLConnection c = null;
            int code = -1;
            String body = "";
            String error = null;
            String contentType = "";
            try {
                URL url = new URL(requestUrl);
                c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(7000);
                c.setReadTimeout(7000);
                c.setUseCaches(false);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("Cache-Control", "no-cache");
                code = c.getResponseCode();
                contentType = c.getContentType() == null ? "" : c.getContentType();
                InputStream stream = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
                if (stream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close(); body = sb.toString();
                }
            } catch (Exception ex) {
                error = ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "بدون توضیح" : ex.getMessage());
            } finally { if (c != null) c.disconnect(); }

            final int httpCode = code;
            final String raw = body;
            final String err = error;
            final String type = contentType;
            main.post(() -> {
                requestRunning = false;
                if (destroyed) return;
                if (err != null) {
                    showResult(false, "اتصال ناموفق", err + "\nURL: " + requestUrl);
                    return;
                }
                if (httpCode < 200 || httpCode >= 300) {
                    showResult(false, "پاسخ HTTP ناموفق: " + httpCode, truncate(raw));
                    return;
                }
                try {
                    JSONObject json = new JSONObject(raw);
                    String health = json.optString("status", "");
                    if (!"ok".equalsIgnoreCase(health)) {
                        showResult(false, "پاسخ دریافت شد؛ Health تأیید نشد", "status=" + health + "\n" + truncate(raw));
                        return;
                    }
                    lastSuccess = System.currentTimeMillis();
                    String platform = json.optString("platform", "نامشخص");
                    String version = json.optString("version", "نامشخص");
                    showResult(true, "متصل و تأییدشده", "HTTP " + httpCode + "  •  platform: " + platform + "  •  version: " + version + "\n" + requestUrl);
                } catch (Exception ex) {
                    showResult(false, "پاسخ معتبر JSON نیست", "HTTP " + httpCode + "  •  " + type + "\n" + truncate(raw) + "\n" + ex.getClass().getSimpleName());
                }
            });
        });
    }

    private String truncate(String s) { return s == null ? "" : (s.length() > 500 ? s.substring(0, 500) + "…" : s); }

    private void showResult(boolean ok, String title, String info) {
        if (state == null) return;
        state.setText((ok ? "●  " : "●  ") + title);
        state.setTextColor(ok ? ACCENT : WARN);
        response.setText(title);
        details.setText(info);
    }

    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("HomeOS TV Launcher")
                .setMessage("نسخه 0.2.0\nسازگار با Android 9 / API 28 و بالاتر.\nاتصال GET به مسیر Health و اعتبارسنجی JSON status=ok.\nاین نسخه از کتابخانه Native وابسته به معماری پردازنده استفاده نمی‌کند.")
                .setPositiveButton("بستن", null).show();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) { showSettings(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        main.removeCallbacks(clockTask);
        main.removeCallbacks(healthTask);
        network.shutdownNow();
        super.onDestroy();
    }
}
