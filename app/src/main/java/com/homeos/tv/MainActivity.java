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
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "homeos_tv";
    private static final String KEY_SERVER = "server_url";
    private static final int BG = Color.rgb(10, 15, 24);
    private static final int PANEL = Color.rgb(22, 31, 45);
    private static final int PANEL_2 = Color.rgb(29, 42, 59);
    private static final int TEXT = Color.rgb(238, 243, 250);
    private static final int MUTED = Color.rgb(151, 166, 187);
    private static final int ACCENT = Color.rgb(88, 190, 166);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView clockView, dateView, connectionView, serverView, hintView;
    private String serverUrl = "";
    private boolean checking = false;
    private boolean connected = false;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (clockView != null) {
                Date now = new Date();
                clockView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
                dateView.setText(new SimpleDateFormat("EEEE، d MMMM yyyy", new Locale("fa")).format(now));
            }
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable healthTick = new Runnable() {
        @Override public void run() {
            checkServer();
            handler.postDelayed(this, 15000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        serverUrl = getPreferences(MODE_PRIVATE).getString(KEY_SERVER, "");
        buildUi();
        handler.post(clockTick);
        handler.postDelayed(healthTick, 1200);
    }

    private GradientDrawable shape(int color, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        if (strokeColor != 0) d.setStroke((int)(1 * getResources().getDisplayMetrics().density), strokeColor);
        return d;
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
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

    private TextView card(String title, String body) {
        LinearLayout box = column();
        box.setPadding(dp(20), dp(18), dp(20), dp(18));
        box.setBackground(shape(PANEL, 18, Color.rgb(43, 58, 77)));
        TextView heading = text(title, 17, MUTED, true);
        TextView content = text(body, 23, TEXT, true);
        box.addView(heading);
        LinearLayout.LayoutParams cp = lp(-1, -2);
        cp.topMargin = dp(12);
        box.addView(content, cp);
        box.setFocusable(true);
        box.setFocusableInTouchMode(true);
        box.setOnFocusChangeListener((v, has) -> v.setBackground(shape(has ? PANEL_2 : PANEL, 18,
                has ? ACCENT : Color.rgb(43, 58, 77))));
        return wrapCard(box);
    }

    private TextView wrapCard(LinearLayout view) {
        // A lightweight marker view is not used; cards are built directly in addCard().
        TextView t = new TextView(this);
        t.setVisibility(View.GONE);
        return t;
    }

    private void addCard(LinearLayout parent, String title, String body, int weight) {
        LinearLayout box = column();
        box.setPadding(dp(20), dp(18), dp(20), dp(18));
        box.setBackground(shape(PANEL, 18, Color.rgb(43, 58, 77)));
        box.setFocusable(true);
        box.setFocusableInTouchMode(true);
        box.setOnFocusChangeListener((v, has) -> v.setBackground(shape(has ? PANEL_2 : PANEL, 18,
                has ? ACCENT : Color.rgb(43, 58, 77))));
        box.addView(text(title, 17, MUTED, true));
        TextView value = text(body, 22, TEXT, true);
        value.setTag(title);
        LinearLayout.LayoutParams vp = lp(-1, -2);
        vp.topMargin = dp(12);
        box.addView(value, vp);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(118), weight);
        bp.setMargins(dp(7), dp(7), dp(7), dp(7));
        parent.addView(box, bp);
    }

    private Button actionButton(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(TEXT);
        b.setBackground(shape(PANEL_2, 14, Color.rgb(58, 78, 100)));
        b.setFocusable(true);
        b.setOnClickListener(v -> action.run());
        b.setOnFocusChangeListener((v, has) -> v.setBackground(shape(has ? Color.rgb(42, 83, 83) : PANEL_2, 14, has ? ACCENT : Color.rgb(58, 78, 100))));
        return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout page = column();
        page.setPadding(dp(42), dp(26), dp(42), dp(30));
        scroll.addView(page);
        setContentView(scroll);

        LinearLayout top = row();
        TextView brand = text("HOMEOS", 19, ACCENT, true);
        TextView edition = text("  TV  /  CONTROL SURFACE", 13, MUTED, true);
        top.addView(brand);
        top.addView(edition);
        page.addView(top);

        LinearLayout hero = row();
        hero.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout time = column();
        clockView = text("--:--", 58, TEXT, true);
        dateView = text("در حال آماده‌سازی...", 17, MUTED, false);
        time.addView(clockView);
        LinearLayout.LayoutParams dateLp = lp(-1, -2);
        dateLp.topMargin = dp(2);
        time.addView(dateView, dateLp);
        hero.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout state = column();
        state.setGravity(Gravity.END);
        connectionView = text("●  در انتظار اتصال", 17, ACCENT, true);
        serverView = text("سرور تنظیم نشده", 13, MUTED, false);
        state.addView(connectionView);
        LinearLayout.LayoutParams svp = lp(-2, -2);
        svp.topMargin = dp(8);
        state.addView(serverView, svp);
        hero.addView(state);
        LinearLayout.LayoutParams hp = lp(-1, -2);
        hp.topMargin = dp(34);
        page.addView(hero, hp);

        TextView section = text("نمای کلی سیستم", 20, TEXT, true);
        LinearLayout.LayoutParams sp = lp(-1, -2);
        sp.topMargin = dp(30);
        page.addView(section, sp);

        LinearLayout cards1 = row();
        cards1.setPadding(0, dp(8), 0, 0);
        addCard(cards1, "HERMES RASPBERRY", "آماده اتصال", 1);
        addCard(cards1, "شبکه", "محلی / LAN", 1);
        addCard(cards1, "HOMEOS CORE", "در انتظار راه‌اندازی", 1);
        page.addView(cards1, lp(-1, -2));

        TextView modulesTitle = text("فضاهای HomeOS", 20, TEXT, true);
        LinearLayout.LayoutParams mt = lp(-1, -2);
        mt.topMargin = dp(22);
        page.addView(modulesTitle, mt);

        LinearLayout cards2 = row();
        cards2.setPadding(0, dp(8), 0, 0);
        addCard(cards2, "خانه هوشمند", "دستگاه‌ها و سنسورها", 1);
        addCard(cards2, "دستگاه‌ها", "مدیریت تجهیزات", 1);
        addCard(cards2, "Hermes", "فرمان و گزارش", 1);
        page.addView(cards2, lp(-1, -2));

        TextView toolsTitle = text("دسترسی سریع", 20, TEXT, true);
        LinearLayout.LayoutParams tt = lp(-1, -2);
        tt.topMargin = dp(22);
        page.addView(toolsTitle, tt);

        LinearLayout buttons = row();
        buttons.addView(actionButton("تنظیم اتصال", this::showSettings), lp(170, 54));
        LinearLayout.LayoutParams b2 = lp(170, 54);
        b2.leftMargin = dp(12);
        buttons.addView(actionButton("آزمایش اتصال", this::checkServer), b2);
        LinearLayout.LayoutParams b3 = lp(170, 54);
        b3.leftMargin = dp(12);
        buttons.addView(actionButton("درباره نسخه", this::showAbout), b3);
        LinearLayout.LayoutParams bp = lp(-1, -2);
        bp.topMargin = dp(10);
        page.addView(buttons, bp);

        hintView = text("نسخه 0.1.0  •  مستقل از سرور  •  آماده اتصال مرحله بعد", 13, MUTED, false);
        LinearLayout.LayoutParams hintLp = lp(-1, -2);
        hintLp.topMargin = dp(26);
        page.addView(hintView, hintLp);

        // Set an initial focus target for TV remotes.
        buttons.getChildAt(0).requestFocus();
    }

    private void showSettings() {
        LinearLayout form = column();
        form.setPadding(dp(8), dp(8), dp(8), dp(4));
        TextView info = text("آدرس پایه سرور Hermes را وارد کن؛ نمونه: http://192.168.1.20:8080", 14, MUTED, false);
        form.addView(info);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setHint("http://IP:PORT");
        input.setText(serverUrl);
        input.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams ip = lp(-1, 54);
        ip.topMargin = dp(12);
        form.addView(input, ip);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("تنظیم اتصال HomeOS")
                .setView(form)
                .setNegativeButton("لغو", (d, w) -> {})
                .setNeutralButton("پاک کردن", (d, w) -> {
                    serverUrl = "";
                    saveServer();
                    updateConnectionUi(false, "سرور تنظیم نشده");
                })
                .setPositiveButton("ذخیره", (d, w) -> {
                    String value = input.getText().toString().trim();
                    while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
                    if (!value.isEmpty() && !(value.startsWith("http://") || value.startsWith("https://"))) {
                        value = "http://" + value;
                    }
                    serverUrl = value;
                    saveServer();
                    updateConnectionUi(false, serverUrl.isEmpty() ? "سرور تنظیم نشده" : serverUrl);
                    if (!serverUrl.isEmpty()) checkServer();
                }).create();
        dialog.getWindow();
        dialog.show();
    }

    private void saveServer() {
        getPreferences(MODE_PRIVATE).edit().putString(KEY_SERVER, serverUrl).apply();
    }

    private void checkServer() {
        if (checking) return;
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            updateConnectionUi(false, "برای شروع، آدرس سرور را تنظیم کن");
            return;
        }
        checking = true;
        final String base = serverUrl;
        executor.execute(() -> {
            HttpURLConnection connection = null;
            boolean ok = false;
            String detail = "پاسخی از /health دریافت نشد";
            try {
                URL url = new URL(base + "/health");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3500);
                connection.setReadTimeout(3500);
                connection.setRequestProperty("Accept", "application/json");
                int code = connection.getResponseCode();
                ok = code >= 200 && code < 300;
                detail = "HTTP " + code + " از /health";
            } catch (Exception e) {
                detail = e.getClass().getSimpleName() + " — مسیر آزمایشی /health";
            } finally {
                if (connection != null) connection.disconnect();
            }
            final boolean result = ok;
            final String message = detail;
            handler.post(() -> {
                checking = false;
                updateConnectionUi(result, message);
            });
        });
    }

    private void updateConnectionUi(boolean ok, String detail) {
        connected = ok;
        if (connectionView != null) {
            connectionView.setText(ok ? "●  متصل" : "●  قطع / در انتظار");
            connectionView.setTextColor(ok ? ACCENT : Color.rgb(232, 174, 103));
            serverView.setText(detail);
        }
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("HomeOS TV Launcher")
                .setMessage("نسخه 0.1.0\\n\\nنسخه پایه مستقل برای Android TV.\\n\\nاتصال فعلی فقط مسیر آزمایشی GET /health را بررسی می‌کند. پروتکل نهایی Hermes پس از بررسی Gateway واقعی Raspberry Pi متصل خواهد شد.")
                .setPositiveButton("بستن", null)
                .show();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(healthTick);
        executor.shutdownNow();
        super.onDestroy();
    }
}
