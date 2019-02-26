/*
 * MIT License
 *
 * Copyright (c) 2019 Yoann Mikami
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

@file:Suppress("CanSealedSubClassBeObject")
package plugins

import annotationProcessors
import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import config.Libs
import implementationDependenciesFrom
import kaptAndroidTest
import kaptTest
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.kotlin.dsl.apply
import repositoriesFrom

sealed class BaseAndroidPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.all {
            when (it) {
                is JavaPlugin -> {
                    project.convention.getPlugin(JavaPluginConvention::class.java).apply {
                        configureJava()
                    }
                }
                is LibraryPlugin -> {
                    val extension = project.extensions.getByType(LibraryExtension::class.java)
                    extension.configureCommonBase()
                    extension.configureLibrary()
                    project.useKotlin()
                        .configureBasicDeps()
                        .repositoriesFrom(config.Repos.dependenciesRepoUrls)
                        .implementationDependenciesFrom(listOf(Libs.kotlin))
                }
                is AppPlugin -> {
                    val extension = project.extensions.getByType(AppExtension::class.java)
                    extension.configureCommonBase()
                    project.useKotlin()
                        .configureBasicDeps()
                        .repositoriesFrom(config.Repos.dependenciesRepoUrls)
                        .implementationDependenciesFrom(listOf(Libs.kotlin))
                }
            }
        }
    }
}


class LibraryModule : BaseAndroidPlugin() {

    override fun apply(project: Project) {
        project.plugins.apply("com.android.library")
        super.apply(project)
    }
}

private val featureDependencyList = listOf(
    Libs.AndroidX.core,
    Libs.AndroidX.coreKtx,
    Libs.AndroidX.fragmentKtx,
    Libs.AndroidX.material,
    Libs.AndroidX.fragment,
    Libs.AndroidX.appcompat,
    Libs.AndroidX.recyclerview,
    Libs.AndroidX.constraintlayout
)

class FeatureModule : BaseAndroidPlugin() {

    override fun apply(project: Project) {
        project.apply(plugin = "com.android.library")
        super.apply(project)
        project.implementationDependenciesFrom(featureDependencyList)
    }
}

class AppModule : BaseAndroidPlugin() {

    override fun apply(project: Project) {
        project.apply(plugin = "com.android.application")
        super.apply(project)
        project.implementationDependenciesFrom(
            featureDependencyList +
                    listOf(
                        Libs.threetenabp
                    )
        )
    }
}

class ButterknifeModule : Plugin<Project> {

    override fun apply(project: Project) {
        project.useKotlin()
            .implementationDependenciesFrom(
                listOf(Libs.Butterknife.core)
            )
            .annotationProcessors(
                listOf(Libs.Butterknife.Kapt.compiler)
            )
    }
}

class DaggerModule : Plugin<Project> {

    override fun apply(project: Project) {
        project.useKotlin()
            .implementationDependenciesFrom(
                listOf(
                    Libs.Dagger.dagger,
                    Libs.Dagger.daggerAndroid,
                    Libs.Dagger.daggerAndroidX
                )
            )
            .annotationProcessors(
                listOf(
                    Libs.Dagger.Kapt.daggerCompiler,
                    Libs.Dagger.Kapt.daggerProcessor
                )
            )
            .dependencies.apply {
            kaptAndroidTest(Libs.Dagger.Kapt.daggerCompiler)
            kaptTest(Libs.Dagger.Kapt.daggerProcessor)
        }
    }
}

class ArchModule : Plugin<Project> {

    override fun apply(project: Project) {
        project.implementationDependenciesFrom(
            listOf(
                Libs.AndroidX.Arch.core,

                Libs.AndroidX.Arch.Lifecycle.common,
                Libs.AndroidX.Arch.Lifecycle.extensions,

                Libs.AndroidX.Arch.Lifecycle.livedataCore,
                Libs.AndroidX.Arch.Lifecycle.livedata,

                Libs.AndroidX.Arch.Lifecycle.viewmodel,
                Libs.AndroidX.Arch.Lifecycle.reactivestreams,

                Libs.AndroidX.Arch.Room.common,
                Libs.AndroidX.Arch.Room.runtime,
                Libs.AndroidX.Arch.Room.rxjava2,

                Libs.AndroidX.Arch.Paging.common,
                Libs.AndroidX.Arch.Paging.runtime
            )
        )
            .annotationProcessors(
                listOf(
                    Libs.AndroidX.Arch.Lifecycle.Kapt.compiler,
                    Libs.AndroidX.Arch.Room.Kapt.compiler
                )
            )
    }
}
