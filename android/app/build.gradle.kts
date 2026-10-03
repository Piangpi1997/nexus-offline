import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val productionApplicationId = providers.gradleProperty("productionApplicationId").orElse("com.nexusoffline").get()
val releaseVersionCode = providers.gradleProperty("releaseVersionCode").orElse("1").get().toInt()
val releaseVersionName = providers.gradleProperty("releaseVersionName").orElse("0.1.0-alpha").get()
val releaseStoreFile = providers.environmentVariable("NEXUS_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("NEXUS_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("NEXUS_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("NEXUS_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.nexusoffline"
    compileSdk = 35

    defaultConfig {
        applicationId = productionApplicationId
        minSdk = 24
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("production") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            signingConfig = if (releaseSigningConfigured) signingConfigs.getByName("production") else null
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("17")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.webkit:webkit:1.17.1")
    implementation("com.google.android.gms:play-services-nearby:19.5.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
