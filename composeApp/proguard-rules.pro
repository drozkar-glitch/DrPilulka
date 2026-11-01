-keep class cz.mares.drpilulka.MainActivity { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn kotlinx.**
-dontwarn org.jetbrains.**
