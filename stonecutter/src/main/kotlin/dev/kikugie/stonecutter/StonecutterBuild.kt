package dev.kikugie.stonecutter

import dev.kikugie.experimentalstonecutter.StonecutterProject
import dev.kikugie.experimentalstonecutter.StonecutterUtility
import dev.kikugie.experimentalstonecutter.build.BuildConfiguration
import dev.kikugie.experimentalstonecutter.build.StonecutterData
import dev.kikugie.semver.SemanticVersion
import dev.kikugie.semver.VersionParser
import dev.kikugie.semver.VersionParsingException
import dev.kikugie.stitcher.lexer.IdentifierRecognizer.Companion.allowed
import dev.kikugie.stonecutter.configuration.*
import groovy.lang.MissingPropertyException
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSetContainer
import java.io.File
import java.nio.file.Path
import kotlin.collections.set
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

/**
 * Stonecutter plugin applied to the versioned build file.
 *
 * @property project The effective Gradle project
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
@OptIn(ExperimentalPathApi::class)
open class StonecutterBuild internal constructor(val project: Project) : BuildConfiguration, StonecutterUtility {
    internal val setup = project.parent?.let {
        project.gradle.extensions.getByType(StonecutterSetup.Container::class.java)[it]
    } ?: throw StonecutterGradleException(
        "Project ${project.path} must be a versioned project."
    )
    internal val data = StonecutterData()

    /**
     * Metadata of the currently processed version.
     */
    val current: StonecutterProject by lazy {
        setup.versions.first { it.project == project.name }.let {
            if (it.project == setup.current.project) it.asActive() else it
        }
    }

    /**
     * The currently active version. Global for all instances of the build file.
     */
    val active get() = setup.current

    /**
     * All available versions.
     */
    val versions get() = setup.versions

    override fun swap(identifier: String, replacement: String) {
        data.swaps[validateId(identifier)] = replacement
    }

    override fun const(identifier: String, value: Boolean) {
        data.constants[validateId(identifier)] = value
    }

    override fun dependency(identifier: String, version: String) {
        data.dependencies[validateId(identifier)] = validateSemver(version)
    }

    override fun exclude(path: Path) {
        data.excludedPaths.add(path)
    }

    override fun exclude(path: String) {
        require(path.isNotBlank()) { "Path must not be empty" }
        if (path.startsWith("*.")) data.excludedExtensions.add(path.substring(2))
        else data.excludedPaths.add(project.parent!!.file(path).toPath())
    }

    override var debug: Boolean
        get() = data.debug
        set(value) {
            data.debug = value
        }

    init {
        project.tasks.register("setupChiseledBuild", StonecutterTask::class.java) {
            if (project.parent == null) throw StonecutterGradleException(
                "Chiseled task can't be registered for the root project. How did you manage to do it though?"
            )

            toVersion.set(current)
            fromVersion.set(active)

            chiseled.set(true)
            data.set(this@StonecutterBuild.data)

            input.set(project.parent!!.file("./src").toPath())
            val out = project.buildDirectory.toPath().resolve("chiseledSrc")
            if (out.exists()) out.deleteRecursively()
            output.set(out)
        }

        project.afterEvaluate {
            configureSources()
            writeBuildModel(this@StonecutterBuild)
        }
    }

    private fun validateId(id: String): String {
        require(id.all(::allowed)) { "Invalid identifier: $id" }
        return id
    }

    private fun validateSemver(ver: String): SemanticVersion = try {
        VersionParser.parse(ver)
    } catch (e: VersionParsingException) {
        throw IllegalArgumentException("Invalid semantic version: $ver").apply {
            initCause(e)
        }
    }

    private fun configureSources() {
        try {
            val useChiseledSrc = setup.anyChiseled(project.gradle.startParameter.taskNames)
            val formatter: (Path) -> Any = when {
                useChiseledSrc   -> { src -> File(project.buildDirectory, "chiseledSrc/$src") }
                current.isActive -> { src -> "../../src/$src" }
                else             -> return
            }

            val parentDir = project.parent!!.projectDir.resolve("src").toPath()
            val thisDir = project.projectDir.resolve("src").toPath()

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

            for (it in project.property("sourceSets") as SourceSetContainer) {
                applyChiseled(it.allJava, it.java)
                applyChiseled(it.resources)
            }
        } catch (_: MissingPropertyException) {
        }
    }
}