package net.ccbluex.gametest.harness.connection

import net.minecraft.client.Minecraft
import net.minecraft.client.User
import net.minecraft.core.UUIDUtil
import java.util.Optional

/** Every test joins under a name of its own. Only for the client thread. */
internal class OfflineAccount private constructor(private val previous: User) {

    fun restore(minecraft: Minecraft) {
        minecraft.user = previous
    }

    companion object {

        fun switch(minecraft: Minecraft, name: String): OfflineAccount {
            val account = OfflineAccount(minecraft.user)
            minecraft.user = User(name, UUIDUtil.createOfflinePlayerUUID(name), "", Optional.empty(), Optional.empty())
            return account
        }

    }

}
