import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.github.xiaohaoz1.jiarenxia"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jiarenxia.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 51
        versionName = "2.1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    // Room schema JSON 供单测 MigrationTestHelper 读取：
    // Robolectric 的 assets 来自 debug 合并产物（unit_test_config 的 android_merged_assets），
    // 故挂到 debug sourceSet；release 不受影响。
    sourceSets["debug"].assets.srcDir(projectDir.path + "/schemas")

    // 正式版签名：仓库根目录存在 keystore.properties 时才启用（见 AGENTS.md「正式版签名」）。
    // 文件与 *.jks/*.keystore 均在 .gitignore 中，不会进版本库。
    val signingPropsFile = rootProject.file("keystore.properties")
    if (signingPropsFile.exists()) {
        val props = Properties().apply {
            signingPropsFile.inputStream().use { load(it) }
        }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // debug 签名与正式版不同：换 applicationId 与正式版共存，预览不影响真实数据。
            // 应用名在 src/debug/res/values/strings.xml 覆盖为「家人侠-测试版」，
            // 官网下载页的 Beta 栏发布的就是这个包（jiarenxia-beta-<版本>.apk）。
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 无 keystore.properties 时保持未签名，绝不回落到 debug 签名
            if (signingPropsFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
        // androidx.baselineprofile 插件约定的采集变体（release 语义、不混淆、
        // 插件自动注入 profileable）。插件只在名字不存在时才自建，这里先声明
        // 以指定 debug 签名。注意：applicationId 与 release 相同（com.jiarenxia.app），
        // 但 debug 签名既不能覆盖已装的正式版，也不能覆盖 .debug 包；
        // 请在模拟器或没装正式版的设备上采集。
        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
        }
        // 真机实测变体：release 全量优化（含 R8）+ profileable + debug 签名，
        // 用于对比 baseline profile 生效前后的启动/帧数据。
        // 同样用 debug 签名打 com.jiarenxia.app：不能覆盖正式版或 .debug 包。
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric JVM 单元测试需要读取资源与清单
        unitTests.isIncludeAndroidResources = true
        // Roborazzi 截图：record 模式直接把 PNG 写到 build/outputs/roborazzi
        unitTests.all { it.systemProperty("roborazzi.test.record", "true") }
        // README/大字号截图：-PreadmeShots=true 才写 docs/images 与走查图
        unitTests.all {
            it.systemProperty(
                "jrx.readmeShots",
                providers.gradleProperty("readmeShots").orElse("false").get()
            )
        }
        // 测试家庭备份：-PtestFamily=true 才生成 testdata/jrx_test_family.jrxb
        unitTests.all {
            it.systemProperty(
                "jrx.testFamily",
                providers.gradleProperty("testFamily").orElse("false").get()
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // Room 数据库 Schema 导出目录，纳入版本管理
    arg("room.schemaLocation", projectDir.path + "/schemas")
}

dependencies {
    baselineProfile(project(":baselineprofile"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:kinship"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}
