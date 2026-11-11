import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: the keystore and its passwords live OUTSIDE the repo.
// Point AIQA_KEYSTORE_PROPS at a .properties file with storeFile, storePassword, keyAlias, keyPassword.
val signingProps = System.getenv("AIQA_KEYSTORE_PROPS")?.let { path ->
    Properties().apply { file(path).inputStream().use { load(it) } }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

android {
    namespace = "app.aiqadigest"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "app.aiqadigest"
        minSdk = 33
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.0"
        // The only network endpoint of the app.
        buildConfigField("String", "DIGEST_URL", "\"https://actionsslave.github.io/ai-qa-digest/digest.json\"")
        // Nostr: author key and relays come from job/nostr.json (the same file the publishing job uses).
        val nostr = groovy.json.JsonSlurper().parse(rootProject.file("job/nostr.json")) as Map<*, *>
        buildConfigField("String", "NOSTR_PUBKEY", "\"${nostr["pubkey"]}\"")
        buildConfigField("String", "NOSTR_RELAYS", "\"${(nostr["relays"] as List<*>).joinToString(",")}\"")
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingProps?.let { signingConfig = signingConfigs.getByName("release") }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // The secp256k1 test native is extracted to java.io.tmpdir; use a directory under build/ (some systems mount /tmp noexec).
        unitTests.all {
            val tmp = layout.buildDirectory.dir("tmp/native").get().asFile
            it.systemProperty("java.io.tmpdir", tmp.absolutePath)
            it.doFirst { tmp.mkdirs() }
        }
    }

    lint { checkReleaseBuilds = false }

    // No dependency metadata blob in the APK (it is only useful for Google tooling).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.secp256k1.jni.android)
    implementation(libs.secp256k1.jvm)

    testImplementation(libs.junit)
    testImplementation(libs.secp256k1.jni.jvm)
}
