package com.minecraftcitiesnetwork.inspector.command;

import com.minecraftcitiesnetwork.inspector.Locale;
import com.minecraftcitiesnetwork.inspector.InspectorPlugin;
import com.minecraftcitiesnetwork.inspector.coreprotect.LookupType;
import com.minecraftcitiesnetwork.inspector.hooks.ClaimsProvider;
import com.minecraftcitiesnetwork.inspector.utils.InspectPlayers;
import com.minecraftcitiesnetwork.inspector.utils.ItemUtils;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public final class InspectCommand implements CommandExecutor, TabCompleter {

    private final InspectorPlugin plugin;

    public InspectCommand(InspectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!hasPermission(player)) {
            Locale.NO_PERMISSION.send(player);
            return true;
        }

        if (args.length > 1) {
            Locale.COMMAND_USAGE.send(player, label, "[page]");
            return true;
        }

        if (args.length == 1) {
            handlePageCommand(player, args[0], label);
            return true;
        }

        toggleInspectMode(player);

        return true;
    }

    private boolean hasPermission(Player player) {
        return player.hasPermission("inspector.use");
    }

    private void handlePageCommand(Player player, String pageArg, String label) {
        if (!InspectPlayers.isInspectEnabled(player) || !InspectPlayers.hasBlock(player)) {
            Locale.NO_BLOCK_SELECTED.send(player, label + " " + pageArg);
            return;
        }

        int page = parsePage(pageArg, player);
        if (page < 1) {
            return;
        }

        Block block = InspectPlayers.getBlock(player);
        ClaimsProvider.ClaimPlugin claimPlugin = plugin.getHooksHandler().getRegionAt(player, block.getLocation());
        
        if (!player.hasPermission("inspector.use")) {
            Locale.NO_PERMISSION.send(player);
            return;
        }
        
        if (!plugin.getHooksHandler().hasRole(claimPlugin, player, block.getLocation(), plugin.getSettings().requiredRoles)) {
            String roles = plugin.getSettings().requiredRoles.stream().collect(Collectors.joining(", "));
            Locale.REQUIRED_ROLE.send(player, roles);
            return;
        }

        Action clickMode = InspectPlayers.hasClickMode(player) ? 
                InspectPlayers.getClickMode(player) : Action.LEFT_CLICK_BLOCK;

        LookupType lookupType = determineLookupType(clickMode, block);
        plugin.getCoreProtect().performLookup(lookupType, player, block, page);
    }

    private int parsePage(String pageArg, Player player) {
        try {
            int page = Integer.parseInt(pageArg);
            if (page < 1) {
                Locale.SPECIFY_PAGE.send(player);
                return -1;
            }
            return page;
        } catch (NumberFormatException ex) {
            Locale.SPECIFY_PAGE.send(player);
            return -1;
        }
    }

    private LookupType determineLookupType(Action clickMode, Block block) {
        if (clickMode == Action.LEFT_CLICK_BLOCK) {
            return LookupType.BLOCK_LOOKUP;
        } else {
            return ItemUtils.isContainer(block.getType()) ? 
                    LookupType.CHEST_TRANSACTIONS : LookupType.INTERACTION_LOOKUP;
        }
    }

    private void toggleInspectMode(Player player) {
        if (InspectPlayers.isInspectEnabled(player)) {
            InspectPlayers.disableInspectMode(player);
            Locale.INSPECTOR_OFF.send(player);
        } else {
            InspectPlayers.enableInspectMode(player);
            Locale.INSPECTOR_ON.send(player);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return new ArrayList<>();
    }

}
