plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// UI text belongs in string resources (values/ and values-zh/). Fail on string literals
// that look like user-facing text in Kotlin sources.
tasks.register("checkUiText") {
    group = "verification"
    description = "Fails when Kotlin sources contain hard-coded UI text."
    val sources = fileTree(rootDir) {
        include("**/src/**/*.kt")
        exclude("**/build/**")
        exclude("Temp/**", "temp/**")
        exclude("**/Temp/**")
    }
    inputs.files(sources)
    doLast {
        val han = Regex("\"[^\"\\n]*\\p{IsHan}[^\"\\n]*\"")
        val uiLiteral = Regex("""(\bText\(\s*|\btext\s*=\s*|\bcontentDescription\s*=\s*)"[^"]""")
        // A literal that is data rather than a label — a folder name on disk, a value sent to a plugin — says so.
        val dataLiteral = Regex("""\bui-text:\s*data\b""")
        val problems = mutableListOf<String>()
        sources.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (line.trimStart().startsWith("//") || line.trimStart()
                        .startsWith("*")
                ) return@forEachIndexed
                if (dataLiteral.containsMatchIn(line)) return@forEachIndexed
                if (han.containsMatchIn(line) || uiLiteral.containsMatchIn(line)) {
                    problems += "${file.relativeTo(rootDir)}:${index + 1}: ${line.trim()}"
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Hard-coded UI text found, move it to string resources:\n" + problems.joinToString(
                    "\n"
                )
            )
        }
    }
}
