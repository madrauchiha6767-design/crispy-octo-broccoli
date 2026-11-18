import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val pluginName = "RXRecorder"
val pluginPackageName = "org.godotengine.plugin.android.template"

android {
    namespace = pluginPackageName
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // MediaStore.RELATIVE_PATH and foreground media projection are used.
        minSdk = 29
        manifestPlaceholders["godotPluginName"] = pluginName
        manifestPlaceholders["godotPluginPackageName"] = pluginPackageName
        buildConfigField("String", "GODOT_PLUGIN_NAME", "\"$pluginName\"")
        setProperty("archivesBaseName", pluginName)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // Godot provides this library at runtime; compileOnly prevents bundling a second Godot engine.
    compileOnly("org.godotengine:godot:4.7.2.stable")
}

// Build both AAR variants and assemble a ready-to-copy Godot add-on directory.
tasks.register("prepareGodotAddon") {
    dependsOn("assembleDebug", "assembleRelease")
    doLast {
        val addonDir = layout.buildDirectory.dir("godot-addon/RXRecorder").get().asFile
        addonDir.deleteRecursively()
        copy {
            from("export_scripts_template")
            into(addonDir)
        }
        copy {
            from(layout.buildDirectory.file("outputs/aar/RXRecorder-debug.aar").get().asFile)
            into(addonDir.resolve("bin/debug"))
        }
        copy {
            from(layout.buildDirectory.file("outputs/aar/RXRecorder-release.aar").get().asFile)
            into(addonDir.resolve("bin/release"))
        }
    }
}
