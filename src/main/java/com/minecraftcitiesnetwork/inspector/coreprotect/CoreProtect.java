package com.minecraftcitiesnetwork.inspector.coreprotect;

import com.minecraftcitiesnetwork.inspector.Locale;
import com.minecraftcitiesnetwork.inspector.InspectorPlugin;
import com.minecraftcitiesnetwork.inspector.hooks.ClaimsProvider;
import com.minecraftcitiesnetwork.inspector.utils.InspectPlayers;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;

public final class CoreProtect {

    private final InspectorPlugin plugin;
    private CoreProtectProvider coreProtectProvider;
    private final String requiredRolesText;

    public CoreProtect(InspectorPlugin plugin) {
        this.plugin = plugin;
        this.coreProtectProvider = loadCoreProtectProvider();
        this.requiredRolesText = plugin.getSettings().requiredRoles.stream().collect(Collectors.joining(", "));
    }

    public void performLookup(LookupType type, Player player, Block block, int page) {
        long startedAtNs = System.nanoTime();
        if (!validateLookup(player, block, page, startedAtNs)) {
            return;
        }

        BlockState blockState = block.getState();
        long validatedAtNs = System.nanoTime();
        plugin.debugTiming("validate lookup took " + nanosToMillis(validatedAtNs - startedAtNs) + "ms");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                performDatabaseLookup(type, player, block, blockState, page, validatedAtNs));
    }

    private boolean validateLookup(Player player, Block block, int page, long startedAtNs) {
        // Check permission
        if (!player.hasPermission("inspector.use")) {
            Locale.NO_PERMISSION.send(player);
            plugin.debugTiming("blocked by permission in " + nanosToMillis(System.nanoTime() - startedAtNs) + "ms");
            return false;
        }

        // Check if player is in a claim
        ClaimsProvider.ClaimPlugin claimPlugin = plugin.getHooksHandler().getRegionAt(player, block.getLocation());
        if (claimPlugin == ClaimsProvider.ClaimPlugin.NONE) {
            Locale.NOT_INSIDE_CLAIM.send(player);
            plugin.debugTiming("blocked by claim lookup in " + nanosToMillis(System.nanoTime() - startedAtNs) + "ms");
            return false;
        }

        // Check if player has required role
        if (!plugin.getHooksHandler().hasRole(claimPlugin, player, block.getLocation(), plugin.getSettings().requiredRoles)) {
            Locale.REQUIRED_ROLE.send(player, requiredRolesText);
            plugin.debugTiming("blocked by role check in " + nanosToMillis(System.nanoTime() - startedAtNs) + "ms");
            return false;
        }

        // Check cooldown
        if (InspectPlayers.isCooldown(player)) {
            DecimalFormat df = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(java.util.Locale.ENGLISH));
            Locale.COOLDOWN.send(player, df.format(InspectPlayers.getTimeLeft(player) / 1000.0));
            plugin.debugTiming("blocked by cooldown in " + nanosToMillis(System.nanoTime() - startedAtNs) + "ms");
            return false;
        }

        // Check page limit
        if (plugin.getSettings().historyLimitPage < page) {
            Locale.LIMIT_REACH.send(player);
            plugin.debugTiming("blocked by page limit in " + nanosToMillis(System.nanoTime() - startedAtNs) + "ms");
            return false;
        }

        // Set cooldown and block
        if (plugin.getSettings().cooldown != -1) {
            InspectPlayers.setCooldown(player);
        }
        InspectPlayers.setBlock(player, block);

        return true;
    }

    private void performDatabaseLookup(LookupType type, Player player, Block block, BlockState blockState, int page, long validatedAtNs) {
        long queryStartedAtNs = System.nanoTime();
        CoreProtectProvider.LookupResult result = performLookup(type, player, block, blockState, page);
        long queryEndedAtNs = System.nanoTime();
        plugin.debugTiming("coreprotect query for " + type + " took " + nanosToMillis(queryEndedAtNs - queryStartedAtNs) + "ms");

        if (result == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> sendLookupResult(type, player, result, page, validatedAtNs, queryEndedAtNs));
    }

    private void sendLookupResult(
            LookupType type,
            Player player,
            CoreProtectProvider.LookupResult result,
            int page,
            long validatedAtNs,
            long queryEndedAtNs
    ) {
        if (!player.isOnline()) {
            return;
        }
        long sendStartedAtNs = System.nanoTime();
        if (result.maxPage < page) {
            Locale.LIMIT_REACH.send(player);
            plugin.debugTiming("response blocked by max page in " + nanosToMillis(System.nanoTime() - validatedAtNs) + "ms after validation");
            return;
        }
        // Apply history limit
        int displayMaxPage = plugin.getSettings().historyLimitPage > 0 ?
                Math.min(result.maxPage, plugin.getSettings().historyLimitPage) : result.maxPage;

        // Send formatted messages
        if (!result.hasData) {
            Component noDataMsg = type == LookupType.INTERACTION_LOOKUP ? Locale.NO_BLOCK_INTERACTIONS.getMessage() :
                    type == LookupType.CHEST_TRANSACTIONS ? Locale.NO_CONTAINER_TRANSACTIONS.getMessage() :
                    Locale.NO_BLOCK_DATA.getMessage();
            if (noDataMsg != null) {
                player.sendMessage(noDataMsg);
            }
            plugin.debugTiming("no-data response sent in " + nanosToMillis(System.nanoTime() - sendStartedAtNs) + "ms");
            return;
        }

        // Send components
        for (Component line : result.lines) {
            player.sendMessage(line);
        }
        
        // Replace footer with page info if needed
        if (result.lines.size() > 1 && displayMaxPage > 1) {
            Component footer = Locale.INSPECT_DATA_FOOTER.getMessage(result.currentPage, displayMaxPage);
            if (footer != null) {
                player.sendMessage(footer);
            }
        }
        long totalDurationNs = System.nanoTime() - validatedAtNs;
        plugin.debugTiming("message send took " + nanosToMillis(System.nanoTime() - sendStartedAtNs)
                + "ms; end-to-end lookup took " + nanosToMillis(totalDurationNs)
                + "ms (" + nanosToMillis(queryEndedAtNs - validatedAtNs) + "ms async)");
    }

    private CoreProtectProvider.LookupResult performLookup(LookupType type, Player player, Block block, BlockState blockState, int page) {
        switch (type) {
            case INTERACTION_LOOKUP:
                return this.coreProtectProvider.performInteractLookup(player, block, page);
            case BLOCK_LOOKUP:
                return this.coreProtectProvider.performBlockLookup(player, blockState, page);
            case CHEST_TRANSACTIONS:
                return this.coreProtectProvider.performChestLookup(player, block, page);
            default:
                return null;
        }
    }

    private static CoreProtectProvider loadCoreProtectProvider() {
        // Direct instantiation - CoreProtect is a hard dependency
        return new CoreProtect22();
    }

    private static String nanosToMillis(long durationNs) {
        return String.format(java.util.Locale.ENGLISH, "%.3f", durationNs / 1_000_000.0);
    }

}
