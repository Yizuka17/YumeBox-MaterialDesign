@file:DependsOn("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")

import java.io.File
import java.io.IOException
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens

val DRY_RUN = args.contains("--dry-run")
val VERBOSE = args.contains("--verbose")

val rootPath = args.firstOrNull { !it.startsWith("--") } ?: "."
val root = File(rootPath).absoluteFile

if (!root.exists()) {
    println("Error: Directory not found: $rootPath")
    System.exit(1)
}

println("Removing ALL comments from: $root")

println("Options: dryRun=$DRY_RUN")

println()

var totalFiles = 0
var totalComments = 0
var totalSaved = 0L

val files =
    root.walkTopDown().filter { it.extension == "kt" }.filter { "build" !in it.path }.toList()

println("Found ${files.size} Kotlin files")

println()

for (file in files) {
    try {
        val result = processFile(file)
        if (result != null) {
            totalFiles++
            totalComments += result.commentsRemoved
            totalSaved += result.charsSaved
        }
    } catch (e: IOException) {
        println("[ERROR] ${file.path}: ${e.message}")
        if (VERBOSE) e.printStackTrace()
    }
}

println()

println("=== Summary ===")

println("Files processed: $totalFiles")

println("Comments removed: $totalComments")

println("Characters saved: $totalSaved")

if (DRY_RUN) {
    println("(DRY RUN - no files were modified)")
}

data class ProcessResult(val commentsRemoved: Int, val charsSaved: Long)

fun processFile(file: File): ProcessResult? {
    val original = file.readText()
    val lexer = KotlinLexer()
    lexer.start(original)
    val commentRanges = buildList {
        while (lexer.tokenType != null) {
            if (KtTokens.COMMENTS.contains(lexer.tokenType)) {
                add(lexer.tokenStart to lexer.tokenEnd)
            }
            lexer.advance()
        }
    }.sortedByDescending { it.first }

    if (commentRanges.isEmpty()) {
        return null
    }

    val processed = StringBuilder(original)
    commentRanges.forEach { (startOffset, endOffset) ->
        val comment = original.substring(startOffset, endOffset)
        val replacement = comment.filter { it == '\r' || it == '\n' }.ifEmpty { " " }
        processed.replace(startOffset, endOffset, replacement)
    }
    val text = processed.toString()
    val charsSaved = original.length - text.length

    if (DRY_RUN) {
        println(
            "[DRY RUN] ${file.path}: would remove ${commentRanges.size} comments ($charsSaved chars)"
        )
    } else {
        file.writeText(text)
        println(
            "[PROCESSED] ${file.path}: removed ${commentRanges.size} comments ($charsSaved chars)"
        )
    }

    return ProcessResult(commentRanges.size, charsSaved.toLong())
}
