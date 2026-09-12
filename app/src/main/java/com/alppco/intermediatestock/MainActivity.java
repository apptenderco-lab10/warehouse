package com.alppco.intermediatestock;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_SAVE_FILE = 4101;
    private static final int REQUEST_OPEN_FILE = 4102;

    private WebView webView;
    private String pendingSaveRequestId;
    private String pendingSaveContent;
    private ValueCallback<Uri[]> pendingFileCallback;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(243, 248, 248));
        webView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);

        webView.addJavascriptInterface(new AndroidFilesBridge(), "AndroidFiles");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                }
                pendingFileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/json", "application/octet-stream", "text/plain"
                });
                startActivityForResult(intent, REQUEST_OPEN_FILE);
                return true;
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/www/index.html");
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SAVE_FILE) {
            boolean saved = false;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSaveContent != null) {
                try (OutputStream stream = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (stream == null) throw new IllegalStateException("مسیر فایل قابل نوشتن نیست.");
                    stream.write(pendingSaveContent.getBytes(StandardCharsets.UTF_8));
                    stream.flush();
                    saved = true;
                    Toast.makeText(this, "فایل با موفقیت ذخیره شد.", Toast.LENGTH_SHORT).show();
                } catch (Exception error) {
                    Toast.makeText(this, "ذخیره فایل ناموفق بود: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            notifySaveResult(saved);
            pendingSaveContent = null;
            pendingSaveRequestId = null;
            return;
        }

        if (requestCode == REQUEST_OPEN_FILE && pendingFileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            pendingFileCallback.onReceiveValue(result);
            pendingFileCallback = null;
        }
    }

    private void notifySaveResult(boolean saved) {
        if (pendingSaveRequestId == null) return;
        String safeId = pendingSaveRequestId.replace("\\", "\\\\").replace("'", "\\'");
        webView.evaluateJavascript(
                "window.__nativeSaveResult && window.__nativeSaveResult('" + safeId + "'," + saved + ");",
                null
        );
    }

    public class AndroidFilesBridge {
        @JavascriptInterface
        public void saveTextFile(String requestId, String fileName, String content) {
            runOnUiThread(() -> {
                if (pendingSaveRequestId != null) {
                    notifySaveResult(false);
                }
                pendingSaveRequestId = requestId;
                pendingSaveContent = content;

                String safeName = fileName == null ? "APP-Stock.json" : fileName.replaceAll("[\\\\/:*?\"<>|]", "-");
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_TITLE, safeName);
                startActivityForResult(intent, REQUEST_SAVE_FILE);
            });
        }

        @JavascriptInterface
        public String getDeviceId() {
            return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        }
    }
}
