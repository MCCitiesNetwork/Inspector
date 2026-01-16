package com.minecraftcitiesnetwork.inspector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class Locale {

    private static final InspectorPlugin plugin = InspectorPlugin.getPlugin();
    private static Map<String, Locale> localeMap = new HashMap<>();

    public static Locale COMMAND_USAGE = new Locale("COMMAND_USAGE");
    public static Locale COOLDOWN = new Locale("COOLDOWN");
    public static Locale INSPECTOR_ON = new Locale("INSPECTOR_ON");
    public static Locale INSPECTOR_OFF = new Locale("INSPECTOR_OFF");
    public static Locale INSPECT_DATA_HEADER = new Locale("INSPECT_DATA_HEADER");
    public static Locale INSPECT_DATA_ROW = new Locale("INSPECT_DATA_ROW");
    public static Locale INSPECT_DATA_FOOTER = new Locale("INSPECT_DATA_FOOTER");
    public static Locale NO_PERMISSION = new Locale("NO_PERMISSION");
    public static Locale NOT_INSIDE_CLAIM = new Locale("NOT_INSIDE_CLAIM");
    public static Locale LIMIT_REACH = new Locale("LIMIT_REACH");
    public static Locale NO_BLOCK_DATA = new Locale("NO_BLOCK_DATA");
    public static Locale NO_BLOCK_INTERACTIONS = new Locale("NO_BLOCK_INTERACTIONS");
    public static Locale NO_CONTAINER_TRANSACTIONS = new Locale("NO_CONTAINER_TRANSACTIONS");
    public static Locale NO_BLOCK_SELECTED = new Locale("NO_BLOCK_SELECTED");
    public static Locale REQUIRED_ROLE = new Locale("REQUIRED_ROLE");
    public static Locale SPECIFY_PAGE = new Locale("SPECIFY_PAGE");


    private Locale(String identifier){
        localeMap.put(identifier, this);
    }

    private String messageTemplate;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public Component getMessage(Object... objects){
        if(messageTemplate != null && !messageTemplate.isEmpty()) {
            String msg = messageTemplate;

            for (int i = 0; i < objects.length; i++)
                msg = msg.replace("{" + i + "}", objects[i].toString());

            return MINI_MESSAGE.deserialize(msg);
        }

        return null;
    }

    public void send(CommandSender sender, Object... objects){
        Component message = getMessage(objects);
        if(message != null && sender != null)
            sender.sendMessage(message);
    }

    private void setMessage(String message){
        this.messageTemplate = message;
    }

    public static void load(){
        InspectorPlugin.log("Loading messages started...");
        long startTime = System.currentTimeMillis();
        int messagesAmount = 0;
        File file = new File(plugin.getDataFolder(), "lang.yml");

        if(!file.exists())
            plugin.saveResource("lang.yml", false);

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(file.toPath())
                .build();
        
        ConfigurationNode cfg;
        try {
            cfg = loader.load();
        } catch (IOException error) {
            error.printStackTrace();
            return;
        }

        for(String identifier : localeMap.keySet()){
            String message = cfg.node(identifier).getString("");
            localeMap.get(identifier).setMessage(message);
            messagesAmount++;
        }

        InspectorPlugin.log(" - Found " + messagesAmount + " messages in lang.yml.");
        InspectorPlugin.log("Loading messages done (Took " + (System.currentTimeMillis() - startTime) + "ms)");
    }

}
