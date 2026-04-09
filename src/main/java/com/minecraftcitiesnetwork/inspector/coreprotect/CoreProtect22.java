package com.minecraftcitiesnetwork.inspector.coreprotect;

import com.minecraftcitiesnetwork.inspector.InspectorPlugin;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class CoreProtect22 implements CoreProtectProvider {

    private static final int RESULTS_PER_PAGE = 7;
    private static final int MAX_LOOKUP_TIME_SECONDS = 31536000; // 1 year
    private static final int SECONDS_PER_DAY = 86400;
    private static final int ACTION_BREAK = 0;
    private static final int ACTION_PLACE = 1;
    private static final int ACTION_INTERACT = 2;
    private static final ThreadLocal<DecimalFormat> TIME_FORMAT =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.00"));
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("(?i)[\u00A7&][0-9A-FK-ORX]");
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final GsonComponentSerializer GSON_SERIALIZER = GsonComponentSerializer.gson();

    private final CoreProtectAPI api;
    private final Map<Integer, String> materialNameCache = new HashMap<>();

    public CoreProtect22() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
        CoreProtectAPI coreProtectAPI = null;
        
        if (plugin != null && plugin instanceof CoreProtect) {
            coreProtectAPI = ((CoreProtect) plugin).getAPI();
            if (coreProtectAPI != null && coreProtectAPI.isEnabled() && coreProtectAPI.APIVersion() >= 10) {
                this.api = coreProtectAPI;
                return;
            }
        }
        
        throw new RuntimeException("CoreProtect API v10+ is required");
    }


    @Override
    public LookupResult performInteractLookup(Player player, Block block, int page) {
        if (api == null) {
            return new LookupResult(Collections.emptyList(), page, 1, false);
        }

        // RIGHT_CLICK on interact blocks: Use blockLookup API which queries exact coordinates
        // Filter to only action 2 (interactions/clicks)
        List<String[]> allResults = api.blockLookup(block, getLookupTimeSeconds());

        if (allResults == null || allResults.isEmpty()) {
            return new LookupResult(Collections.emptyList(), page, 1, false);
        }

        // Filter to only interaction (2) actions
        List<String[]> filteredResults = new ArrayList<>();
        for (String[] result : allResults) {
            CoreProtectAPI.ParseResult parseResult = safeParse(result);
            if (parseResult != null && parseResult.getActionId() == ACTION_INTERACT) {
                filteredResults.add(result);
            }
        }
        
        return buildInteractionLookupResult(filteredResults, block.getLocation(), page);
    }
    
    private LookupResult buildInteractionLookupResult(List<String[]> results, Location location, int page) {
        List<Component> lines = new ArrayList<>();
        
        if (results == null || results.isEmpty()) {
            return new LookupResult(lines, page, 1, false);
        }
        
        int startIndex = (page - 1) * RESULTS_PER_PAGE;
        int endIndex = Math.min(startIndex + RESULTS_PER_PAGE, results.size());
        int totalPages = (int) Math.ceil((double) results.size() / RESULTS_PER_PAGE);
        
        if (startIndex >= results.size()) {
            return new LookupResult(lines, page, totalPages, false);
        }
        
        lines.add(buildHeader("Player Interactions", location));
        long now = System.currentTimeMillis();
        
        // Add data lines
        for (int i = startIndex; i < endIndex; i++) {
            String[] result = results.get(i);
            if (result == null || result.length == 0) {
                continue;
            }
            
            CoreProtectAPI.ParseResult parseResult = safeParse(result);
            if (parseResult == null) {
                continue;
            }
            long timeMillis = parseResult.getTimestamp();
            String playerName = parseResult.getPlayer();
            String blockName = formatMaterialName(parseResult.getType());

            Component timeSinceComponent = formatTimeSince(now - timeMillis);
            Component line = Component.text()
                    .append(timeSinceComponent)
                    .append(Component.text(" "))
                    .append(Component.text("-", NamedTextColor.WHITE))
                    .append(Component.text(" "))
                    .append(Component.text(playerName, NamedTextColor.DARK_AQUA))
                    .append(Component.text(" clicked ", NamedTextColor.WHITE))
                    .append(Component.text(blockName, NamedTextColor.DARK_AQUA))
                    .append(Component.text(".", NamedTextColor.WHITE))
                    .build();
            lines.add(line);
        }
        
        return new LookupResult(lines, page, totalPages, lines.size() > 1);
    }

    @Override
    public LookupResult performBlockLookup(Player player, BlockState blockState, int page) {
        if (api == null) {
            return new LookupResult(Collections.emptyList(), page, 1, false);
        }

        // LEFT_CLICK: Use blockLookup API which queries exact coordinates
        // Filter to only actions 0 (break) and 1 (place)
        Block block = blockState.getLocation().getBlock();
        List<String[]> allResults = api.blockLookup(block, getLookupTimeSeconds());

        if (allResults == null || allResults.isEmpty()) {
            return new LookupResult(Collections.emptyList(), page, 1, false);
        }

        // Filter to only break (0) and place (1) actions
        List<String[]> filteredResults = new ArrayList<>();
        for (String[] result : allResults) {
            CoreProtectAPI.ParseResult parseResult = safeParse(result);
            if (parseResult != null) {
                int actionId = parseResult.getActionId();
                if (actionId == ACTION_BREAK || actionId == ACTION_PLACE) {
                    filteredResults.add(result);
                }
            }
        }
        
        return buildBlockLookupResult(filteredResults, blockState.getLocation(), page);
    }

    private int getLookupTimeSeconds() {
        InspectorPlugin plugin = InspectorPlugin.getPlugin();
        if (plugin == null || plugin.getSettings() == null) {
            return MAX_LOOKUP_TIME_SECONDS;
        }

        int configuredDays = plugin.getSettings().historyLimitDate;
        if (configuredDays <= 0 || configuredDays == Integer.MAX_VALUE) {
            return MAX_LOOKUP_TIME_SECONDS;
        }

        long configuredSeconds = (long) configuredDays * SECONDS_PER_DAY;
        return (int) Math.min(MAX_LOOKUP_TIME_SECONDS, configuredSeconds);
    }

    private LookupResult buildBlockLookupResult(List<String[]> results, Location location, int page) {
        List<Component> lines = new ArrayList<>();
        
        if (results == null || results.isEmpty()) {
            return new LookupResult(lines, page, 1, false);
        }
        
        int startIndex = (page - 1) * RESULTS_PER_PAGE;
        int endIndex = Math.min(startIndex + RESULTS_PER_PAGE, results.size());
        int totalPages = (int) Math.ceil((double) results.size() / RESULTS_PER_PAGE);
        
        if (startIndex >= results.size()) {
            return new LookupResult(lines, page, totalPages, false);
        }
        
        lines.add(buildHeader("Inspector", location));
        long now = System.currentTimeMillis();
        
        // Add data lines
        for (int i = startIndex; i < endIndex; i++) {
            String[] result = results.get(i);
            if (result == null || result.length == 0) {
                continue;
            }
            
            CoreProtectAPI.ParseResult parseResult = safeParse(result);
            if (parseResult == null) {
                continue;
            }
            long timeMillis = parseResult.getTimestamp();
            String playerName = parseResult.getPlayer();
            int actionId = parseResult.getActionId();
            String blockName = formatMaterialName(parseResult.getType());

            String action = actionId == ACTION_BREAK ? "broke" : "placed";
            NamedTextColor tagColor = actionId == ACTION_PLACE ? NamedTextColor.GREEN : NamedTextColor.RED;
            String tag = actionId == ACTION_PLACE ? "+" : "-";
            Component timeSinceComponent = formatTimeSince(now - timeMillis);

            Component line = Component.text()
                    .append(timeSinceComponent)
                    .append(Component.text(" "))
                    .append(Component.text(tag, tagColor))
                    .append(Component.text(" "))
                    .append(Component.text(playerName, NamedTextColor.DARK_AQUA))
                    .append(Component.text(" " + action + " ", NamedTextColor.WHITE))
                    .append(Component.text(blockName, NamedTextColor.DARK_AQUA))
                    .append(Component.text(".", NamedTextColor.WHITE))
                    .build();
            lines.add(line);
        }
        
        return new LookupResult(lines, page, totalPages, lines.size() > 1);
    }

    @Override
    public LookupResult performChestLookup(Player player, Block block, int page) {
        if (api == null) {
            return new LookupResult(Collections.emptyList(), page, 1, false);
        }

        // Container transactions are stored in a separate 'container' table, not the 'block' table
        // We need to query the container table directly using CoreProtect's Database class
        return performContainerLookup(block.getLocation(), page);
    }

    private LookupResult performContainerLookup(Location location, int page) {
        try {
            return performContainerLookupInternal(location, page);
        } catch (Exception e) {
            InspectorPlugin.log("[DEBUG] Error performing container lookup: " + e.getMessage());
            return new LookupResult(Collections.emptyList(), page, 1, false);
        }
    }

    private LookupResult performContainerLookupInternal(Location location, int page) throws Exception {
        // CoreProtect's API doesn't expose container lookups, so we access internal classes directly
        List<Component> lines = new ArrayList<>();
        
        Connection conn = Database.getConnection(true, 1000);
        if (conn == null) {
            return new LookupResult(lines, page, 1, false);
        }

        try (Connection connection = conn) {
            String prefix = ConfigHandler.prefix;
            String worldName = location.getWorld().getName();
            Integer worldId = ConfigHandler.worlds.get(worldName);
            if (worldId == null) {
                String worldQuery = "SELECT id FROM " + prefix + "world WHERE world = ? LIMIT 0, 1";
                try (PreparedStatement worldStmt = connection.prepareStatement(worldQuery)) {
                    worldStmt.setString(1, worldName);
                    try (ResultSet worldResult = worldStmt.executeQuery()) {
                        if (worldResult.next()) {
                            worldId = worldResult.getInt("id");
                        } else {
                            return new LookupResult(lines, page, 1, false);
                        }
                    }
                }
            }
            
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            
            int pageStart = (page - 1) * RESULTS_PER_PAGE;
            long timeThreshold = System.currentTimeMillis() / 1000L - getLookupTimeSeconds();

            // Get total count
            int totalCount = 0;
            String indexHint = "";
            String countQuery = "SELECT COUNT(*) as count FROM " + prefix + "container " + indexHint +
                    "WHERE wid = ? AND x = ? AND z = ? AND y = ? AND time > ? LIMIT 0, 1";
            try (PreparedStatement countStmt = connection.prepareStatement(countQuery)) {
                countStmt.setInt(1, worldId);
                countStmt.setInt(2, x);
                countStmt.setInt(3, z);
                countStmt.setInt(4, y);
                countStmt.setLong(5, timeThreshold);
                try (ResultSet countResults = countStmt.executeQuery()) {
                    if (countResults.next()) {
                        totalCount = countResults.getInt("count");
                    }
                }
            }
            int totalPages = (int) Math.ceil(totalCount / (RESULTS_PER_PAGE + 0.0));
            
            // Query data
            String query = "SELECT time,user,action,type,data,amount,metadata FROM " + prefix + "container " + indexHint +
                    "WHERE wid = ? AND x = ? AND z = ? AND y = ? AND time > ? " +
                    "ORDER BY rowid DESC LIMIT ?, ?";
            
            lines.add(buildHeader("Container Transactions", location));
            
            boolean hasAnyData = false;
            long now = System.currentTimeMillis();

            try (PreparedStatement dataStmt = connection.prepareStatement(query)) {
                dataStmt.setInt(1, worldId);
                dataStmt.setInt(2, x);
                dataStmt.setInt(3, z);
                dataStmt.setInt(4, y);
                dataStmt.setLong(5, timeThreshold);
                dataStmt.setInt(6, pageStart);
                dataStmt.setInt(7, RESULTS_PER_PAGE);
                try (ResultSet results = dataStmt.executeQuery()) {
                while (results.next()) {
                    long resultTime = results.getLong("time");
                    int resultUserId = results.getInt("user");
                    int resultAction = results.getInt("action");
                    int resultType = results.getInt("type");
                    int resultData = results.getInt("data");
                    int resultAmount = results.getInt("amount");
                    byte[] resultMetadata = results.getBytes("metadata");
                    
                    // Load player name
                    if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                        UserStatement.loadName(connection, resultUserId);
                    }
                    String playerName = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                    if (playerName == null) {
                        playerName = "Unknown";
                    }
                    
                    String materialName = getContainerMaterialName(resultType);
                    String action = resultAction != 0 ? "added" : "removed";
                    NamedTextColor tagColor = resultAction != 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
                    String tag = resultAction != 0 ? "+" : "-";
                    Component hoverDetails = buildContainerHover(materialName, resultAmount, action, resultData, resultMetadata);
                    
                    long timeMillis = resultTime * 1000L;
                    Component timeSinceComponent = formatTimeSince(now - timeMillis);
                    
                    Component line = Component.text()
                            .append(timeSinceComponent)
                            .append(Component.text(" "))
                            .append(Component.text(tag, tagColor))
                            .append(Component.text(" "))
                            .append(Component.text(playerName, NamedTextColor.DARK_AQUA))
                            .append(Component.text(" " + action + " ", NamedTextColor.WHITE))
                            .append(Component.text("x" + resultAmount + " " + materialName, NamedTextColor.DARK_AQUA))
                            .append(Component.text(".", NamedTextColor.WHITE))
                            .hoverEvent(hoverDetails)
                            .build();
                    lines.add(line);
                    hasAnyData = true;
                }
                }
            }
            
            if (!hasAnyData) {
                lines.clear();
            }
            
            return new LookupResult(lines, page, totalPages, hasAnyData);
        }
    }

    private Component buildHeader(String title, Location location) {
        return Component.text()
                .append(Component.text("----- ", NamedTextColor.WHITE))
                .append(Component.text(title, NamedTextColor.DARK_AQUA))
                .append(Component.text(" ----- ", NamedTextColor.WHITE))
                .append(Component.text("(x" + location.getBlockX() + "/y" + location.getBlockY() + 
                        "/z" + location.getBlockZ() + ")", NamedTextColor.GRAY))
                .build();
    }

    private Component formatTimeSince(long milliseconds) {
        // Match CoreProtect's time formatting (using seconds, not milliseconds)
        long seconds = milliseconds / 1000;
        double timeSince = seconds / 60.0; // Convert to minutes
        
        DecimalFormat decimalFormat = TIME_FORMAT.get();
        String timeStr;
        
        // Minutes
        if (timeSince < 60.0) {
            timeStr = decimalFormat.format(timeSince) + "m ago";
        }
        // Hours
        else if ((timeSince = timeSince / 60.0) < 24.0) {
            timeStr = decimalFormat.format(timeSince) + "h ago";
        }
        // Days
        else {
            timeSince = timeSince / 24.0;
            timeStr = decimalFormat.format(timeSince) + "d ago";
        }
        
        // CoreProtect wraps time in GREY color (matching ChatUtils.getTimeSince with component=true)
        return Component.text(timeStr, NamedTextColor.GRAY);
    }

    private CoreProtectAPI.ParseResult safeParse(String[] result) {
        if (result == null || result.length == 0) {
            return null;
        }
        try {
            return api.parseResult(result);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String formatMaterialName(Material type) {
        if (type == null) {
            return "";
        }
        return type.name().toLowerCase().replace("_", " ");
    }

    private String getContainerMaterialName(int resultType) {
        String cached = materialNameCache.get(resultType);
        if (cached != null) {
            return cached;
        }

        String materialNameFromCache = ConfigHandler.materialsReversed.get(resultType);
        Material material = null;
        if (materialNameFromCache != null) {
            String materialKey = materialNameFromCache.toUpperCase();
            int namespaceSeparator = materialKey.indexOf(':');
            if (namespaceSeparator >= 0 && namespaceSeparator + 1 < materialKey.length()) {
                materialKey = materialKey.substring(namespaceSeparator + 1);
            }
            material = Material.getMaterial(materialKey);
        }
        if (material == null) {
            material = Material.AIR;
        }

        String materialName = formatMaterialName(material);
        materialNameCache.put(resultType, materialName);
        return materialName;
    }

    private Component buildContainerHover(
            String materialName,
            int amount,
            String action,
            int dataValue,
            byte[] metadata
    ) {
        List<Component> decodedMetaLines = decodeMetadataTooltip(metadata);
        if (!decodedMetaLines.isEmpty()) {
            Component hover = Component.empty();
            for (int i = 0; i < decodedMetaLines.size(); i++) {
                if (i > 0) {
                    hover = hover.append(Component.newline());
                }
                hover = hover.append(decodedMetaLines.get(i));
            }
            return hover;
        }
        return Component.text(materialName, NamedTextColor.DARK_AQUA);
    }

    private List<Component> decodeMetadataTooltip(byte[] metadata) {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(metadata);
             BukkitObjectInputStream objectStream = new BukkitObjectInputStream(byteStream)) {
            Object root = objectStream.readObject();
            Map<String, Object> metaMap = findPrimaryMetaMap(root);
            if (metaMap == null || metaMap.isEmpty()) {
                return Collections.emptyList();
            }
            return buildDecodedMetaLines(metaMap);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private Map<String, Object> findPrimaryMetaMap(Object root) {
        if (root == null) {
            return null;
        }
        if (root instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stringMap = (Map<String, Object>) map;
            if (looksLikeItemMetaMap(stringMap)) {
                return stringMap;
            }
            for (Object value : stringMap.values()) {
                Map<String, Object> nested = findPrimaryMetaMap(value);
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (root instanceof List<?> list) {
            for (Object entry : list) {
                Map<String, Object> nested = findPrimaryMetaMap(entry);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private boolean looksLikeItemMetaMap(Map<String, Object> map) {
        return map.containsKey("meta-type")
                || map.containsKey("display-name")
                || map.containsKey("lore")
                || map.containsKey("enchants")
                || map.containsKey("stored-enchants");
    }

    private List<Component> buildDecodedMetaLines(Map<String, Object> metaMap) {
        List<Component> lines = new ArrayList<>();

        Object displayNameObj = metaMap.get("display-name");
        if (displayNameObj instanceof String displayName && !displayName.isBlank()) {
            lines.add(deserializeLegacyText(displayName));
        }

        Object loreObj = metaMap.get("lore");
        if (loreObj instanceof List<?> loreList && !loreList.isEmpty()) {
            for (Object loreLine : loreList) {
                String loreText = String.valueOf(loreLine);
                if (!stripLegacyColors(loreText).isBlank()) {
                    lines.add(deserializeLegacyText(loreText));
                }
            }
        }

        Map<String, Integer> enchantments = normalizeEnchantments(metaMap.get("enchants"));
        enchantments.putAll(normalizeEnchantments(metaMap.get("stored-enchants")));
        if (!enchantments.isEmpty()) {
            for (Map.Entry<String, Integer> enchantEntry : enchantments.entrySet()) {
                lines.add(Component.text(prettyEnchantName(enchantEntry.getKey(), enchantEntry.getValue()), NamedTextColor.GRAY));
            }
        }

        return lines;
    }

    private Map<String, Integer> normalizeEnchantments(Object value) {
        Map<String, Integer> normalized = new java.util.LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> rawMap)) {
            return normalized;
        }
        for (Map.Entry<?, ?> rawEntry : rawMap.entrySet()) {
            String key = String.valueOf(rawEntry.getKey());
            int level;
            Object levelObj = rawEntry.getValue();
            if (levelObj instanceof Number number) {
                level = number.intValue();
            } else {
                try {
                    level = Integer.parseInt(String.valueOf(levelObj));
                } catch (NumberFormatException ex) {
                    level = 1;
                }
            }
            normalized.put(key, level);
        }
        return normalized;
    }

    private String prettyEnchantName(String key, int level) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < normalized.length()) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }
        String[] words = normalized.split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name + " " + toRoman(level);
    }

    private String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(level);
        };
    }

    private String stripLegacyColors(String text) {
        return LEGACY_COLOR_PATTERN.matcher(text).replaceAll("");
    }

    private Component deserializeLegacyText(String text) {
        String trimmed = text.trim();
        if (looksLikeJsonComponent(trimmed)) {
            try {
                return GSON_SERIALIZER.deserialize(trimmed);
            } catch (Exception ignored) {
                // Fall through to legacy/plain parsing.
            }
        }
        if (text.indexOf('\u00A7') >= 0) {
            return SECTION_SERIALIZER.deserialize(text);
        }
        if (text.indexOf('&') >= 0) {
            return AMPERSAND_SERIALIZER.deserialize(text);
        }
        return Component.text(text, NamedTextColor.WHITE);
    }

    private boolean looksLikeJsonComponent(String text) {
        return (text.startsWith("{") && text.endsWith("}"))
                || (text.startsWith("[") && text.endsWith("]"));
    }

}
