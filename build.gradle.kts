plugins {
    `kotlin-dsl`
}

buildscript {
    repositoriesFrom(config.Repos.buildRepoUrls)
    repositories.jcenter()
    repositories.google()
    dependenciesFrom(config.Builds.basePlugins)

    dependencies.classpath("com.novoda:bintray-release:0.9.1")
}