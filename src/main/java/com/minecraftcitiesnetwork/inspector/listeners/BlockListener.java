package com.minecraftcitiesnetwork.inspector.listeners;

import com.minecraftcitiesnetwork.inspector.InspectorPlugin;
import com.minecraftcitiesnetwork.inspector.utils.InspectPlayers;
import com.minecraftcitiesnetwork.inspector.coreprotect.LookupType;
import com.minecraftcitiesnetwork.inspector.utils.ItemUtils;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;


public final class BlockListener implements Listener {

    private final InspectorPlugin plugin;

    public BlockListener(InspectorPlugin plugin){
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent e){
        if(!InspectPlayers.isInspectEnabled(e.getPlayer()))
            return;

        if(e.getClickedBlock() == null)
            return;

        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // Don't process if already cancelled (another plugin handled it)
        if(e.isCancelled())
            return;

        // Handle LEFT_CLICK_BLOCK first (takes priority)
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            handleLeftClick(e);
        }
        // Handle RIGHT_CLICK_BLOCK only if not already cancelled (left-click takes priority)
        else if (e.getAction() == Action.RIGHT_CLICK_BLOCK && !e.isCancelled()) {
            handleRightClick(e);
        }
    }

    private void handleLeftClick(PlayerInteractEvent event) {
        event.setCancelled(true);
        Block clickedBlock = event.getClickedBlock();
        Player player = event.getPlayer();
        
        InspectPlayers.setBlock(player, clickedBlock);
        InspectPlayers.setClickMode(player, Action.LEFT_CLICK_BLOCK);
        plugin.getCoreProtect().performLookup(LookupType.BLOCK_LOOKUP, player, clickedBlock, 1);
    }

    private void handleRightClick(PlayerInteractEvent event) {
        event.setCancelled(true);
        Block clickedBlock = event.getClickedBlock();
        Player player = event.getPlayer();
        
        boolean isContainer = ItemUtils.isContainer(clickedBlock.getType());
        LookupType lookupType = isContainer ? LookupType.CHEST_TRANSACTIONS : LookupType.INTERACTION_LOOKUP;
        
        InspectPlayers.setBlock(player, clickedBlock);
        InspectPlayers.setClickMode(player, Action.RIGHT_CLICK_BLOCK);
        plugin.getCoreProtect().performLookup(lookupType, player, clickedBlock, 1);
    }


}

