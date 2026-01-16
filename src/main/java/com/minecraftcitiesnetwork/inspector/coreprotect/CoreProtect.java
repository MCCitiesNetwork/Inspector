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
import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;

public final class CoreProtect {

    private final InspectorPlugin plugin;
    private CoreProtectProvider coreProtectProvider;

    public CoreProtect(InspectorPlugin plugin) {
        this.plugin = plugin;
        this.coreProtectProvider = loadCoreProtectProvider();
    }

    public void performLookup(LookupType type, Player player, Block block, int page) {
        if (!validateLookup(player, block, page)) {
            return;
        }

        BlockState blockState = block.getState();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                performDatabaseLookup(type, player, block, blockState, page));
    }

    private boolean validateLookup(Player player, Block block, int page) {
        // Check permission
        if (!player.hasPermission("inspector.use")) {
            Locale.NO_PERMISSION.send(player);
            return false;
        }

        // Check if player is in a claim
        ClaimsProvider.ClaimPlugin claimPlugin = plugin.getHooksHandler().getRegionAt(player, block.getLocation());
        if (claimPlugin == ClaimsProvider.ClaimPlugin.NONE) {
            Locale.NOT_INSIDE_CLAIM.send(player);
            return false;
        }

        // Check if player has required role
        if (!plugin.getHooksHandler().hasRole(claimPlugin, player, block.getLocation(), plugin.getSettings().requiredRoles)) {
            String roles = plugin.getSettings().requiredRoles.stream().collect(Collectors.joining(", "));
            Locale.REQUIRED_ROLE.send(player, roles);
            return false;
        }

        // Check cooldown
        if (InspectPlayers.isCooldown(player)) {
            DecimalFormat df = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(java.util.Locale.ENGLISH));
            Locale.COOLDOWN.send(player, df.format(InspectPlayers.getTimeLeft(player) / 1000.0));
            return false;
        }

        // Check page limit
        if (plugin.getSettings().historyLimitPage < page) {
            Locale.LIMIT_REACH.send(player);
            return false;
        }

        // Set cooldown and block
        if (plugin.getSettings().cooldown != -1) {
            InspectPlayers.setCooldown(player);
        }
        InspectPlayers.setBlock(player, block);

        return true;
    }

    private void performDatabaseLookup(LookupType type, Player player, Block block, BlockState blockState, int page) {
        CoreProtectProvider.LookupResult result = performLookup(type, player, block, blockState, page);
        
        if (result == null) {
            return;
        }

        if (result.maxPage < page) {
            Locale.LIMIT_REACH.send(player);
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

}
