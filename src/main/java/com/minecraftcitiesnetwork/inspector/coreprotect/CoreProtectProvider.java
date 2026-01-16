package com.minecraftcitiesnetwork.inspector.coreprotect;

import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

import java.util.List;

public interface CoreProtectProvider {

    // Returns formatted components ready to send to player, with header, data lines, and footer
    // First element is header, last element is footer, middle elements are data lines
    LookupResult performInteractLookup(Player player, Block block, int page);

    LookupResult performBlockLookup(Player player, BlockState blockState, int page);

    LookupResult performChestLookup(Player player, Block block, int page);

    class LookupResult {
        public final List<Component> lines;
        public final int currentPage;
        public final int maxPage;
        public final boolean hasData;

        public LookupResult(List<Component> lines, int currentPage, int maxPage, boolean hasData) {
            this.lines = lines;
            this.currentPage = currentPage;
            this.maxPage = maxPage;
            this.hasData = hasData;
        }
    }

}
