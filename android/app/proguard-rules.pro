-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

-keepclassmembers class com.nexusoffline.MainActivity$NativeBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# LiteRT-LM resolves native/session entry points and model/runtime types at runtime.
-keep class com.google.ai.edge.litertlm.** { *; }
