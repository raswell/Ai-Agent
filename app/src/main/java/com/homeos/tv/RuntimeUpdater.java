package com.homeos.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class RuntimeUpdater {
    interface Callback { void onFinished(boolean changed, String message); }
    private static final String PREFS = "homeos_tv";
    private static final String DEFAULT_SERVER = "http://192.168.254.54:8081";
    private static final int SHELL_VERSION_CODE = 1;
    private static final int MAX_BUNDLE_BYTES = 20 * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 40L * 1024L * 1024L;
    private final Context context;
    private final File activeDir;
    private volatile boolean running;

    RuntimeUpdater(Context context, File activeDir) { this.context = context.getApplicationContext(); this.activeDir = activeDir; }

    void checkForUpdate(Callback callback) {
        synchronized (this) { if (running) { if (callback != null) callback.onFinished(false, "Update already running"); return; } running = true; }
        new Thread(() -> {
            boolean changed = false; String message;
            try { changed = downloadAndActivate(); message = changed ? "Runtime updated" : "No update"; }
            catch (Exception e) { message = "Update unavailable: " + e.getMessage(); }
            synchronized (RuntimeUpdater.this) { running = false; }
            if (callback != null) callback.onFinished(changed, message);
        }, "homeos-runtime-update").start();
    }

    private boolean downloadAndActivate() throws Exception {
        String base = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("server", DEFAULT_SERVER).replaceAll("/+$", "");
        JSONObject manifest = new JSONObject(new String(readUrl(base + "/launcher/v1/manifest.json", 1024 * 1024), "UTF-8"));
        if (manifest.optInt("schemaVersion", 0) != 1) throw new Exception("Unsupported manifest schema");
        String version = manifest.getString("version");
        if (manifest.optInt("minShellVersion", 1) > SHELL_VERSION_CODE) throw new Exception("Runtime requires a newer shell APK");
        int incomingCode = manifest.optInt("versionCode", 0);
        int currentCode = readRuntimeVersionCode(activeDir);
        if (incomingCode <= currentCode) return false;
        String bundlePath = manifest.getString("bundleUrl");
        if (!bundlePath.startsWith("/launcher/v1/") || bundlePath.contains("..")) throw new Exception("Invalid bundle URL");
        String expected = manifest.getString("sha256").toLowerCase(Locale.ROOT);
        if (!expected.matches("[0-9a-f]{64}")) throw new Exception("Invalid SHA-256");
        byte[] zip = readUrl(base + bundlePath, MAX_BUNDLE_BYTES);
        if (!sha256(zip).equals(expected)) throw new Exception("Bundle SHA-256 mismatch");

        File parent = activeDir.getParentFile();
        File staging = new File(parent, "staging"); deleteTree(staging); if (!staging.mkdirs()) throw new Exception("Cannot create staging directory");
        unzipSafely(zip, staging);
        File entry = new File(staging, "index.html");
        if (!entry.isFile()) { deleteTree(staging); throw new Exception("Bundle has no index.html"); }
        JSONObject runtimeManifest = new JSONObject(); runtimeManifest.put("version", version); runtimeManifest.put("versionCode", incomingCode); runtimeManifest.put("installedAt", System.currentTimeMillis());
        try (FileOutputStream out = new FileOutputStream(new File(staging, "runtime-version.json"))) { out.write(runtimeManifest.toString().getBytes("UTF-8")); out.getFD().sync(); }

        File backup = new File(parent, "previous"); deleteTree(backup);
        if (activeDir.exists() && !activeDir.renameTo(backup)) { deleteTree(staging); throw new Exception("Cannot preserve previous runtime"); }
        if (!staging.renameTo(activeDir)) {
            if (backup.exists()) backup.renameTo(activeDir);
            throw new Exception("Cannot activate staged runtime");
        }
        return true;
    }

    private static byte[] readUrl(String address, int maxBytes) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(10000); c.setRequestMethod("GET"); c.setInstanceFollowRedirects(false);
        try {
            int status = c.getResponseCode(); if (status != 200) throw new Exception("HTTP " + status);
            int length = c.getContentLength(); if (length > maxBytes) throw new Exception("Response too large");
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n; int total = 0;
                while ((n = in.read(b)) != -1) { total += n; if (total > maxBytes) throw new Exception("Response too large"); out.write(b, 0, n); }
                return out.toByteArray();
            }
        } finally { c.disconnect(); }
    }

    private static void unzipSafely(byte[] zip, File destination) throws Exception {
        long total = 0; String root = destination.getCanonicalPath() + File.separator;
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                File out = new File(destination, entry.getName()).getCanonicalFile();
                if (!out.getPath().startsWith(root)) throw new Exception("Unsafe ZIP path");
                if (entry.isDirectory()) { if (!out.exists() && !out.mkdirs()) throw new Exception("Cannot create directory"); }
                else {
                    File parent = out.getParentFile(); if (!parent.exists() && !parent.mkdirs()) throw new Exception("Cannot create parent");
                    try (FileOutputStream stream = new FileOutputStream(out)) {
                        byte[] b = new byte[8192]; int n;
                        while ((n = in.read(b)) != -1) { total += n; if (total > MAX_EXTRACTED_BYTES) throw new Exception("Expanded bundle too large"); stream.write(b, 0, n); }
                    }
                }
                in.closeEntry();
            }
        }
    }

    private static int readRuntimeVersionCode(File dir) {
        try (FileInputStream in = new FileInputStream(new File(dir, "runtime-version.json")); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[1024]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return new JSONObject(new String(out.toByteArray(), "UTF-8")).optInt("versionCode", 0);
        } catch (Exception e) { return 0; }
    }
    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data); StringBuilder s = new StringBuilder();
        for (byte b : digest) s.append(String.format(Locale.ROOT, "%02x", b & 0xff)); return s.toString();
    }
    static String readRuntimeVersion(File dir) {
        try (FileInputStream in = new FileInputStream(new File(dir, "runtime-version.json")); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[1024]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return new JSONObject(new String(out.toByteArray(), "UTF-8")).optString("version", "bundled");
        } catch (Exception e) { return "bundled"; }
    }
    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
