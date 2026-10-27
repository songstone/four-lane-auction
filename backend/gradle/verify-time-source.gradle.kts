// Search production sources only; test clocks and dependency-owned SQL are outside this rule.
// This is intentionally a source search, not a Java/SQL symbol resolver (ADR-002).
data class JavaTimeSourceText(val code: String, val literals: String)

fun javaTimeSourceText(source: String): JavaTimeSourceText {
    val code = source.toCharArray()
    val literals = CharArray(source.length) { if (source[it] == '\n') '\n' else ' ' }
    fun mask(start: Int, end: Int) {
        for (index in start until end) {
            if (code[index] != '\n' && code[index] != '\r') code[index] = ' '
        }
    }
    var offset = 0
    while (offset < source.length) {
        when {
            source.startsWith("//", offset) -> {
                val end = source.indexOf('\n', offset).let { if (it < 0) source.length else it }
                mask(offset, end)
                offset = end
            }
            source.startsWith("/*", offset) -> {
                val end = source.indexOf("*/", offset + 2).let { if (it < 0) source.length else it + 2 }
                mask(offset, end)
                offset = end
            }
            source[offset] == '"' || source[offset] == '\'' -> {
                val start = offset
                val quote = source[offset]
                val textBlock = source.startsWith("\"\"\"", offset)
                offset += if (textBlock) 3 else 1
                while (offset < source.length) {
                    if (source[offset] == '\\') {
                        offset = (offset + 2).coerceAtMost(source.length)
                    } else if (textBlock && source.startsWith("\"\"\"", offset)) {
                        offset += 3
                        break
                    } else if (!textBlock && source[offset] == quote) {
                        offset++
                        break
                    } else {
                        offset++
                    }
                }
                if (quote == '"') {
                    for (index in start until offset) literals[index] = source[index]
                }
                mask(start, offset)
            }
            else -> offset++
        }
    }
    return JavaTimeSourceText(String(code), String(literals))
}

fun withoutSqlComments(source: String): String {
    // Keep offsets/newlines so diagnostics still point to the source file.
    return Regex("/\\*.*?\\*/|<!--.*?-->|(?m:--[ \\t][^\\r\\n]*|--$|#[^\\r\\n]*)", RegexOption.DOT_MATCHES_ALL)
        .replace(source) { match -> match.value.map { if (it == '\n') '\n' else ' ' }.joinToString("") }
}

val productionTimeSources = files(subprojects.map { module ->
    fileTree(module.file("src/main")) {
        include("**/*.java", "**/*.sql", "**/*.xml", "**/*.yml", "**/*.yaml", "**/*.properties")
    }
})

tasks.register("verifyTimeSources") {
    group = "verification"
    description = "Rejects wall-clock calls outside the injected Clock and DB-owned current timestamps."
    inputs.files(productionTimeSources).withPropertyName("productionTimeSources")

    doLast {
        val violations = mutableListOf<String>()
        val javaRules = listOf(
            "Instant.now()/LocalDateTime.now()" to
                Regex("\\b(?:java\\s*\\.\\s*time\\s*\\.\\s*)?(?:Instant|LocalDateTime)\\s*\\.\\s*now\\s*\\(\\s*\\)"),
            "System.currentTimeMillis()" to
                Regex("\\b(?:java\\s*\\.\\s*lang\\s*\\.\\s*)?System\\s*\\.\\s*currentTimeMillis\\s*\\(\\s*\\)"),
            "new Date()" to
                Regex("\\bnew\\s+(?:java\\s*\\.\\s*util\\s*\\.\\s*)?Date\\s*\\(\\s*\\)")
        )
        val sqlRule = Regex(
            "(?<![a-z0-9_])(?:NOW\\s*\\(\\s*[0-6]?\\s*\\)|CURRENT_TIMESTAMP(?:\\s*\\(\\s*[0-6]?\\s*\\))?)(?![a-z0-9_])",
            RegexOption.IGNORE_CASE
        )
        fun check(file: File, text: String, rule: String, pattern: Regex, onlyUnqualified: Boolean = false) {
            pattern.findAll(text).forEach { match ->
                if (onlyUnqualified && text.take(match.range.first).trimEnd().endsWith(".")) return@forEach
                val line = text.take(match.range.first).count { it == '\n' } + 1
                violations += "${rootProject.relativePath(file)}:$line: $rule"
            }
        }
        val sources = productionTimeSources.files.sortedBy { it.path }
        sources.forEach { file ->
            val source = file.readText(Charsets.UTF_8)
            if (file.extension == "java") {
                val parts = javaTimeSourceText(source)
                javaRules.forEach { (rule, pattern) -> check(file, parts.code, rule, pattern) }
                if (Regex("import\\s+static\\s+java\\s*\\.\\s*time\\s*\\.\\s*(?:Instant|LocalDateTime)\\s*\\.\\s*(?:now|\\*)\\s*;")
                        .containsMatchIn(parts.code)) {
                    check(file, parts.code, "statically imported now()", Regex("(?<![\\w.])now\\s*\\(\\s*\\)"), true)
                }
                if (Regex("import\\s+static\\s+java\\s*\\.\\s*lang\\s*\\.\\s*System\\s*\\.\\s*(?:currentTimeMillis|\\*)\\s*;")
                        .containsMatchIn(parts.code)) {
                    check(file, parts.code, "statically imported currentTimeMillis()", Regex("(?<![\\w.])currentTimeMillis\\s*\\(\\s*\\)"), true)
                }
                // SQL/JPQL in annotations, ordinary strings and Java text blocks.
                check(file, parts.literals, "SQL NOW()/CURRENT_TIMESTAMP", sqlRule)
            } else {
                check(file, withoutSqlComments(source), "SQL NOW()/CURRENT_TIMESTAMP", sqlRule)
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Time source violations (${violations.size}):\n${violations.joinToString("\n")}")
        }
        logger.lifecycle("Time source check: ${sources.size} production files, 0 violations")
    }
}
