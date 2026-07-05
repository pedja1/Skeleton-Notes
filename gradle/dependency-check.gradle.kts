import org.gradle.api.artifacts.ExternalModuleDependency

val allowlistFilePath = rootProject.rootDir.resolve("gradle/dependency-allowlist.txt")

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

fun collectAllDeps(): Set<String> {
    val deps = mutableSetOf<String>()
    rootProject.allprojects.forEach { project ->
        if (project.name == "buildSrc") return@forEach
        deps.addAll(collectDirectDependencies(project))
    }
    return deps
}

fun collectAllPlugins(): Set<String> {
    val aliases = readPluginAliases()
    val plugins = mutableSetOf<String>()

    rootProject.allprojects
        .filter { it.name != "buildSrc" }
        .forEach { project ->
            plugins.addAll(collectExplicitPlugins(project.buildFile, aliases))
        }

    val settingsFile = rootProject.rootDir.resolve("settings.gradle.kts")
    plugins.addAll(collectExplicitPlugins(settingsFile, aliases))

    return plugins
}

tasks.register<CheckDependencyAllowlistTask>("checkDependencyAllowlist") {
    group = "verification"
    description = "Checks that all dependencies and plugins are in the allowlist"
    allowlistFile.set(allowlistFilePath)
    declaredDependencies.set(collectAllDeps())
    declaredPlugins.set(collectAllPlugins())
}

tasks.register<GenerateDependencyAllowlistTask>("generateDependencyAllowlist") {
    group = "verification"
    description = "Generates the dependency allowlist from current project state"
    allowlistFile.set(allowlistFilePath)
    declaredDependencies.set(collectAllDeps())
    declaredPlugins.set(collectAllPlugins())
}
