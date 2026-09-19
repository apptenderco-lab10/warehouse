# Keep only JavaScript bridge methods that are invoked by WebView.
-keepclassmembers class com.alppco.intermediatestock.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep JavascriptInterface annotations.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
