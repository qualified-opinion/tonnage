plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.janreins.tonnage"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.janreins.tonnage"
    minSdk = 26
    targetSdk = 35
    // CI release builds pass these so each published APK can be installed over the previous one
    versionCode = providers.environmentVariable("TONNAGE_VERSION_CODE").orNull?.toIntOrNull() ?: 1
    versionName = providers.environmentVariable("TONNAGE_VERSION_NAME").orNull ?: "0.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Release signing comes from environment variables (set by the GitHub release workflow, or
  // locally). Without them the release APK is signed with the debug key so it still installs.
  val releaseKeystore = providers.environmentVariable("TONNAGE_KEYSTORE_FILE").orNull
  val hasReleaseKeystore = releaseKeystore != null && file(releaseKeystore).exists()

  signingConfigs {
    if (hasReleaseKeystore) {
      create("release") {
        storeFile = file(releaseKeystore!!)
        storePassword = providers.environmentVariable("TONNAGE_KEYSTORE_PASSWORD").orNull
        keyAlias = providers.environmentVariable("TONNAGE_KEY_ALIAS").orNull
        keyPassword = providers.environmentVariable("TONNAGE_KEY_PASSWORD").orNull
      }
    }
  }

  buildTypes {
    release {
      signingConfig = if (hasReleaseKeystore) {
        signingConfigs.getByName("release")
      } else {
        signingConfigs.getByName("debug")
      }
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  sourceSets {
    getByName("test").assets.directories.add("$projectDir/schemas")
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}
