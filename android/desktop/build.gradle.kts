import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        // 复用 app 模块中零 Android 依赖的核心源文件
        kotlin.srcDir("../app/src/main/java")
        kotlin.include(
            "**/Main.kt",
            "**/DesktopData.kt",
            "**/FrenchIpa.kt",
            "**/VerbConjugator.kt",
            "**/ChineseVerbSearch.kt"
        )
        // 直接复用 Android 的预构建词典资产（仅打包 dictionary.db）
        resources.srcDir("../app/src/main/assets")
        resources.include("dictionary.db")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("org.json:json:20240303")
}

compose.desktop {
    application {
        mainClass = "com.coolmoonfrench.dict.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "MoonFrenchDict"
            packageVersion = "1.0.0"
            vendor = "coolmoon"
            description = "法语词典桌面预览：查词、变位、中文候选"
        }
    }
}
