import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// 模型类在 :picaapi 里编译，Compose 编译器看不到它们的字段全是 val，默认当成不稳定类型；
// 登记进稳定清单，列表项在父级重组时才能整块跳过。
composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose-stability.conf"),
    )
    // 编译器报告落到 build/compose-reports，想看谁「不能跳过」就翻这里。
    reportsDestination = layout.buildDirectory.dir("compose-reports")
}

kotlin {
    androidLibrary {
        namespace = "com.picacompose.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.picaapi)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.animation)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(libs.miuix.ui)
            implementation(libs.cupertino)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            // 应用内小服务：本机监听一个随机端口给 WebView 用（见 LocalHttpServer）。
            implementation(libs.ktor3.network)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }

        // 帮助页：resources/help/ 下的 index.html 和配图，构建时原样拷进包。
        // 以 resources/ 为资源根，所以代码里按 help/xxx 取；Android 和桌面读的是同一份。
        // 不写成 Kotlin 字符串：那得往源码里塞几十 K 字符，编辑器难受，编译器也要多存一份常量。
        val resDir = rootProject.layout.projectDirectory.dir("resources")
        jvmMain.get().resources.srcDir(resDir)
        androidMain.get().resources.srcDir(resDir)

        // Desktop 和 Android 都是 JVM：下载用的文件操作只写一份，两边共用。
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmCommonMain)
        jvmMain.get().dependsOn(jvmCommonMain)
    }
}

