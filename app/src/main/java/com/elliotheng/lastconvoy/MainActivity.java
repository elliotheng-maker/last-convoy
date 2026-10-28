package com.elliotheng.lastconvoy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Last Convoy for Android.
 * The game ships inside the APK so it plays offline. On launch the app checks
 * updates/version.json on GitHub: a newer game build is offered as a quick
 * in-app update (no reinstall, saves kept); a newer APK is offered as a download.
 */
public class MainActivity extends Activity {
    private static final String ORIGIN = "https://lastconvoy.app/"; // fixed origin so saves survive game updates
    private WebView web;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("lc", MODE_PRIVATE);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith(ORIGIN)) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); return true;
            }
        });
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(web);
        loadGame();
        checkForUpdates(false);
    }

    private int bundledVersion() { try { return Integer.parseInt(new String(readAll(getAssets().open("version.txt"))).trim()); } catch (Exception e) { return 0; } }
    private int installedVersion() { return Math.max(bundledVersion(), prefs.getInt("gameVersion", 0)); }
    private File updatedFile() { return new File(getFilesDir(), "game.html"); }

    private void loadGame() {
        try {
            byte[] html;
            File f = updatedFile();
            if (f.exists() && prefs.getInt("gameVersion", 0) > bundledVersion()) html = readAll(new FileInputStream(f));
            else html = readAll(getAssets().open("game.html"));
            web.loadDataWithBaseURL(ORIGIN, new String(html, "UTF-8"), "text/html", "UTF-8", null);
        } catch (Exception e) { web.loadData("<h2>Could not load the game</h2>", "text/html", "UTF-8"); }
    }

    private void checkForUpdates(final boolean manual) {
        new Thread(() -> {
            try {
                JSONObject j = new JSONObject(new String(fetch(BuildConfig.UPDATE_BASE + "version.json?t=" + System.currentTimeMillis()), "UTF-8"));
                final int gameV = j.optInt("version", 0), apkV = j.optInt("apkVersion", 0);
                final String notes = j.optString("notes", "");
                if (apkV > BuildConfig.VERSION_CODE) runOnUiThread(() -> askApk(notes));
                else if (gameV > installedVersion() && gameV != prefs.getInt("skipped", 0)) runOnUiThread(() -> askGame(gameV, notes));
                else if (manual) runOnUiThread(() -> Toast.makeText(this, "You're on the latest version.", Toast.LENGTH_SHORT).show());
            } catch (Exception e) { if (manual) runOnUiThread(() -> Toast.makeText(this, "Couldn't check for updates.", Toast.LENGTH_SHORT).show()); }
        }).start();
    }

    private void askGame(final int v, String notes) {
        new AlertDialog.Builder(this).setTitle("Update available")
            .setMessage((notes.isEmpty() ? "A new version of Last Convoy is ready." : notes) + "\n\nYour current run is kept.")
            .setPositiveButton("Update now", (d, w) -> downloadGame(v))
            .setNegativeButton("Later", null)
            .setNeutralButton("Skip this one", (d, w) -> prefs.edit().putInt("skipped", v).apply())
            .show();
    }

    private void askApk(String notes) {
        new AlertDialog.Builder(this).setTitle("New app version")
            .setMessage((notes.isEmpty() ? "" : notes + "\n\n") + "This update needs a fresh install. Download it, open it, and tap Install. Your saves are kept.")
            .setPositiveButton("Download", (d, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.APK_URL))))
            .setNegativeButton("Later", null).show();
    }

    private void downloadGame(final int v) {
        Toast.makeText(this, "Downloading update…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                byte[] html = fetch(BuildConfig.UPDATE_BASE + "game.html?v=" + v);
                if (html.length < 10000 || !new String(html, 0, 400, "UTF-8").contains("<")) throw new Exception("bad file");
                File tmp = new File(getFilesDir(), "game.tmp");
                try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(html); }
                if (!tmp.renameTo(updatedFile())) throw new Exception("could not save");
                prefs.edit().putInt("gameVersion", v).apply();
                runOnUiThread(() -> { Toast.makeText(this, "Updated.", Toast.LENGTH_SHORT).show(); loadGame(); });
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Update failed. Try again later.", Toast.LENGTH_LONG).show()); }
        }).start();
    }

    private static byte[] fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(20000); c.setUseCaches(false);
        try (InputStream in = c.getInputStream()) { return readAll(in); } finally { c.disconnect(); }
    }
    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] buf = new byte[16384]; int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n); in.close(); return o.toByteArray();
    }

    @Override public void onBackPressed() {
        new AlertDialog.Builder(this).setTitle("Leave Last Convoy?").setMessage("Your run is saved.")
            .setPositiveButton("Leave", (d, w) -> finish())
            .setNeutralButton("Check for updates", (d, w) -> checkForUpdates(true))
            .setNegativeButton("Stay", null).show();
    }
    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
}
