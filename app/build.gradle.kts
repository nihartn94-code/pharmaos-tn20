import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.pharmaos.vpmzql"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField(
      "String",
      "PHARMAOS_API_BASE_URL",
      "\"https://BACKEND_URL_NOT_CONFIGURED.pharmaos.invalid/\""
    )
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      val keystoreFile = file("${rootDir}/debug.keystore")
      // Conditional check: if keystore file does not exist, create a dummy/debug keystore
      if (!keystoreFile.exists()) {
        keystoreFile.parentFile?.mkdirs()
        val base64Keystore = file("${rootDir}/debug.keystore.base64")
        if (base64Keystore.exists()) {
          try {
            val cleanBase64 = base64Keystore.readText().replace("\r", "").replace("\n", "").trim()
            keystoreFile.writeBytes(Base64.getDecoder().decode(cleanBase64))
          } catch (_: Exception) {}
        }
        if (!keystoreFile.exists()) {
          val userHomeKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
          if (userHomeKeystore.exists()) {
            try {
              userHomeKeystore.copyTo(keystoreFile, overwrite = false)
            } catch (_: Exception) {}
          }
        }
        if (!keystoreFile.exists()) {
          try {
            val embeddedBase64 = "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"
            keystoreFile.writeBytes(Base64.getDecoder().decode(embeddedBase64))
          } catch (_: Exception) {}
        }
      }
      val userHomeKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
      storeFile = if (keystoreFile.exists()) keystoreFile else if (userHomeKeystore.exists()) userHomeKeystore else keystoreFile
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.mlkit.text.recognition)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
