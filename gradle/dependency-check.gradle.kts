import org.gradle.api.artifacts.ExternalModuleDependency

val allowlistFile = rootProject.rootDir.resolve("gradle/dependency-allowlist.txt")

fun readAllowlist(): Pair<Set<String>, Set<String>> {
    val deps = mutableSetOf<String>()
    val plugins = mutableSetOf<String>()
    if (allowlistFile.exists()) {
        allowlistFile.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
            when {
                trimmed.startsWith("dep:") -> deps.add(trimmed.removePrefix("dep:"))
                trimmed.startsWith("plugin:") -> plugins.add(trimmed.removePrefix("plugin:"))
            }
        }
    }
    return deps to plugins
}

fun readPluginAliases(): Map<String, String> {
    val catalogFile = rootProject.rootDir.resolve("gradle/libs.versions.toml")
    if (!catalogFile.exists()) return emptyMap()
    val aliases = mutableMapOf<String, String>()
    var inPlugins = false
    catalogFile.forEachLine { line ->
        val trimmed = line.trim()
        if (trimmed == "[plugins]") { inPlugins = true; return@forEachLine }
        if (inPlugins && trimmed.startsWith("[")) { inPlugins = false; return@forEachLine }
        if (inPlugins && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
            val match = Regex("""^(\S+)\s*=\s*\{[^}]*id\s*=\s*"([^"]+)""").find(trimmed)
            if (match != null) {
                aliases[match.groupValues[1]] = match.groupValues[2]
            }
        }
    }
    return aliases
}

fun resolvePluginId(declaration: String, aliases: Map<String, String>): String? {
    val aliasRegex = Regex("""alias\s*\(\s*libs\.plugins\.([a-zA-Z0-9_.]+)\s*\)""")
    val aliasMatch = aliasRegex.find(declaration)
    if (aliasMatch != null) {
        val aliasName = aliasMatch.groupValues[1]
        aliases[aliasName.replace('.', '-')]?.let { return it }
        aliases[aliasName.replace('.', '_')]?.let { return it }
        return null
    }

    val kotlinRegex = Regex("""kotlin\s*\(\s*"([^"]+)"\s*\)""")
    val kotlinMatch = kotlinRegex.find(declaration)
    if (kotlinMatch != null) {
        return "org.jetbrains.kotlin.${kotlinMatch.groupValues[1]}"
    }

    val idRegex = Regex("""id\s*\(\s*"([^"]+)"\s*\)""")
    val idMatch = idRegex.find(declaration)
    if (idMatch != null) {
        return idMatch.groupValues[1]
    }

    return null
}

fun collectExplicitPlugins(buildFile: java.io.File, aliases: Map<String, String>): Set<String> {
    if (!buildFile.exists()) return emptySet()
    val content = buildFile.readText()
    val plugins = mutableSetOf<String>()

    val pluginBlockRegex = Regex("""plugins\s*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)
    val match = pluginBlockRegex.find(content)
    if (match != null) {
        val block = match.groupValues[1]
        block.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) return@forEach
            val declaration = trimmed.removeSuffix(" apply false").trim()
            resolvePluginId(declaration, aliases)?.let { plugins.add(it) }
        }
    }

    return plugins
}

fun collectDirectDependencies(project: Project): Set<String> {
    val userConfigSuffixes = setOf(
        "api", "implementation", "compileonly", "runtimeonly", "annotationprocessor",
        "kapt", "ksp", "compileonlyapi"
    )
    return project.configurations
        .filter { config ->
            val name = config.name.lowercase()
            val isUserConfig = userConfigSuffixes.any { name.endsWith(it) }
            val isTest = name.contains("test") || name.contains("androidtest")
            isUserConfig && !isTest && config.dependencies.isNotEmpty()
        }
        .flatMap { config ->
            config.dependencies
                .filterIsInstance<ExternalModuleDependency>()
                .map { "${it.group}:${it.name}" }
        }
        .toSet()
}

tasks.register("checkDependencyAllowlist") {
    group = "verification"
    description = "Checks that all dependencies and plugins are in the allowlist"

    notCompatibleWithConfigurationCache("Accesses project model at execution time")

    doLast {
        val (allowedDeps, allowedPlugins) = readAllowlist()
        val aliases = readPluginAliases()

        val violations = mutableListOf<String>()

        rootProject.allprojects.forEach { project ->
            if (project.name == "buildSrc") return@forEach

            val deps = collectDirectDependencies(project)
            deps.forEach { dep ->
                if (dep !in allowedDeps) {
                    violations.add("  ${project.path}: dependency '$dep' is NOT in allowlist")
                }
            }
        }

        val allBuildFiles = rootProject.allprojects
            .filter { it.name != "buildSrc" }
            .map { it.buildFile }
        val settingsFile = rootProject.rootDir.resolve("settings.gradle.kts")

        val foundPlugins = mutableSetOf<String>()
        allBuildFiles.forEach { file ->
            collectExplicitPlugins(file, aliases).forEach { foundPlugins.add(it) }
        }
        collectExplicitPlugins(settingsFile, aliases).forEach { foundPlugins.add(it) }

        foundPlugins.forEach { plugin ->
            if (plugin !in allowedPlugins) {
                violations.add("  plugin '$plugin' is NOT in allowlist")
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Dependency/plugin allowlist violations found:\n" +
                violations.joinToString("\n") +
                "\n\nRun './gradlew generateDependencyAllowlist' to update the allowlist."
            )
        }

        println("Dependency allowlist check passed. ${allowedDeps.size} dependencies, ${allowedPlugins.size} plugins verified.")
    }
}

tasks.register("generateDependencyAllowlist") {
    group = "verification"
    description = "Generates the dependency allowlist from current project state"

    notCompatibleWithConfigurationCache("Accesses project model at execution time")

    doLast {
        val aliases = readPluginAliases()

        val allDeps = mutableSetOf<String>()
        rootProject.allprojects.forEach { project ->
            if (project.name == "buildSrc") return@forEach
            allDeps.addAll(collectDirectDependencies(project))
        }

        val allPlugins = mutableSetOf<String>()
        rootProject.allprojects
            .filter { it.name != "buildSrc" }
            .forEach { project ->
                allPlugins.addAll(collectExplicitPlugins(project.buildFile, aliases))
            }

        val settingsFile = rootProject.rootDir.resolve("settings.gradle.kts")
        allPlugins.addAll(collectExplicitPlugins(settingsFile, aliases))

        val content = buildString {
            appendLine("# Dependency and plugin allowlist")
            appendLine("# Format: dep:groupId:artifactId or plugin:plugin.id")
            appendLine("# Auto-generated by generateDependencyAllowlist task")
            appendLine()
            appendLine("# Dependencies")
            allDeps.sorted().forEach { appendLine("dep:$it") }
            appendLine()
            appendLine("# Plugins")
            allPlugins.sorted().forEach { appendLine("plugin:$it") }
        }

        allowlistFile.writeText(content)
        println("Allowlist generated at ${allowlistFile.relativeTo(rootProject.rootDir)}")
        println("  Dependencies: ${allDeps.size}")
        println("  Plugins: ${allPlugins.size}")
    }
}
