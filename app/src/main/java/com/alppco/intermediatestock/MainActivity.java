package com.alppco.intermediatestock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_SAVE_FILE = 6101;
    private static final int REQUEST_NOTIFICATIONS = 6103;
    private static final String CHANNEL_ID = "app_gold_secure_announcements";
    private static final String APP_HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + APP_HOST + "/assets/www/index.html";
    private static final String EXPECTED_CERT_SHA256 = "8C3D4275E0FB09817F50682142B5D62EC4267078A59048087D3898D49D8D85C2";

    private WebView webView;
    private byte[] pendingSaveBytes;
    private String pendingSaveMime;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!BuildConfig.DEBUG && !isSignatureValid()) {
            new AlertDialog.Builder(this)
                    .setTitle("APP Gold Secure")
                    .setMessage("امضای امنیتی برنامه معتبر نیست. این نسخه ممکن است دستکاری یا دوباره امضا شده باشد.")
                    .setCancelable(false)
                    .setPositiveButton("خروج", (d, w) -> finish())
                    .show();
            return;
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .setDomain(APP_HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

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
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(true);
        settings.setGeolocationEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(false);
        cookies.setAcceptThirdPartyCookies(webView, false);
        cookies.removeAllCookies(null);

        webView.clearCache(true);
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme()) && APP_HOST.equalsIgnoreCase(uri.getHost())) {
                    return false;
                }
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {}
                }
                return true;
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                try {
                    view.destroy();
                } catch (Exception ignored) {}
                recreate();
                return true;
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL);
        } else {
            webView.restoreState(savedInstanceState);
            String current = webView.getUrl();
            if (current == null || !current.startsWith("https://" + APP_HOST + "/")) {
                webView.loadUrl(START_URL);
            }
        }
    }

    private boolean isSignatureValid() {
        try {
            PackageInfo info;
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) return false;
                signatures = info.signingInfo.hasMultipleSigners()
                        ? info.signingInfo.getApkContentsSigners()
                        : info.signingInfo.getSigningCertificateHistory();
            } else {
                info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            if (signatures == null || signatures.length == 0) return false;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Signature signature : signatures) {
                byte[] hash = digest.digest(signature.toByteArray());
                if (toHex(hash).equals(EXPECTED_CERT_SHA256)) return true;
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02X", b));
        return sb.toString();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "اطلاعیه‌های APP Gold Secure",
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
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            try {
                webView.removeJavascriptInterface("Android");
                webView.clearHistory();
                webView.destroy();
            } catch (Exception ignored) {}
            webView = null;
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
                            .setContentTitle(title == null ? "APP Gold Secure" : title)
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
