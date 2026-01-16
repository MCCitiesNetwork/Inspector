package com.minecraftcitiesnetwork.inspector.handlers;

import com.minecraftcitiesnetwork.inspector.InspectorPlugin;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class SettingsHandler {

    public final Set<String> requiredRoles;
    public final int historyLimitDate;
    public final int historyLimitPage;
    public final long cooldown;

    public SettingsHandler(InspectorPlugin plugin) {
        InspectorPlugin.log("Loading configuration started...");
        long startTime = System.currentTimeMillis();
        File file = new File(plugin.getDataFolder(), "config.yml");

        if (!file.exists())
            plugin.saveResource("config.yml", false);

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(file.toPath())
                .build();
        
        ConfigurationNode cfg;
        try {
            cfg = loader.load();
        } catch (IOException error) {
            error.printStackTrace();
            throw new RuntimeException("Failed to load config.yml", error);
        }

        requiredRoles = new LinkedHashSet<>();
        cfg.node("required-roles").childrenList().forEach(node ->
                requiredRoles.add(node.getString("").toLowerCase(Locale.ENGLISH)));
        
        int historyLimitDateRaw = cfg.node("history-limit", "date").getInt(-1);
        historyLimitDate = historyLimitDateRaw == -1 ? Integer.MAX_VALUE : historyLimitDateRaw;
        
        int historyLimitPageRaw = cfg.node("history-limit", "page").getInt(-1);
        historyLimitPage = historyLimitPageRaw == -1 ? Integer.MAX_VALUE : historyLimitPageRaw;
        
        cooldown = cfg.node("cooldown").getLong(5000);

        InspectorPlugin.log("Loading configuration done (Took " + (System.currentTimeMillis() - startTime) + "ms)");
    }

}
