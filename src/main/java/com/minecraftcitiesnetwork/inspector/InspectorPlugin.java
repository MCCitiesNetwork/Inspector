package com.minecraftcitiesnetwork.inspector;

import com.minecraftcitiesnetwork.inspector.command.InspectCommand;
import com.minecraftcitiesnetwork.inspector.coreprotect.CoreProtect;
import com.minecraftcitiesnetwork.inspector.handlers.HooksHandler;
import com.minecraftcitiesnetwork.inspector.handlers.SettingsHandler;
import com.minecraftcitiesnetwork.inspector.listeners.BlockListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class InspectorPlugin extends JavaPlugin {

    private static InspectorPlugin plugin;

    private SettingsHandler settingsHandler;
    private HooksHandler hooksHandler;

    private CoreProtect coreProtect;

    @Override
    public void onLoad() {
        plugin = this;
    }

    @Override
    public void onEnable() {
        log("******** ENABLE START ********");

        InspectCommand inspectCommand = new InspectCommand(this);
        getCommand("inspect").setExecutor(inspectCommand);
        getCommand("inspect").setTabCompleter(inspectCommand);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);

        settingsHandler = new SettingsHandler(this);
        hooksHandler = new HooksHandler(this);

        Locale.load();

        log("******** ENABLE DONE ********");

        Bukkit.getScheduler().runTask(this, this::loadCoreProtect);
    }

    private void loadCoreProtect() {
        try {
            coreProtect = new CoreProtect(this);
        } catch (Exception error) {
            error.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    public SettingsHandler getSettings() {
        return settingsHandler;
    }

    public void setSettings(SettingsHandler settingsHandler) {
        this.settingsHandler = settingsHandler;
    }

    public HooksHandler getHooksHandler() {
        return hooksHandler;
    }

    public CoreProtect getCoreProtect() {
        return coreProtect;
    }

    public static void log(String message) {
        plugin.getLogger().info(message);
    }

    public static InspectorPlugin getPlugin() {
        return plugin;
    }

}
