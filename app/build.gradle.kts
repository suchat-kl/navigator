import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// กุญแจเซ็นรุ่น release อ่านจาก keystore.properties (ไม่เข้า git) — ดู docs/play/ขั้นตอนเผยแพร่บน Google Play.md
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// กุญแจ OpenRouteService (หาเส้นทาง + ค้นหาสถานที่) อ่านจาก local.properties (ไม่เข้า git)
// สำรอง: gradle property หรือตัวแปรสภาพแวดล้อม ORS_API_KEY — ดู docs/play/ตั้งค่า OpenRouteService.md
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val orsApiKey: String = localProps.getProperty("orsApiKey")?.takeIf { it.isNotBlank() }
    ?: (findProperty("orsApiKey") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("ORS_API_KEY").orEmpty()

// รหัส AdMob: รุ่น debug/staging ใช้รหัสทดสอบของ Google เสมอ
// รุ่น release ใช้รหัสจริงจาก gradle.properties (admobAppId, admobBannerId, admobInterstitialId)
// หน่วยโฆษณาที่ไม่ได้ใส่รหัส = ไม่แสดงในรุ่น release (ไม่ใช้รหัสทดสอบในแอปที่เผยแพร่)
// ดู docs/play/ตั้งค่า AdMob.md
val testAdmob = mapOf(
    "app" to "ca-app-pub-3940256099942544~3347511713",
    "banner" to "ca-app-pub-3940256099942544/9214589741",
    "interstitial" to "ca-app-pub-3940256099942544/1033173712",
)
fun admob(key: String, test: String) = (findProperty(key) as String?)?.takeIf { it.isNotBlank() } ?: test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "th.banceum.suchat.navigator"
    compileSdk = 36

    defaultConfig {
        applicationId = "th.banceum.suchat.navigator"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["admobAppId"] = testAdmob.getValue("app")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"${testAdmob.getValue("banner")}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${testAdmob.getValue("interstitial")}\"")
        buildConfigField("String", "ORS_API_KEY", "\"$orsApiKey\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) create("upload") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
            manifestPlaceholders["admobAppId"] = admob("admobAppId", testAdmob.getValue("app"))
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${admob("admobBannerId", "")}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${admob("admobInterstitialId", "")}\"")
        }
        // เหมือน release ทุกอย่าง (ย่อโค้ดด้วย R8) แต่เซ็นด้วยกุญแจ debug และใช้โฆษณาทดสอบ ใช้ทดสอบก่อนเผยแพร่
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".staging"
            manifestPlaceholders["admobAppId"] = testAdmob.getValue("app")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${testAdmob.getValue("banner")}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${testAdmob.getValue("interstitial")}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // แผนที่ MapLibre + แผนที่ฐานจาก OpenFreeMap (ข้อมูล OpenStreetMap ไม่ต้องใช้กุญแจ)
    implementation("org.maplibre.gl:android-sdk:13.6.1")
    // ตำแหน่งปัจจุบัน (Fused Location)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // โฆษณา AdMob + แบบขอความยินยอม (UMP)
    implementation("com.google.android.gms:play-services-ads:24.4.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
