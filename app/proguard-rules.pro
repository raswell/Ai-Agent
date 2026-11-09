# Keep JavaScript bridge methods visible to WebView.
-keepclassmembers class com.homeos.tv.MainActivity$HomeOSBridge { @android.webkit.JavascriptInterface <methods>; }
