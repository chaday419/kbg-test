# Garder les classes principales
-keep class cd.upn.suivipresence.** { *; }

# Garder les interfaces JavaScript
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Garder les annotations
-keepattributes *Annotation*
-keepattributes JavascriptInterface

# Supprimer les logs en production
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
