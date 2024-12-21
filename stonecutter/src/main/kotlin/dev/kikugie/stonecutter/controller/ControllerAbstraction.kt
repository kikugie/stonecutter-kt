package dev.kikugie.stonecutter.controller

import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.StonecutterAPI
import dev.kikugie.stonecutter.StonecutterDelicate
import dev.kikugie.stonecutter.build.StonecutterBuild
import dev.kikugie.stonecutter.controller.manager.ControllerManager
import dev.kikugie.stonecutter.controller.manager.controller
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.ProjectHierarchy.Companion.locate
import dev.kikugie.stonecutter.data.StonecutterProject
import dev.kikugie.stonecutter.data.container.TreeBuilderContainer
import dev.kikugie.stonecutter.data.container.getContainer
import dev.kikugie.stonecutter.data.parameters.GlobalParameters
import dev.kikugie.stonecutter.data.tree.*
import dev.kikugie.stonecutter.projectPath
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

internal typealias BranchEntry = Pair<LightBranch, StonecutterProject>

/**Separates public API properties from the controller configuration functionality.*/
abstract class ControllerAbstraction(protected val root: Project) {
    protected val parameters: GlobalParameters = GlobalParameters()
    protected val configurations: MutableMap<BranchEntry, ParameterHolder> = mutableMapOf()
    protected val builds: MutableList<Action<StonecutterBuild>> = mutableListOf()
    private val manager: ControllerManager = checkNotNull(root.controller()) {
        "Project ${root.path} is not a Stonecutter controller. What did you even do to get this error?"
    }

    /**Project tree instance containing the necessary data and safe to use with configuration cache.
     * @see [withProject]*/
    @StonecutterAPI val tree: LightTree = constructTree()

    /**Version control project used by the `Reset active project` task.*/
    @StonecutterAPI val vcsVersion: StonecutterProject get() = tree.vcs

    /**All unique versions registered in the tree.
     * Branches may have the same or a subset of these.*/
    @StonecutterAPI val versions: Collection<StonecutterProject> get() = tree.versions
    // link: wiki-controller-active
    /**
     * The active version selected by `stonecutter.active "..."` call.
     * @see <a href="https://stonecutter.kikugie.dev/stonecutter/guide/setup#active-version">Wiki page</a>
     */
    @StonecutterAPI val current: StonecutterProject get() = tree.current

    /**Type of the chiseled task. Used with [registerChiseled].*/
    @StonecutterAPI val chiseled: Class<ChiseledTask> = ChiseledTask::class.java

    /**Creates a tree wrapper that implements its Gradle [Project].
     * Can be used to retrieve properties from other projects, but unsafe to use in tasks.*/
    @StonecutterDelicate fun withProject(tree: LightTree): ProjectTree =
        tree.withProject(root.locate(tree.hierarchy))

    /**Creates a branch wrapper that implements its Gradle [Project].
     * Can be used to retrieve properties from other projects, but unsafe to use in tasks.*/
    @StonecutterDelicate fun withProject(branch: LightBranch): ProjectBranch =
        branch.withProject(root.locate(branch.hierarchy))

    /**Creates a node wrapper that implements its Gradle [Project].
     * Can be used to retrieve properties from other projects, but unsafe to use in tasks.*/
    @StonecutterDelicate fun withProject(node: LightNode): ProjectNode =
        node.withProject(root.locate(node.hierarchy))

    // link: wiki-controller-active
    /**
     * Sets the active project. **DO NOT call on your own**.
     * @see <a href="https://stonecutter.kikugie.dev/stonecutter/guide/setup#active-version">Wiki page</a>
     */
    infix fun active(name: Identifier) = with(tree) {
        current.isActive = false
        current = versions.find { it.project == name } ?: error("Project $name is not registered in ${root.path}")
        current.isActive = true
    }

    // link: wiki-chisel
    /**
     * Registers the task as chiseled. This is required for all tasks that need to build all versions.
     *
     * @see [ChiseledTask]
     * @see <a href="https://stonecutter.kikugie.dev/stonecutter/guide/setup#chiseled-tasks">Wiki page</a>
     */
    @StonecutterAPI infix fun registerChiseled(provider: TaskProvider<*>) {
        parameters.addTask(provider.name)
    }

    // link: wiki-controller-params
    /**
     * Specifies configurations for all combinations of versions and branches.
     * This provides parameters for the processor to use non-existing versions.
     * If the given version exists, it will be applied to the [StonecutterBuild].
     *
     * @see <a href="https://stonecutter.kikugie.dev/stonecutter/guide/setup#global-parameters">Wiki page</a>
     */
    @StonecutterAPI infix fun parameters(configuration: Action<ParameterHolder>) = tree.branches.asSequence()
        .flatMap { b -> versions.map { b to it } }
        .forEach {
            configurations.getOrPut(it) { ParameterHolder(it.first, it.second) }.let(configuration::execute)
        }

    /**
     * Executes the provided [action] on each node.
     * This may miss the configuration required for a multi-branch setups
     * and may have issues accessing versioned project properties.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use `parameters {}` for global configuration.")
    @StonecutterAPI infix fun configureEach(action: Action<StonecutterBuild>) {
        builds += action
    }

    protected fun updateController(version: StonecutterProject) =
        manager.updateHeader(root.buildFile.toPath(), version.project)

    private fun constructTree(): LightTree {
        val builder: TreeBuilder = checkNotNull(root.gradle.getContainer<TreeBuilderContainer>()[root]) {
            "Project ${root.path} is not registered. This might've been caused by removing a project while its active"
        }
        val branches = builder.nodes.mapValues { (name, nodes) ->
            val branch = if (name.isEmpty()) root else root.project(name)
            val versions = nodes.associate {
                it.project to LightNode(branch.project(it.project).projectPath, it)
            }
            LightBranch(branch.projectPath, name, versions).also {
                versions.forEach { (_, v) -> v.branch = it }
            }
        }
        return LightTree(root.projectPath, ProjectHierarchy(root.path), builder.vcsProject, branches).also {
            branches.forEach { (_, b) -> b.tree = it }
        }
    }
}