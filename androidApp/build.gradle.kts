import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

android {
    namespace = "com.picacompose"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.picacompose"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 4
        versionName = "0.0.4"
    }

    buildTypes {
        getByName("release") {
            // 只裁剪，不做混淆和优化：删掉没用到的库代码和资源，但类名保持原样
            // （-dontobfuscate / -dontoptimize 写在 proguard-rules.pro 末尾）。
            // 资源压缩必须有 R8 参与，AGP 不允许「只裁资源不裁代码」。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 发出去的那个包用 release 构建：只有非 debuggable 的包，系统才会在安装时
            // 按 assets/dexopt/baseline.prof 做 AOT 编译（debug 包的 dexopt 状态是
            // run-from-apk，profile 直接被忽略）。签名沿用 debug key，跟以前发出去的
            // 包同一个证书，老用户能直接覆盖安装。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // 这台打包机是离线的，lint 那一套 jar 不在缓存里；release 版就不跑 lint 了。
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // picacomic-api 用了 java.time，minSdk 24 需要脱糖
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
