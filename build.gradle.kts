plugins {
    `kotlin-dsl`
}

buildscript {
    dd.repositoriesFrom(this, dd.Repos.buildRepoUrls)
    repositories.jcenter()
    repositories.google()
    dependencies {
        (dd.Builds.run {
            listOf(androidGradlePlugin, kotlinGradlePlugin, novodaGradlePlugin)
        }).forEach { cp ->
            classpath(cp)
        }
    }
}

subprojects {
    buildscript {
        dd.repositoriesFrom(this, dd.Repos.buildRepoUrls)
        repositories.jcenter()
        repositories.google()
    }
    dd.repositoriesFrom(this, dd.Repos.dependenciesRepoUrls)
    repositories.jcenter()
    repositories.google()
}

repositories {
    jcenter()
}

enum class ModuleType(val suffix: String) { LIBRARY("lib"), FEATURE("feature") }

fun buildScriptPath(moduleType: ModuleType, withButterKnife: Boolean) =
    File(baseBuildScriptPath, "build_${moduleType.suffix}${if (withButterKnife) "_bk" else ""}.gradle")

val baseBuildScriptPath by extra { file("build_scripts") }
val libModuleBuildScriptFile by extra { buildScriptPath(ModuleType.LIBRARY, false) }
val libModuleWithButterKnifeBuildScriptFile by extra { buildScriptPath(ModuleType.LIBRARY, true) }
val butterKnifeBuildScriptFile by extra { File(baseBuildScriptPath, "butterknife.gradle") }
val publishBuildScriptFile by extra { File(baseBuildScriptPath, "publish.gradle") }