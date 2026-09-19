package com.alppco.intermediatestock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int REQUEST_SAVE_FILE = 6101;
    private static final int REQUEST_OPEN_FILE = 6102;
    private static final int REQUEST_NOTIFICATIONS = 6103;
    private static final String CHANNEL_ID = "app_gold_announcements";
    private static final String APP_ORIGIN = "https://appassets.androidplatform.net";
    private static final String APP_URL = APP_ORIGIN + "/assets/www/index.html";

    private WebView webView;
    private byte[] pendingSaveBytes;
    private String pendingSaveMime;
    private ValueCallback<Uri[]> pendingFileCallback;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView.setWebContentsDebuggingEnabled(false);
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(255, 253, 245));
        webView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (APP_ORIGIN.equals(uri.getScheme() + "://" + uri.getHost())) return false;
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {}
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFileCallback != null) pendingFileCallback.onReceiveValue(null);
                pendingFileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_OPEN_FILE);
                return true;
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(APP_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "اطلاعیه‌های APP Gold",
                    NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("اطلاعیه‌ها و پیام‌های داخلی شرکت");
            nm.createNotificationChannel(ch);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SAVE_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSaveBytes != null) {
                try (OutputStream stream = getContentResolver().openOutputStream(data.getData(), "w")) {
                    if (stream == null) throw new IllegalStateException("مسیر فایل قابل نوشتن نیست.");
                    stream.write(pendingSaveBytes);
                    stream.flush();
                    Toast.makeText(this, "فایل ذخیره شد.", Toast.LENGTH_SHORT).show();
                } catch (Exception error) {
                    Toast.makeText(this, "ذخیره فایل ناموفق بود.", Toast.LENGTH_LONG).show();
                }
            }
            pendingSaveBytes = null;
            pendingSaveMime = null;
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

    public class AndroidBridge {
        @JavascriptInterface
        public void saveBase64(String fileName, String mimeType, String base64Data) {
            runOnUiThread(() -> {
                try {
                    pendingSaveBytes = Base64.decode(base64Data, Base64.DEFAULT);
                    pendingSaveMime = (mimeType == null || mimeType.trim().isEmpty())
                            ? "application/octet-stream" : mimeType;
                    String safeName = (fileName == null || fileName.trim().isEmpty())
                            ? "APP-Gold-Export.bin"
                            : fileName.replaceAll("[\\\\/:*?\"<>|]", "-");

                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(pendingSaveMime);
                    intent.putExtra(Intent.EXTRA_TITLE, safeName);
                    startActivityForResult(intent, REQUEST_SAVE_FILE);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "ساخت فایل ناموفق بود.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void setSession(String accessToken, String refreshToken) {
            // Intentionally no-op: tokens remain inside WebView's sandboxed storage.
            // Do not duplicate access/refresh tokens into plain SharedPreferences.
        }

        @JavascriptInterface
        public void clearSession() {
            // Remove any legacy token copies created by older builds.
            getSharedPreferences("app_hr_session", MODE_PRIVATE).edit().clear().apply();
        }

        @JavascriptInterface
        public void notifyAnnouncement(String id, String title, String body, String priority) {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }

                    Intent launch = new Intent(MainActivity.this, MainActivity.class);
                    launch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
                    PendingIntent pi = PendingIntent.getActivity(
                            MainActivity.this,
                            (id == null ? 0 : id.hashCode()),
                            launch,
                            flags
                    );

                    android.app.Notification.Builder builder;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        builder = new android.app.Notification.Builder(MainActivity.this, CHANNEL_ID);
                    } else {
                        builder = new android.app.Notification.Builder(MainActivity.this);
                    }

                    builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle(title == null ? "APP Gold" : title)
                            .setContentText(body == null ? "" : body)
                            .setStyle(new android.app.Notification.BigTextStyle().bigText(body == null ? "" : body))
                            .setAutoCancel(true)
                            .setContentIntent(pi);

                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    nm.notify(id == null ? (int) System.currentTimeMillis() : id.hashCode(), builder.build());
                } catch (Exception ignored) {}
            });
        }
    }
}
