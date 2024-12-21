package dev.kikugie.stonecutter.build

import dev.kikugie.stonecutter.*
import dev.kikugie.stonecutter.StonecutterPlugin
import dev.kikugie.stonecutter.data.ProjectHierarchy.Companion.hierarchy
import dev.kikugie.stonecutter.data.ProjectHierarchy.Companion.locate
import dev.kikugie.stonecutter.data.StonecutterProject
import dev.kikugie.stonecutter.data.container.ConfigurationService.Companion.of
import dev.kikugie.stonecutter.data.tree.*
import dev.kikugie.stonecutter.process.StonecutterTask
import dev.kikugie.stonecutter.projectPath
import groovy.lang.MissingPropertyException
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString

// link: wiki-build
/**
 * Stonecutter plugin applied to the versioned buildscript.
 *
 * @property project This plugin's project
 * @see <a href="https://stonecutter.kikugie.dev/stonecutter/guide/setup#versioning-build-gradle-kts">Wiki page</a>
 */
@OptIn(ExperimentalPathApi::class)
open class StonecutterBuild(private val project: Project) : BuildAbstraction(project.hierarchy), StonecutterUtility {
    private val parent: Project = requireNotNull(project.parent) { "No parent project for '${project.path}'" }

    /**Project tree instance containing the necessary data and safe to use with configuration cache.
     * @see [withProject]*/
    @StonecutterAPI val tree: LightTree = StonecutterPlugin.SERVICE.of(parent.hierarchy).tree
        ?: error("Tree for '${project.path}' not found")

    /**Branch this node belongs to containing the necessary data and safe to use with configuration cache.
     * @see [withProject]*/
    @StonecutterAPI val branch: LightBranch = tree[parent.hierarchy.last().removePrefix(":")]
        ?: error("Branch for '${project.path}' not found")

    /**This project's node containing only the necessary data and safe to use with configuration cache.
     * @see [withProject]*/
    @StonecutterAPI val node: LightNode = branch[project.hierarchy.last().removePrefix(":")]
        ?: error("Node for '${project.path}' not found")

    /**All versions in this project's branch.*/
    @StonecutterAPI val versions: Collection<StonecutterProject> get() = branch.versions

    /**The currently active version. Global for all instances of the build file.*/
    @StonecutterAPI val active: StonecutterProject get() = tree.current

    /**Metadata of the currently processed version.*/
    @StonecutterAPI val current: StonecutterProject = node.metadata

    /**Creates a tree wrapper that implements its Gradle [Project].
     * Can be used to retrieve properties from other projects, but unsafe to use in tasks.*/
    @StonecutterDelicate fun withProject(tree: LightTree): ProjectTree =
        tree.withProject(project.locate(tree.hierarchy))

    /**Creates a branch wrapper that implements its Gradle [Project].
     * Can be used to retrieve properties from other projects, but unsafe to use in tasks.*/
    @StonecutterDelicate fun withProject(branch: LightBranch): ProjectBranch =
        branch.withProject(project.locate(branch.hierarchy))

    /**Creates a node wrapper that implements its Gradle [Project].
     * Can be used to retrieve properties from other projects, but unsafe to use in tasks.*/
    @StonecutterDelicate fun withProject(node: LightNode): ProjectNode =
        node.withProject(project.locate(node.hierarchy))

    init {
        createSetupTask()
        project.afterEvaluate {
            configureProject()
            serializeNode()
        }
    }

    private fun createSetupTask() = project.tasks.register<StonecutterTask>("setupChiseledBuild") {
        val chiseledSrc = project.projectPath.resolve("build/chiseledSrc")
        instance(project.hierarchy)

        fromVersion.set(active)
        toVersion.set(current)

        input("src")
        output(parent.projectPath.relativize(chiseledSrc).invariantSeparatorsPathString)
        sources.set(listOf(branch))

        parameters(StonecutterPlugin.SERVICE().snapshot())
        doFirst {
            chiseledSrc
                .runCatching { if (exists()) deleteRecursively() }
                .onFailure { logger.warn("Failed to clean chiseledSrc", it) }
        }
    }

    private fun configureProject() = with(project) {
        try {
            val globalParameters = StonecutterPlugin.SERVICE.of(hierarchy).global
                ?: error("No global parameters for '${hierarchy}'")
            val useChiseledSrc =
                globalParameters.process && globalParameters.hasChiseled(gradle.startParameter.taskNames)
            val formatter: (Path) -> Any = when {
                useChiseledSrc -> { src -> projectDir.resolve("build/chiseledSrc/$src") }
                current.isActive -> { src -> "../../src/$src" }
                else -> return
            }

            val parentDir = parent!!.projectDir.resolve("src").toPath()
            val thisDir = projectDir.resolve("src").toPath()

            fun applyChiseled(from: SourceDirectorySet, to: SourceDirectorySet = from) {
                from.sourceDirectories.mapNotNull {
                    val relative = thisDir.relativize(it.toPath())
                    if (relative.startsWith(".."))
                        return@mapNotNull if (current.isActive) null
                        else parentDir.relativize(it.toPath())
                    else relative
                }.forEach {
                    to.srcDir(formatter(it))
                }
            }

            for (src in property("sourceSets") as SourceSetContainer) {
                applyChiseled(src.allJava, src.java)
                applyChiseled(src.resources)
                src.extensions.extensionsSchema
                    .filter { it.publicType.concreteClass.interfaces.contains(SourceDirectorySet::class.java) }
                    .forEach { applyChiseled(src.extensions[it.name] as SourceDirectorySet) }
            }
        } catch (_: MissingPropertyException) {
        }
    }

    private fun serializeNode() {
        NodeModel(
            current,
            node.location.relativize(tree.location),
            BranchInfo(branch.id, node.location.relativize(branch.location)),
            current.isActive,
            data
        ).save(tree.location.resolve("build/stonecutter-cache")).onFailure {
            project.logger.warn("Failed to save node model for '${branch.id}:${current.project}'", it)
        }
    }
}