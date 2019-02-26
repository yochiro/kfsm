package dd

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.kotlin.dsl.ScriptHandlerScope
import java.net.URI

object Repos {

    val buildRepoUrls: List<String> = listOf(
    )

    val dependenciesRepoUrls: List<String> = listOf(
    )
}


fun RepositoryHandler.maven(repoUrl: String): MavenArtifactRepository {
    return maven { url = URI(repoUrl) }
}

// FIXME : Failed to work as a method extension
fun repositoriesFrom(project: Project, listOfRepos: List<String>) {
    project.repositories.run {
        listOfRepos.forEach { repo ->
            maven(repo)
        }
    }
}

// FIXME : Failed to work as a method extension
fun repositoriesFrom(scriptHandlerScope: ScriptHandlerScope, listOfRepos: List<String>) {
    scriptHandlerScope.repositories.run {
        listOfRepos.forEach { repo ->
            maven(repo)
        }
    }
}