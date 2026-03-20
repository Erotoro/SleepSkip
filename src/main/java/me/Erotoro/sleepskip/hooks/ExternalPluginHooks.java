package me.Erotoro.sleepskip.hooks;

import me.Erotoro.sleepskip.SleepSkip;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional integrations for vanish/AFK providers and conflict reporting.
 */
public class ExternalPluginHooks {

    private static final Set<String> CONFLICTING_SLEEP_PLUGINS = Set.of(
            "BetterSleeping4",
            "BetterSleeping",
            "Harbor",
            "SleepMost",
            "NightSkipper"
    );

    private final SleepSkip plugin;
    private final Plugin essentialsPlugin;
    private final Plugin cmiPlugin;
    private final Set<String> reflectionWarnings = ConcurrentHashMap.newKeySet();

    public ExternalPluginHooks(SleepSkip plugin) {
        this.plugin = plugin;
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        this.essentialsPlugin = pluginManager.getPlugin("Essentials");
        this.cmiPlugin = pluginManager.getPlugin("CMI");
    }

    public void logDetectedHooks() {
        if (essentialsPlugin != null && essentialsPlugin.isEnabled()) {
            plugin.getLogger().info(plugin.tr(
                    "logs.hook-essentials",
                    "Hooked into Essentials for vanish/AFK detection."
            ));
        }

        if (cmiPlugin != null && cmiPlugin.isEnabled()) {
            plugin.getLogger().info(plugin.tr(
                    "logs.hook-cmi",
                    "Hooked into CMI for vanish/AFK detection."
            ));
        }
    }

    public void logConflicts() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        for (String pluginName : CONFLICTING_SLEEP_PLUGINS) {
            Plugin detected = pluginManager.getPlugin(pluginName);
            if (detected != null && detected.isEnabled()) {
                plugin.getLogger().warning(plugin.tr(
                        "logs.sleep-plugin-conflict",
                        "Detected another sleep-related plugin: {plugin}. Behavior conflicts are possible."
                ).replace("{plugin}", pluginName));
            }
        }
    }

    public boolean isVanished(Player player) {
        return hasTruthyMetadata(player, "vanished")
                || invokeUserBoolean(essentialsPlugin, "getUser", player, "isVanished")
                || isCmiBoolean(player, "isVanished");
    }

    public boolean isAfk(Player player) {
        return invokeUserBoolean(essentialsPlugin, "getUser", player, "isAfk")
                || isCmiBoolean(player, "isAfk");
    }

    private boolean invokeUserBoolean(Plugin sourcePlugin, String userMethod, Player player, String booleanMethod) {
        if (sourcePlugin == null || !sourcePlugin.isEnabled()) {
            return false;
        }

        Object user = invokeExact(sourcePlugin, userMethod, new Class<?>[]{Player.class}, player);
        return user != null && Boolean.TRUE.equals(invoke(user, booleanMethod));
    }

    private boolean isCmiBoolean(Player player, String methodName) {
        if (cmiPlugin == null || !cmiPlugin.isEnabled()) {
            return false;
        }

        Object cmiInstance = invokeStatic("com.Zrips.CMI.CMI", "getInstance");
        Object playerManager = cmiInstance == null ? null : invoke(cmiInstance, "getPlayerManager");
        Object user = playerManager == null ? null : invokeExact(playerManager, "getUser", new Class<?>[]{Player.class}, player);
        return user != null && Boolean.TRUE.equals(invoke(user, methodName));
    }

    private boolean hasTruthyMetadata(Player player, String key) {
        List<MetadataValue> metadata = player.getMetadata(key);
        for (MetadataValue value : metadata) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private Object invoke(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) {
            warnOnce(target.getClass().getName() + "#" + methodName,
                    "Failed to invoke hook method " + target.getClass().getName() + "#" + methodName + ": " + exception.getClass().getSimpleName());
            return null;
        }
    }

    private Object invokeExact(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) {
            warnOnce(target.getClass().getName() + "#" + methodName,
                    "Failed to invoke hook method " + target.getClass().getName() + "#" + methodName + ": " + exception.getClass().getSimpleName());
            return null;
        }
    }

    private Object invokeStatic(String className, String methodName) {
        try {
            Class<?> clazz = Class.forName(className);
            Method method = clazz.getMethod(methodName);
            return method.invoke(null);
        } catch (ReflectiveOperationException exception) {
            warnOnce(className + "#" + methodName,
                    "Failed to invoke hook method " + className + "#" + methodName + ": " + exception.getClass().getSimpleName());
            return null;
        }
    }

    private void warnOnce(String key, String message) {
        if (reflectionWarnings.add(key)) {
            plugin.getLogger().warning(message);
        }
    }
}
