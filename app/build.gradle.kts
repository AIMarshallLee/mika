plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 关于页上的构建编号:当前提交的短哈希;没有 git(如从源码包编译)时写 dev
val gitHash: String = runCatching {
    providers.exec { commandLine("git", "rev-parse", "--short=7", "HEAD") }.standardOutput.asText.get().trim()
}.getOrNull()?.takeIf { it.isNotEmpty() } ?: "dev"

android {
    namespace = "com.wangzan101.mika"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wangzan101.mika"
        minSdk = 30
        targetSdk = 35
        versionCode = 38
        versionName = "0.1.37"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
        // 关于页版本号后的 Beta 标记;出正式版时改成 false
        buildConfigField("boolean", "BETA", "true")
        // 咔哒(昆仑增长)修改:品牌名、电脑连接默认端口、是否检查原版发布页,按构建类型给;原版构建保持原值
        buildConfigField("String", "BRAND_NAME", "\"Mika\"")
        buildConfigField("int", "MCP_DEFAULT_PORT", "8765")
        buildConfigField("boolean", "UPDATE_CHECK", "true")
        buildConfigField("long", "ACCENT_LIGHT", "0xFF0A7CFFL")
        buildConfigField("long", "ACCENT_DARK", "0xFF3D97FFL")
    }

    // 沙箱程序只有 arm64 版本(jniLibs/arm64-v8a);APK 不限 ABI,别的设备照常安装、显示「此设备不支持沙箱」。
    // 沙箱程序要从 nativeLibraryDir 直接执行,安装时必须解到磁盘
    packaging { jniLibs { useLegacyPackaging = true } }

    // 发布用的包(perf、release)按架构再出一份:除了全架构包,另出只带 arm64-v8a 原生库的小包。
    // 两个包代码、资源、版本号、签名都一样,只差 lib/ 下的其他架构;各自是完整 APK,不是一起安装的拆分包。
    // 产物:app-universal-<类型>.apk、app-arm64-v8a-<类型>.apk。拆分对所有构建类型生效,
    // 所以只在这次构建要出 perf/release 包时打开,单独构建调试包时产物仍是 app-debug.apk。
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { Regex("(?i)(perf|release)").containsMatchIn(it) }
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }
    // 自带的运行环境(assets/runtime/rootfs.tar.xz,tools/sandbox/build-runtime.py 生成)已是 xz,不再压一遍
    androidResources { noCompress += "xz" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 评测任务集(仓库根目录 eval/tasks)打进测试包的 assets,真机评测跑器从那里读题
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("eval/tasks"))

    buildTypes {
        // 真机验收时并排安装,保留发行版和用户数据;默认构建标识不变。
        if (providers.gradleProperty("devInstall").isPresent) getByName("debug") {
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "Mika Dev")
        }
        // 看流畅度用的包:和发布版一样不可调试(调试包不做预先编译,界面慢好几倍,手感不作数),
        // 用本机调试签名,能直接覆盖调试包、保留数据;可被性能工具采样。./gradlew :app:assemblePerf
        create("perf") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isProfileable = true
            matchingFallbacks += "release"
        }
        // 咔哒(昆仑增长)修改:咔哒手机版(白标)。代码与原版相同,只换应用标识、名字、图标和少量文字;
        // 资源覆盖放在 app/src/kada/res,原版资源一字不改,方便以后合并 Mika 上游。
        // 和发布版一样不可调试(界面流畅);演示阶段用本机调试签名,正式发布换成自己保管的发布密钥(不进仓库)。
        // 构建:./gradlew :app:assembleKada  或  tools/kada/build-demo.sh
        create("kada") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("String", "BRAND_NAME", "\"咔哒\"")
            buildConfigField("int", "MCP_DEFAULT_PORT", "18765")
            buildConfigField("boolean", "UPDATE_CHECK", "false")
            // 昆仑紫 #4A265F(和咔哒电脑端同一个主题色);深色模式用提亮的同色相紫,保证对比度
            buildConfigField("long", "ACCENT_LIGHT", "0xFF4A265FL")
            buildConfigField("long", "ACCENT_DARK", "0xFFB58BD6L")
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

// 咔哒(昆仑增长)修改:咔哒构建用自己的应用标识和版本号,与原版 Mika 并存安装、数据互不影响。
// 代码命名空间(com.wangzan101.mika)不变;清单里的 ${'$'}{applicationId}、Provider、助手进程名都跟着新标识走。
val kadaApplicationId = "com.kunlungrowth.kadadesk"
val kadaVersionName = "0.1.0-demo"
val kadaVersionCode = 1
androidComponents {
    onVariants(selector().withBuildType("kada")) { variant ->
        variant.applicationId.set(kadaApplicationId)
        variant.outputs.forEach { output ->
            output.versionName.set(kadaVersionName)
            output.versionCode.set(kadaVersionCode)
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.protobuf.lite)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.xz)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    // 两种识别模型都随 APK 安装；首次使用不依赖 Play 服务下载。
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    // 微信绑定二维码只在本机编码，不调用在线图片服务。
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // 沙箱路径契约(core 的 testFixtures):AndroidSandbox 在 JVM 与真机上都跑同一套
    testImplementation(testFixtures(project(":core")))
    androidTestImplementation(testFixtures(project(":core")))

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.junit)
}

// LicensesTest 读仓库的 licenses/THIRD_PARTY_NOTICES.md:让它参与测试的增量判断
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("licenses/THIRD_PARTY_NOTICES.md")).withPropertyName("thirdPartyNotices")
}
