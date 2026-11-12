package net.ccbluex.gametest.harness.report

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.MarkerManager
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * What a run prints about its game tests: a line per test, coloured, with the details that matter below it. On
 * GitHub Actions the details fold into groups, failures become error annotations and the job summary gets a table.
 * The log file gets the same lines without colour.
 */
internal object Console {

    private val github = System.getenv("GITHUB_ACTIONS") == "true"

    /** Whether the console shows only the game tests; the default on CI, `-Dgametest.console=all|tests` decides. */
    val quiet = System.getProperty("gametest.console")?.let { it == "tests" } ?: github

    private val colours = System.getenv("NO_COLOR") == null &&
        (github || System.getenv("FORCE_COLOR") != null || System.getenv("TERM").let { it != null && it != "dumb" })

    /** Lines of the report, which the console appenders leave to [out]. */
    val REPORT: Marker = MarkerManager.getMarker("GAMETEST_REPORT")

    // Minecraft hands System.out to its logger
    private val out = PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8)
    private val logger = LogManager.getLogger("GameTest")

    private const val NAME_WIDTH = 30
    private const val STACK_LINES = 40

    fun header(text: String) = print(bold(text))

    fun note(text: String) = print(dim(text))

    fun outcome(outcome: Outcome) {
        val failure = outcome.failure
        val facts = buildList {
            add(dim(duration(outcome.nanos)))
            if (outcome.attempts > 1) {
                add(yellow("attempt ${outcome.attempts}"))
            }
            if (outcome.violations.level > 0) {
                add(yellow("VL ${outcome.violations.level}"))
            }
        }.joinToString(dim(" · "))
        if (failure == null) {
            print("${green("✓")} ${outcome.test.padEnd(NAME_WIDTH)} $facts")
            if (outcome.violations.lines.isNotEmpty()) {
                group("${outcome.test}: tolerated", outcome.violations.lines)
            }
            return
        }
        print("${red("✗")} ${red(outcome.test.padEnd(NAME_WIDTH))} $facts")
        val message = failure.message ?: failure.toString()
        message.lines().forEach { print("    ${red(it)}") }
        annotate(outcome.test, message)
        group("${outcome.test}: stack trace", stack(failure))
    }

    fun retrying(test: String, attempt: Int, failure: Throwable) {
        val reason = (failure.message ?: failure.toString()).lines().first()
        print("${yellow("↻")} ${test.padEnd(NAME_WIDTH)} ${yellow("attempt $attempt failed:")} $reason")
        group("$test: attempt $attempt", stack(failure))
    }

    fun summary(outcomes: List<Outcome>) {
        val failed = outcomes.filter { it.failure != null }
        val retried = outcomes.count { it.failure == null && it.attempts > 1 }
        val total = duration(outcomes.sumOf { it.nanos })
        val passed = outcomes.size - failed.size
        print(
            bold("${green("$passed passed")}, ${if (failed.isEmpty()) "0 failed" else red("${failed.size} failed")}") +
                dim(" · $retried after a retry · $total")
        )
        failed.forEach { print("  ${red("✗ ${it.test}")}") }
        writeJobSummary(outcomes, passed, failed.size)
    }

    private fun writeJobSummary(outcomes: List<Outcome>, passed: Int, failed: Int) {
        val file = System.getenv("GITHUB_STEP_SUMMARY")?.takeIf(String::isNotBlank) ?: return
        val text = buildString {
            appendLine("### Game tests: $passed passed, $failed failed")
            appendLine()
            appendLine("| | Test | Time | Attempts | VL |")
            appendLine("| --- | --- | ---: | ---: | ---: |")
            outcomes.sortedWith(compareBy({ it.failure == null }, { it.test })).forEach {
                val mark = if (it.failure == null) "✅" else "❌"
                appendLine("| $mark | ${it.test} | ${duration(it.nanos)} | ${it.attempts} | ${it.violations.level} |")
            }
            outcomes.filter { it.failure != null }.forEach {
                appendLine()
                appendLine("<details><summary>${it.test}</summary>")
                appendLine()
                appendLine("```")
                appendLine(it.failure?.message)
                appendLine("```")
                appendLine("</details>")
            }
            appendLine()
        }
        Files.writeString(Path.of(file), text, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    private fun stack(failure: Throwable) = failure.stackTraceToString().lines().take(STACK_LINES)

    private fun group(title: String, lines: List<String>) {
        if (github) {
            raw("::group::$title")
            lines.forEach { print(it) }
            raw("::endgroup::")
        } else {
            lines.forEach { print("    ${dim(it)}") }
        }
    }

    private fun annotate(title: String, message: String) {
        if (github) {
            raw("::error title=${escapeProperty(title)}::${escapeData(message)}")
        }
    }

    private fun print(line: String) {
        out.println(line)
        logger.info(REPORT, plain(line))
    }

    // Workflow commands only work at the start of a line and stay out of the log file
    private fun raw(line: String) = out.println(line)

    private fun duration(nanos: Long): String {
        val millis = TimeUnit.NANOSECONDS.toMillis(nanos)
        val seconds = millis / 1000
        return if (seconds < 60) {
            "%.1fs".format(Locale.ROOT, millis / 1000.0)
        } else {
            "%dm %02ds".format(seconds / 60, seconds % 60)
        }
    }

    private fun escapeData(text: String) = text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")

    private fun escapeProperty(text: String) = escapeData(text).replace(":", "%3A").replace(",", "%2C")

    private fun colour(code: String, text: String) = if (colours) "\u001B[${code}m$text\u001B[0m" else text

    private fun bold(text: String) = colour("1", text)

    private fun dim(text: String) = colour("2", text)

    private fun green(text: String) = colour("32", text)

    private fun yellow(text: String) = colour("33", text)

    private fun red(text: String) = colour("31", text)

    private fun plain(text: String) = text.replace(Regex("\u001B\\[[0-9;]*m"), "")

}
