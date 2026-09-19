# Keep only the methods intentionally exposed from trusted bundled JavaScript.
-keepclassmembers class com.alppco.intermediatestock.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve annotation metadata required by WebView JavaScript bridge.
-keepattributes *Annotation*
