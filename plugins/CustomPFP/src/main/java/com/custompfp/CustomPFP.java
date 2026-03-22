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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AliucordPlugin
@SuppressWarnings({"unchecked", "unused"})
public class CustomPFP extends Plugin {
    private static final String SETTINGS_PREFIX = "pfp_";
    private static final Pattern USER_ID_PATTERN = Pattern.compile("\\d{5,}");
    private static final Pattern GIF_EXTENSION_PATTERN = Pattern.compile("(?i)\\.gif(?=($|[?#]))");
    private static final Pattern GIF_FORMAT_PATTERN = Pattern.compile("(?i)([?&]format=)gif(?=(&|#|$))");

    private final Map<Long, AvatarOverride> overrides = new HashMap<>();

    @Override
    @SuppressWarnings("ConstantConditions")
    public void start(Context context) {
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
                    long userId = ((Number) callFrame.args[0]).longValue();
                    AvatarOverride override = getAvatarOverride(userId);
                    if (override != null) {
                        boolean useAnimated = (Boolean) callFrame.args[3];
                        callFrame.setResult(useAnimated ? override.animatedUrl : override.staticUrl);
                    }
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
                    String userId = extractUserId(setargs.get("user"));
                    String url = normalizeUrl(setargs.get("url"));
                    if (userId == null || url == null) {
                        return new CommandsAPI.CommandResult(
                            "Missing arguments. Usage: /pfp set @user <url>",
                            null,
                            false
                        );
                    }
                    setAvatarOverride(Long.parseLong(userId), url);
                    return new CommandsAPI.CommandResult("Custom profile picture set. You may need to navigate away and back to see the change.", null, false);
                }

                if (ctx.containsArg("clear")) {
                    var clearargs = ctx.getSubCommandArgs("clear");
                    String userId = extractUserId(clearargs.get("user"));
                    if (userId == null) {
                        return new CommandsAPI.CommandResult(
                            "Missing user. Usage: /pfp clear @user",
                            null,
                            false
                        );
                    }
                    clearAvatarOverride(Long.parseLong(userId));
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
        overrides.clear();
    }

    private AvatarOverride getAvatarOverride(long userId) {
        AvatarOverride cached = overrides.get(userId);
        if (cached != null) {
            return cached;
        }

        AvatarOverride override = AvatarOverride.fromUrl(settings.getString(getSettingsKey(userId), null));
        if (override != null) {
            overrides.put(userId, override);
        }
        return override;
    }

    private void setAvatarOverride(long userId, String animatedUrl) {
        AvatarOverride override = AvatarOverride.fromUrl(animatedUrl);
        if (override == null) {
            clearAvatarOverride(userId);
            return;
        }

        overrides.put(userId, override);
        settings.setString(getSettingsKey(userId), override.animatedUrl);
    }

    private void clearAvatarOverride(long userId) {
        overrides.remove(userId);
        settings.setString(getSettingsKey(userId), null);
    }

    private static String getSettingsKey(long userId) {
        return SETTINGS_PREFIX + userId;
    }

    private static String extractUserId(Object userArg) {
        if (userArg == null) {
            return null;
        }

        if (userArg instanceof Number) {
            return String.valueOf(((Number) userArg).longValue());
        }

        String rawValue = userArg.toString().trim();
        if (rawValue.isEmpty()) {
            return null;
        }

        Matcher matcher = USER_ID_PATTERN.matcher(rawValue);
        if (matcher.find()) {
            return matcher.group();
        }

        try {
            Object id = userArg.getClass().getMethod("getId").invoke(userArg);
            if (id instanceof Number) {
                return String.valueOf(((Number) id).longValue());
            }
            if (id != null) {
                return extractUserId(id);
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

    private static String normalizeUrl(Object urlArg) {
        if (urlArg == null) {
            return null;
        }

        String url = urlArg.toString().trim();
        return url.isEmpty() ? null : url;
    }

    private static String getStaticUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        String staticUrl = GIF_EXTENSION_PATTERN.matcher(url).replaceFirst(".png");
        return GIF_FORMAT_PATTERN.matcher(staticUrl).replaceFirst("$1png");
    }

    private static final class AvatarOverride {
        private final String animatedUrl;
        private final String staticUrl;

        private AvatarOverride(String animatedUrl, String staticUrl) {
            this.animatedUrl = animatedUrl;
            this.staticUrl = staticUrl;
        }

        private static AvatarOverride fromUrl(String animatedUrl) {
            String normalizedUrl = normalizeUrl(animatedUrl);
            if (normalizedUrl == null) {
                return null;
            }

            return new AvatarOverride(normalizedUrl, getStaticUrl(normalizedUrl));
        }
    }
}
