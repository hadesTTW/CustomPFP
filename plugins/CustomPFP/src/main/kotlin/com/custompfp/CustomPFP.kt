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

        // Register /pfp command with set and clear subcommands
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
            "Image URL (e.g. https://example.com/avatar.png)",
            null,
            true,
            true,
            ArrayList(),
            ArrayList(),
            ArrayList(),
            false
        )
        val setOption = Utils.createCommandOption(
            ApplicationCommandType.SUBCOMMAND,
            "set",
            "Set a custom profile picture for a user",
            null,
            true,
            true,
            ArrayList(),
            ArrayList(),
            Arrays.asList(userOption, urlOption),
            false
        )
        val clearOption = Utils.createCommandOption(
            ApplicationCommandType.SUBCOMMAND,
            "clear",
            "Clear custom profile picture for a user",
            null,
            true,
            true,
            ArrayList(),
            ArrayList(),
            Arrays.asList(userOption),
            false
        )

        commands.registerCommand(
            "pfp",
            "Set or clear custom profile pictures for users",
            Arrays.asList(setOption, clearOption),
            { ctx ->
                when {
                    ctx.containsArg("set") -> {
                        val args = ctx.getSubCommandArgs("set")
                        val user = args["user"] as? String
                        val url = args["url"] as? String
                        if (user.isNullOrBlank() || url.isNullOrBlank()) {
                            CommandsAPI.CommandResult("Missing arguments. Usage: /pfp set @user <url>", null, false)
                        } else {
                            settings.setString("pfp_$user", url.trim())
                            CommandsAPI.CommandResult("Custom profile picture set for user.", null, false)
                        }
                    }
                    ctx.containsArg("clear") -> {
                        val args = ctx.getSubCommandArgs("clear")
                        val user = args["user"] as? String
                        if (user.isNullOrBlank()) {
                            CommandsAPI.CommandResult("Missing user. Usage: /pfp clear @user", null, false)
                        } else {
                            settings.setString("pfp_$user", null)
                            CommandsAPI.CommandResult("Custom profile picture cleared.", null, false)
                        }
                    }
                    else -> CommandsAPI.CommandResult("Use /pfp set or /pfp clear", null, false)
                }
            }
        )
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        commands.unregisterAll()
    }

    private fun getStaticUrl(url: String): String {
        return if (url.contains(".gif")) url.replace(".gif", ".png") else url
    }
}
