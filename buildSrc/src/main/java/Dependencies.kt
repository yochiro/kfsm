// Requires package to allow nested object access in build.gradle
// https://github.com/handstandsam/AndroidDependencyManagement/issues/4
package dd

import org.gradle.api.JavaVersion

object BuildGradlePluginsVersions {
    const val gradlePluginVersion = "3.3.1"
    const val kotlinVersion = "1.3.21"
    const val novodaPluginVersion = "0.9"
}

private object KotlinXVersions {
    const val kotlinCoroutinesCore = "1.1.1"
}

private object LibVersions {
    const val butterKnifeVersion = "10.0.0"
    const val threetenAbpVersion = "1.1.0"
}

private object TestingVersions {
    const val testJUnitVersion = "4.13-beta-2"
    const val testRobolectricVersion = "4.2"
    const val testGoogleTruthVersion = "0.43"
}

object AndroidCompileOptions {

    const val compileSdkVersion = 28
    const val minSdkVersion = 15
    const val targetSdkVersion = 28
    val javaVersion = JavaVersion.VERSION_1_8
}

object Builds {
    const val androidGradlePlugin = "com.android.tools.build:gradle:${BuildGradlePluginsVersions.gradlePluginVersion}"
    const val kotlinGradlePlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${BuildGradlePluginsVersions.kotlinVersion}"
    const val butterknifeGradlePlugin = "com.jakewharton:butterknife-gradle-plugin:${LibVersions.butterKnifeVersion}"
    const val novodaGradlePlugin = "com.novoda:bintray-release:${BuildGradlePluginsVersions.novodaPluginVersion}"
}

object Libs {

    object Butterknife {

        object Kapt {
            const val compiler = "com.jakewharton:butterknife-compiler:${LibVersions.butterKnifeVersion}"
        }

        const val core = "com.jakewharton:butterknife:${LibVersions.butterKnifeVersion}"
    }

    object Kotlin {
        const val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${KotlinXVersions.kotlinCoroutinesCore}"
    }

    const val kotlin = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${BuildGradlePluginsVersions.kotlinVersion}"
    const val threetenabp = "com.jakewharton.threetenabp:threetenabp:${LibVersions.threetenAbpVersion}"
}

object TestingLibs {
    const val junit = "junit:junit:${TestingVersions.testJUnitVersion}"
    const val truth = "com.google.truth:truth:${TestingVersions.testGoogleTruthVersion}"
    const val robolectric = "org.robolectric:robolectric:${TestingVersions.testRobolectricVersion}"
}
