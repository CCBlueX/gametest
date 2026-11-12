package net.ccbluex.gametest.harness.report

import net.ccbluex.gametest.harness.GameTestResults
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.filter.AbstractFilter
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo

/**
 * Keeps the report's own lines off the console appenders, which [Console] prints itself, and when [Console.quiet]
 * everything else too but what ends the run: a fatal error or a failure of the game test runner. A mixin plugin, as
 * that loads before any mod's entrypoint gets to log.
 */
class ConsoleFilter : IMixinConfigPlugin {

    override fun onLoad(mixinPackage: String) {
        val context = LogManager.getContext(false) as LoggerContext
        context.configuration.appenders.values.filterIsInstance<ConsoleAppender>().forEach { it.addFilter(Rule) }
    }

    override fun getRefMapperConfig(): String? = null

    override fun shouldApplyMixin(targetClassName: String, mixinClassName: String) = true

    override fun acceptTargets(myTargets: MutableSet<String>, otherTargets: MutableSet<String>) = Unit

    override fun getMixins(): List<String>? = null

    override fun preApply(target: String, targetClass: ClassNode, mixin: String, info: IMixinInfo) = Unit

    override fun postApply(target: String, targetClass: ClassNode, mixin: String, info: IMixinInfo) = Unit

    private object Rule : AbstractFilter(Filter.Result.NEUTRAL, Filter.Result.DENY) {

        override fun filter(event: LogEvent): Filter.Result = when {
            event.marker?.isInstanceOf(Console.REPORT) == true -> Filter.Result.DENY
            !Console.quiet -> Filter.Result.NEUTRAL
            event.level.isMoreSpecificThan(Level.FATAL) -> Filter.Result.NEUTRAL
            event.level.isMoreSpecificThan(Level.ERROR) && event.loggerName.startsWith(RUNNER) &&
                !reported(event.thrown) -> Filter.Result.NEUTRAL
            else -> Filter.Result.DENY
        }

        // The summary has listed these failures already
        private fun reported(thrown: Throwable?) =
            thrown?.stackTrace?.any { it.className == GameTestResults::class.java.name } == true

    }

}

private const val RUNNER = "fabric-client-gametest"
