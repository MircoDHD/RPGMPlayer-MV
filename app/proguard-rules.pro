# Methods called from JavaScript through addJavascriptInterface must survive shrinking.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
