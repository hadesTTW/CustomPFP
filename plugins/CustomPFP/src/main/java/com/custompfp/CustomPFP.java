package com.custompfp;

import android.content.Context;
import com.aliucord.Utils;
import com.aliucord.annotations.AliucordPlugin;
import com.aliucord.api.CommandsAPI;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.Hook;
import com.discord.api.commands.ApplicationCommandType;
import com.discord.utilities.icon.IconUtils;
import java.util.ArrayList;
import java.util.Arrays;

@AliucordPlugin
@SuppressWarnings({"unchecked", "unused"})
public class CustomPFP extends Plugin {

    @Override
    @SuppressWarnings("ConstantConditions")
    public void start(Context context) {
        // Patch IconUtils.getForUser to return custom PFP URLs when set
        try {
            patcher.patch(
                IconUtils.class.getDeclaredMethod(
                    "getForUser",
                    Long.class,
                    String.class,
                    Integer.class,
                    Boolean.TYPE,
                    Integer.class
                ),
                new Hook(callFrame -> {
                    long userId = (Long) callFrame.args[0];
                    String customUrl = settings.getString("pfp_" + userId, null);
                    if (customUrl == null) return;
                    boolean useAnimated = (Boolean) callFrame.args[3];
                    String url = useAnimated ? customUrl : getStaticUrl(customUrl);
                    callFrame.setResult(url);
                })
            );
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        // Same structure as Friend Nicknames: subcommands set/clear with user option
        var userOption = Utils.createCommandOption(
            ApplicationCommandType.USER,
            "user",
            "User to set/clear custom profile picture for",
            null,
            true,
            true,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            false
        );
        var urlOption = Utils.createCommandOption(
            ApplicationCommandType.STRING,
            "url",
            "Image URL (png, jpg, jpeg, gif, webp)",
            null,
            true,
            true,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            false
        );
        var setOption = Utils.createCommandOption(
            ApplicationCommandType.SUBCOMMAND,
            "set",
            "Set a custom profile picture",
            null,
            true,
            true,
            new ArrayList<>(),
            new ArrayList<>(),
            Arrays.asList(userOption, urlOption),
            false
        );
        var clearOption = Utils.createCommandOption(
            ApplicationCommandType.SUBCOMMAND,
            "clear",
            "Clear custom profile picture",
            null,
            true,
            true,
            new ArrayList<>(),
            new ArrayList<>(),
            Arrays.asList(userOption),
            false
        );

        commands.registerCommand(
            "pfp",
            "Set or clear custom profile pictures for users",
            Arrays.asList(setOption, clearOption),
            ctx -> {
                if (ctx.containsArg("set")) {
                    var setargs = ctx.getSubCommandArgs("set");
                    var user = (String) setargs.get("user");
                    var url = (String) setargs.get("url");
                    if (user == null || user.equals("") || url == null || url.equals("")) {
                        return new CommandsAPI.CommandResult(
                            "Missing arguments. Usage: /pfp set @user <url>",
                            null,
                            false
                        );
                    }
                    settings.setString(user, url.trim());
                    return new CommandsAPI.CommandResult("Custom profile picture set.", null, false);
                }

                if (ctx.containsArg("clear")) {
                    var clearargs = ctx.getSubCommandArgs("clear");
                    var user = (String) clearargs.get("user");
                    if (user == null || user.equals("")) {
                        return new CommandsAPI.CommandResult(
                            "Missing user. Usage: /pfp clear @user",
                            null,
                            false
                        );
                    }
                    settings.setString(user, null);
                    return new CommandsAPI.CommandResult("Custom profile picture cleared.", null, false);
                }

                return new CommandsAPI.CommandResult();
            }
        );
    }

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
        commands.unregisterAll();
    }

    private static String getStaticUrl(String url) {
        return (url != null && url.contains(".gif")) ? url.replace(".gif", ".png") : url;
    }
}
