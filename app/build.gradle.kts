plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "in.qaida.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.qaida.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    }
    androidResources {
        // Opus clips are already compressed; keep them stored so they can be memory-mapped.
        noCompress += "opus"
    }
    testOptions {
        unitTests.all { test ->
            test.useJUnitPlatform()
            // Asset roots in override order, mirroring how AGP merges assets for the variant under test.
            val buildType = if (test.name.contains("Release")) "release" else "debug"
            test.systemProperty("qaida.assetRoots", listOf("src/$buildType/assets", "src/main/assets").joinToString(File.pathSeparator) { file(it).path })
            test.systemProperty("qaida.releaseRoots", listOf("src/release", "src/main").joinToString(File.pathSeparator) { file(it).path })
            // The tests read these trees from disk; declare them so a content change re-runs the tests.
            test.inputs.files(listOf("main", "debug", "release").map { fileTree("src/$it") { include("assets/**", "res/**") } })
                .withPropertyName("contentTrees")
                .withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// The stub pack lives in src/debug and must never be what a release ships. Refuse to package a
// release until real content has been supplied in src/main/assets/content.
val checkReleaseContent by tasks.registering {
    val letters = file("src/main/assets/content/letters.json")
    doLast {
        check(letters.exists()) {
            "No real content: $letters is missing. Release builds cannot ship the debug stub pack."
        }
    }
}
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease") }
    .configureEach { dependsOn(checkReleaseContent) }

// The app's core promise is that nothing leaves the device. A library can add permissions to the merged
// manifest silently, so every variant's merged manifest is checked against this allowlist.
val allowedPermissions = setOf(
    "in.qaida.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", // androidx.core, app-private, signature-level
)
androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val check = tasks.register("verify${name}ManifestPermissions") {
            val manifest = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
            inputs.file(manifest)
            doLast {
                val text = manifest.get().asFile.readText()
                val declared = Regex("""<uses-permission(?:-sdk-23)?\s+android:name="([^"]+)"""").findAll(text).map { it.groupValues[1] }.toSet()
                val unexpected = declared - allowedPermissions
                check(unexpected.isEmpty()) { "Unexpected permissions in the ${variant.name} merged manifest: $unexpected" }
            }
        }
        tasks.matching { it.name == "assemble$name" || it.name == "bundle$name" || it.name == "check" }
            .configureEach { dependsOn(check) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.media3.exoplayer)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit.jupiter)
    // android.jar only stubs org.json on the JVM; the real implementation is needed to run the content parser in unit tests.
    testImplementation(libs.org.json)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
