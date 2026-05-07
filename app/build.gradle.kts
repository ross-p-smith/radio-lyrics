import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.radiolyric"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.radiolyric"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Default keeps both ABIs. Pass `-Pradio.arm64Only=true` to halve APK
            // size for verified 64-bit-only targets such as the Mekede DUDU7
            // (see docs/target-device-facts.md §2).
            val arm64Only = (project.findProperty("radio.arm64Only") as? String) == "true"
            abiFilters +=
                if (arm64Only) listOf("arm64-v8a") else listOf("arm64-v8a", "armeabi-v7a")
        }

        // Folklore OEM ACC_ON actions for the BootReceiver intent-filter. These
        // were not observed on the live Mekede DUDU7 capture (2026-05-05) but
        // are kept registered by default for other Mekede / FYT / Microntek /
        // Cayboy / Carboy / Wits / MTC / XY firmwares. Pass
        // `-Pradio.includeFolkloreAccActions=false` to swap them for inert
        // namespaced placeholders without losing the manifest slot.
        val includeFolklore =
                (project.findProperty("radio.includeFolkloreAccActions") as? String) != "false"
        val folkloreActions =
                listOf(
                        "com.fyt.boot.ACCON",
                        "com.glsx.boot.ACCON",
                        "com.cayboy.action.ACC_ON",
                        "com.carboy.action.ACC_ON",
                        "com.microntek.startApp",
                        "android.intent.action.ACC_ON",
                        "com.android.action.ACC_ON",
                        "com.xy.power.ACC_ON",
                        "com.mtcd.action.ACC_ON",
                        "com.mtce.action.ACC_ON",
                        "com.wits.action.ACC_ON",
                )
        folkloreActions.forEachIndexed { index, action ->
            val key = "accAction${index + 1}"
            manifestPlaceholders[key] =
                    if (includeFolklore) action else "com.example.radiolyric.disabled.acc${index + 1}"
        }

        // Debug-only chooser between OmriUsbRadioSource and FakeRadioSource. Default `real` keeps
        // on-device hardware iteration friction-free; pass `-Pradio.source=fake` for hardware-free
        // builds. See app/src/debug/.../di/DebugRadioBindings.kt for how this is consumed.
        val radioSource = (project.findProperty("radio.source") as? String ?: "real").lowercase()
        require(radioSource in setOf("real", "fake")) {
            "radio.source must be 'real' or 'fake' (got: '$radioSource')"
        }
        buildConfigField("String", "RADIO_SOURCE", "\"$radioSource\"")
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
        getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
        getByName("debug").java.srcDirs("src/debug/kotlin")
        getByName("release").java.srcDirs("src/release/kotlin")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/licenses/**",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Phase 2/4: vendored LGPL-2.1 DAB driver (USB DAB only).
    implementation(project(":omri-usb"))

    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    // Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Imaging
    implementation(libs.coil.compose)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
