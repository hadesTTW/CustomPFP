package com.custompfp

import android.content.Context
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.discord.api.commands.ApplicationCommandType
import com.discord.utilities.icon.IconUtils
import java.util.Arrays
import java.util.ArrayList

@AliucordPlugin
class CustomPFP : Plugin() {

    override fun start(context: Context) {
        // Patch IconUtils.getForUser to return custom PFP URLs when set
        patcher.patch(
            IconUtils::class.java.getDeclaredMethod(
                "getForUser",
                java.lang.Long::class.java,
                String::class.java,
                Integer::class.java,
                Boolean::class.javaPrimitiveType,
                Integer::class.java
            ),
            Hook {
                val userId = it.args[0] as Long
                val customUrl = settings.getString("pfp_$userId", null) ?: return@Hook
                val useAnimated = it.args[3] as Boolean
                val url = if (useAnimated) customUrl else getStaticUrl(customUrl)
                it.result = url
            }
        )

        // Register /pfp command - url optional: with url = set, without = clear
        val userOption = Utils.createCommandOption(
            ApplicationCommandType.USER,
            "user",
            "User to set/clear custom profile picture for",
            null,
            true,
            true,
            ArrayList(),
            ArrayList(),
            ArrayList(),
            false
        )
        val urlOption = Utils.createCommandOption(
            ApplicationCommandType.STRING,
            "url",
            "Image URL (png, jpg, jpeg, gif, webp). Omit to clear.",
            null,
            false,
            true,
            ArrayList(),
            ArrayList(),
            ArrayList(),
            false
        )

        commands.registerCommand(
            "pfp",
            "Set or clear custom profile pictures for users",
            Arrays.asList(userOption, urlOption),
            { ctx ->
                val user = ctx.getRequiredUser("user")
                val urlRaw = ctx.getString("url")
                val url = urlRaw?.trim()?.takeIf { it.isNotBlank() }

                if (url != null) {
                    settings.setString("pfp_${user.id}", url)
                    CommandsAPI.CommandResult("Custom profile picture set.", null, false)
                } else {
                    settings.setString("pfp_${user.id}", null)
                    CommandsAPI.CommandResult("Custom profile picture cleared.", null, false)
                }
            }
        )
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        commands.unregisterAll()
    }

    private fun getStaticUrl(url: String): String {
        // For animated gifs, use static fallback in non-animated contexts
        return when {
            url.contains(".gif") -> url.replace(".gif", ".png")
            else -> url // png, jpg, jpeg, webp - use as-is
        }
    }
}
