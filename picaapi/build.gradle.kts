import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidLibrary {
        namespace = "com.picacompose.picaapi"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.ktor3.client.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            implementation(libs.ktor3.client.okhttp)
        }

        jvmMain.dependencies {
            implementation(libs.ktor3.client.cio)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
