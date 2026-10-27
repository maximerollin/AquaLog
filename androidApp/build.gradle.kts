plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.firebase.crashlytics)
}

fun configuredValue(name: String) = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name))
    .getOrElse("")
val externalFields = listOf(
    "SUPABASE_URL", "SUPABASE_ANON_KEY", "PRIVACY_URL", "SUPPORT_URL",
    "POSTHOG_API_KEY", "POSTHOG_HOST", "FIREBASE_APPLICATION_ID", "FIREBASE_API_KEY",
    "FIREBASE_PROJECT_ID", "FEEDBACK_URL", "REVENUECAT_PUBLIC_API_KEY", "LOGSNAG_PROJECT", "LOGSNAG_REVIEW_PUBLIC_TOKEN",
)
require(externalFields.none { field -> listOf("DEV", "BETA", "PROD").any { configuredValue("AQUALOG_${it}_$field").isNotBlank() } }) {
    "Use the shared AQUALOG_ configuration; separate DEV/BETA/PROD projects are retired"
}
mapOf(
    "SUPABASE" to listOf("SUPABASE_URL", "SUPABASE_ANON_KEY"),
    "POSTHOG" to listOf("POSTHOG_HOST", "POSTHOG_API_KEY"),
    "FIREBASE" to listOf("FIREBASE_APPLICATION_ID", "FIREBASE_API_KEY", "FIREBASE_PROJECT_ID"),
    "LOGSNAG_REVIEW" to listOf("LOGSNAG_PROJECT", "LOGSNAG_REVIEW_PUBLIC_TOKEN"),
).forEach { (service, fields) ->
    val count = fields.count { configuredValue("AQUALOG_$it").isNotBlank() }
    require(count == 0 || count == fields.size) { "$service configuration is incomplete" }
}
val revenueCatKey = configuredValue("AQUALOG_REVENUECAT_PUBLIC_API_KEY")
require(revenueCatKey.isBlank() || revenueCatKey.startsWith("goog_")) {
    "RevenueCat requires a public Google Play SDK key (goog_), never a secret or Test Store key"
}
val distributionTrack = configuredValue("AQUALOG_DISTRIBUTION_TRACK").ifBlank { "local" }
require(distributionTrack in setOf("local", "internal", "alpha", "production")) { "Invalid distribution track" }
val aqualogVersionCode = providers.gradleProperty("AQUALOG_VERSION_CODE").map(String::toInt).orElse(1)
val aqualogVersionName = providers.gradleProperty("AQUALOG_VERSION_NAME").orElse("0.1.0")
fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.maximerollin.aqualog"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.maximerollin.aqualog"
        minSdk = 23
        targetSdk = 37
        versionCode = aqualogVersionCode.get()
        versionName = aqualogVersionName.get()
        testInstrumentationRunner = "com.maximerollin.aqualog.AquaLogTestRunner"
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"

    }

    testOptions {
        // Each instrumented test runs in its own process, so no activity or window survives into the next one.
        // Package data is kept: the opt-in Play transaction test reuses the purchase made on this installation.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testBuildType = providers.gradleProperty("aqualogTestBuildType").getOrElse("debug")

    buildTypes {
        all {
            manifestPlaceholders["authScheme"] = "aqualog"
            buildConfigField("String", "AUTH_REDIRECT_URL", "aqualog://auth/callback".asBuildConfigString())
            buildConfigField("String", "DISTRIBUTION_TRACK", distributionTrack.asBuildConfigString())
            externalFields.forEach { field ->
                buildConfigField("String", field, configuredValue("AQUALOG_$field").asBuildConfigString())
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Firebase is initialised manually from BuildConfig, so there is no
            // google-services.json for the Crashlytics plugin to upload against.
            // R8 still emits mapping.txt; the release workflow ships it to Play.
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
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

androidComponents {
    beforeVariants { variant ->
        (variant as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = true
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
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.revenuecat.purchases)
    implementation(libs.google.play.review)
    implementation(libs.posthog.android)
    implementation(libs.firebase.crashlytics)
    implementation(libs.androidx.work.runtime)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.services.storage)
    androidTestUtil(libs.androidx.test.services)
    androidTestUtil(libs.androidx.test.orchestrator)
}

val collectLocalizedAccessibilityReports by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/connected_android_test_additional_output/debugAndroidTest/connected")) {
        include("**/localized-accessibility/*.png")
        eachFile { relativePath = org.gradle.api.file.RelativePath(true, name) }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("reports/localized-accessibility"))
}

tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    finalizedBy(collectLocalizedAccessibilityReports)
}
