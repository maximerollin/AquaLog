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
    "FIREBASE_PROJECT_ID", "FEEDBACK_URL",
)
val environments = listOf("DEV", "BETA", "PROD")
require(externalFields.none { configuredValue("AQUALOG_$it").isNotBlank() }) {
    "Use environment-scoped AQUALOG_DEV_, AQUALOG_BETA_ or AQUALOG_PROD_ configuration"
}
environments.forEach { environment ->
    mapOf(
        "SUPABASE" to listOf("SUPABASE_URL", "SUPABASE_ANON_KEY"),
        "POSTHOG" to listOf("POSTHOG_HOST", "POSTHOG_API_KEY"),
        "FIREBASE" to listOf("FIREBASE_APPLICATION_ID", "FIREBASE_API_KEY", "FIREBASE_PROJECT_ID"),
    ).forEach { (service, fields) ->
        val configured = fields.count { configuredValue("AQUALOG_${environment}_$it").isNotBlank() }
        require(configured == 0 || configured == fields.size) { "$environment $service configuration is incomplete" }
    }
}
listOf("SUPABASE_URL", "SUPABASE_ANON_KEY", "POSTHOG_API_KEY", "FIREBASE_APPLICATION_ID", "FIREBASE_PROJECT_ID")
    .forEach { field ->
        val values = environments.map { configuredValue("AQUALOG_${it}_$field").trim().trimEnd('/') }
            .filter(String::isNotBlank)
        require(values.distinct().size == values.size) { "$field must differ between environments" }
    }
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
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"

    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testBuildType = providers.gradleProperty("aqualogTestBuildType").getOrElse("debug")

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
        }
        create("beta") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".beta"
            matchingFallbacks += "debug"
        }
        all {
            val environment = when (name) {
                "debug" -> "DEV"
                "beta" -> "BETA"
                else -> "PROD"
            }
            val authScheme = when (name) {
                "debug" -> "aqualog-dev"
                "beta" -> "aqualog-beta"
                else -> "aqualog"
            }
            manifestPlaceholders["authScheme"] = authScheme
            buildConfigField("String", "AUTH_REDIRECT_URL", "$authScheme://auth/callback".asBuildConfigString())
            externalFields.forEach { field ->
                buildConfigField("String", field, configuredValue("AQUALOG_${environment}_$field").asBuildConfigString())
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
    implementation(libs.google.play.billing)
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
