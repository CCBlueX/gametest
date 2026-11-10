package net.ccbluex.gametest.harness.action

import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.mixin.ChatComponentAccessor
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos

/**
 * Turns towards [pos] and lets the new rotation reach the server before anything is done with it. A human
 * cannot turn and click within one tick, and anticheats know that.
 */
fun GameTestScope.aim(pos: BlockPos) {
    input.lookAt(pos)
    ticks(2)
}

/** Chat lines without formatting, newest first. Only readable on the client thread. */
val Minecraft.chat: List<String>
    get() = (gui.hud.chat as ChatComponentAccessor).allMessages.map {
        ChatFormatting.stripFormatting(it.content().string).orEmpty()
    }

fun Minecraft.chatContains(text: String) = chat.any { text in it }

fun GameTestScope.chat() = client { it.chat }

fun GameTestScope.screenshot(name: String) {
    // Vanilla's notes on unsigned chat and social interactions would cover the corner
    client { it.gui.toastManager().clear() }
    context.takeScreenshot(name)
}
