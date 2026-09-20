plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.coolmoonfrench.dict"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.coolmoonfrench.dict"
        minSdk = 24
        targetSdk = 34
        versionCode = 18
        versionName = "1.0.18"

        // ffmpeg-kit 仅提供 arm64/x86_64，32 位设备本就不支持视频转文字；
        // 去掉 armeabi-v7a 减 37MB。
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // bouncycastle(pdfbox 传递依赖)pqc 的 lowmc/sike 属性是签名算法测试向量，
            // 运行时不会被读取，排除可省 ~4MB。
            excludes += listOf("org/bouncycastle/pqc/**")
        }
        jniLibs {
            // 压缩存储 native 库，APK 减 60-70MB（安装时解压，设备磁盘占用不变）。
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.33.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-coil3:0.33.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation(files("libs/sherpa-onnx-1.13.7.aar"))
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    kapt("androidx.room:room-compiler:2.8.5")
    // Kotlin 2.4.0 生成 metadata 2.4.0，Room compiler 默认依赖的 kotlin-metadata-jvm(2.2.0) 最多只能读 2.3.0，
    // 必须显式提升 kapt 处理器 classpath 上的版本，否则 kapt 生成 DAO 实现时会崩。
    kapt("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.0")
    implementation("com.alphacephei:vosk-android:0.3.47")
    // ffmpeg-kit-maintained 6.0.3 AAR 未声明传递依赖，但 FFmpegKitConfig 引用了 smart-exception-java 的 Exceptions 类，
    // 必须显式声明，否则运行时 NoClassDefFoundError。
    implementation("com.arthenica:smart-exception-java:0.2.1")
    // VideoToText 只用 -vn -ar 16000 -ac 1 -c:a pcm_s16le，min-gpl 含 AAC 解码 / mov,mp4 解封装 / pcm_s16le 编码，
    // 换 min-gpl 省 arm64 约 17MB。
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-min-gpl:6.0.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("io.mockk:mockk:1.13.10")
}