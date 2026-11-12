package net.ccbluex.gametest.harness.report

import net.ccbluex.gametest.harness.verdict.Violations

/** How one game test ended: after how many [attempts] and [nanos], with what it was let off, or its [failure]. */
class Outcome(
    val test: String,
    val attempts: Int,
    val nanos: Long,
    val violations: Violations,
    val failure: Throwable?,
)
