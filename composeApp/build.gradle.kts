import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        add(project.projectDir.path)
                    }
                }
            }
        }
        binaries.executable()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.2")
        }
    }
}

val rawBuild = System.getenv("PROJECT_BUILD_NUMBER")?.toIntOrNull()
    ?: System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
    ?: 1
val ciBuildNumber = if (rawBuild < 100) rawBuild + 100 else rawBuild

android {
    namespace = "cz.mares.drpilulka"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.mares.drpilulka"
        minSdk = 24
        targetSdk = 36
        versionCode = ciBuildNumber
        versionName = "1.0.$ciBuildNumber"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("CM_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank() && File(keystorePath).exists()) {
                storeFile = File(keystorePath)
                storePassword = System.getenv("CM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CM_KEY_ALIAS")
                keyPassword = System.getenv("CM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystorePath = System.getenv("CM_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank() && File(keystorePath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val generateIosIcon by tasks.registering {
    doLast {
        val iconDir = rootProject.file("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset")
        iconDir.mkdirs()
        val iconFile = File(iconDir, "app-icon-1024.png")
        val width = 1024
        val height = 1024

        val rawData = ByteArrayOutputStream(height * (1 + width * 3))
        for (y in 0 until height) {
            rawData.write(0)
            for (x in 0 until width) {
                var r = 12
                var g = 18
                var b = 34

                // Retro mrizka na pozadi
                if ((x % 64 < 3) || (y % 64 < 3)) {
                    r = 22
                    g = 32
                    b = 58
                }

                // Obrys lekarske lahvicky
                val inBottleNeck = (y in 120..230) && (x in 370..654)
                val inBottleRim = (y in 90..125) && (x in 340..684)
                val inBottleBody = (y in 230..910) && (x in 210..814)

                if (inBottleRim || inBottleNeck || inBottleBody) {
                    val border = (inBottleBody && (x < 234 || x > 790 || y > 886 || (y < 254 && (x < 370 || x > 654)))) ||
                        (inBottleNeck && (x < 394 || x > 630)) ||
                        (inBottleRim && (x < 364 || x > 660 || y < 112))
                    if (border) {
                        r = 226
                        g = 232
                        b = 240
                    } else {
                        r = 7
                        g = 10
                        b = 20
                    }
                }

                // Sikma dvoubarevna pilulka uprostred lahvicky
                val dx = x - 512.0
                val dy = y - 540.0
                val rotX = dx * 0.7071 + dy * 0.7071
                val rotY = -dx * 0.7071 + dy * 0.7071

                val halfLen = 135.0
                val radius = 115.0
                val clampedX = when {
                    rotX < -halfLen -> -halfLen
                    rotX > halfLen -> halfLen
                    else -> rotX
                }
                val dist = sqrt((rotX - clampedX) * (rotX - clampedX) + rotY * rotY)

                if (dist <= radius + 16.0) {
                    if (dist > radius) {
                        r = 255
                        g = 255
                        b = 255
                    } else if (abs(rotX) < 8.0) {
                        r = 15
                        g = 23
                        b = 42
                    } else if (rotX < 0) {
                        // Cervena polovina pilulky
                        r = 239
                        g = 68
                        b = 68
                        if (rotY < -35.0 && rotY > -80.0 && rotX > -115.0 && rotX < -25.0) {
                            r = 254
                            g = 202
                            b = 202
                        }
                    } else {
                        // Modra polovina pilulky
                        r = 59
                        g = 130
                        b = 246
                        if (rotY < -35.0 && rotY > -80.0 && rotX > 25.0 && rotX < 115.0) {
                            r = 191
                            g = 219
                            b = 254
                        }
                    }
                }

                rawData.write(r)
                rawData.write(g)
                rawData.write(b)
            }
        }

        fun writeChunk(out: DataOutputStream, type: String, data: ByteArray) {
            out.writeInt(data.size)
            val typeBytes = type.toByteArray(Charsets.US_ASCII)
            out.write(typeBytes)
            out.write(data)
            val crc = CRC32()
            crc.update(typeBytes)
            crc.update(data)
            out.writeInt(crc.value.toInt())
        }

        val pngOut = ByteArrayOutputStream()
        val dataOut = DataOutputStream(pngOut)
        dataOut.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        val ihdr = ByteArrayOutputStream()
        val ihdrOut = DataOutputStream(ihdr)
        ihdrOut.writeInt(width)
        ihdrOut.writeInt(height)
        ihdrOut.writeByte(8)
        ihdrOut.writeByte(2) // Ciste RGB bez alfa kanalu
        ihdrOut.writeByte(0)
        ihdrOut.writeByte(0)
        ihdrOut.writeByte(0)
        writeChunk(dataOut, "IHDR", ihdr.toByteArray())

        val idatBos = ByteArrayOutputStream()
        DeflaterOutputStream(idatBos).use { it.write(rawData.toByteArray()) }
        writeChunk(dataOut, "IDAT", idatBos.toByteArray())

        writeChunk(dataOut, "IEND", ByteArray(0))
        iconFile.writeBytes(pngOut.toByteArray())
    }
}

tasks.matching { it.name.startsWith("link") && it.name.contains("FrameworkIos") }.configureEach {
    dependsOn(generateIosIcon)
}
