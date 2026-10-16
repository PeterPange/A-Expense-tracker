import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 读取密钥配置（secrets.properties 不进 git）
val secretsProps = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.jizhang.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jizhang.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.8.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "AI_API_KEY", "\"${secretsProps.getProperty("AI_API_KEY", "")}\"")
        buildConfigField("String", "CLOUD_SERVER", "\"${secretsProps.getProperty("CLOUD_SERVER", "")}\"")
        val updateNotes = "v0.7.0 更新内容:\\n\\n· 新增主题选择（4种配色+明暗模式）\\n· 新增用户档案（头像+昵称）\\n· 新增更新公告弹窗\\n· 新增启动时自动检查更新\\n· 新增分类预算功能\\n· 新增首页快捷统计\\n· 新增拍照记账（OCR识别+AI自动填入）"
        buildConfigField("String", "UPDATE_NOTES", "\"${updateNotes}\"")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room 本地数据库（2.7+ 支持 KSP2）
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // 网络（AI 接口）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 图片加载（头像）
    implementation("io.coil-kt:coil-compose:2.7.0")
}
