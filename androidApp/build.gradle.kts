plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val supabaseUrl = providers.gradleProperty("AQUALOG_SUPABASE_URL")
    .orElse(providers.environmentVariable("AQUALOG_SUPABASE_URL"))
    .getOrElse("")
val supabaseAnonKey = providers.gradleProperty("AQUALOG_SUPABASE_ANON_KEY")
    .orElse(providers.environmentVariable("AQUALOG_SUPABASE_ANON_KEY"))
    .getOrElse("")
fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.maximerollin.aqualog"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.maximerollin.aqualog"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.maximerollin.aqualog.AquaLogTestRunner"
        buildConfigField("String", "SUPABASE_URL", supabaseUrl.asBuildConfigString())
        buildConfigField("String", "SUPABASE_ANON_KEY", supabaseAnonKey.asBuildConfigString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
