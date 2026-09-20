# pdfbox-android 大量使用反射加载字体/资源，保留全部类
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }

# bouncycastle(pdfbox 传递依赖)签名相关按名字保留，避免算法反射失效
-keep class org.bouncycastle.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }

# sherpa-onnx 通过 JNI 绑定本地库，方法名不可混淆
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# vosk 语音识别 JNI
-keep class org.vosk.** { *; }
-keep class org.mockk.** { *; }

# JNA 是 Vosk 的底层依赖，其 JNI 代码按字段名 "peer" 反射访问 com.sun.jna.Pointer。
# 若被混淆会抛 "Can't obtain peer field ID for class com.sun.jna.Pointer"，导致离线模型加载失败。
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-dontwarn com.sun.jna.**

# ffmpeg-kit JNI
-keep class com.arthenica.ffmpegkit.** { *; }

# 内置 ML Kit OCR 原生库
-keep class com.google.mlkit.** { *; }

# okhttp 一般无需额外规则，但保留 JSON 反射入口以防定制解析器
-keep class com.squareup.okhttp3.** { *; }

# 保留 Room 生成的 DAO/Database 实现（Room AAR 自带 consumer 规则，这里兜底）
-keep class androidx.room.** { *; }

# markdown 渲染库经 Kotlin 反射注册扩展，保留包名
-keep class com.mikepenz.markdown.** { *; }
-keep class io.noties.markwon.** { *; }

-dontwarn com.arthenica.ffmpegkit.**
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**