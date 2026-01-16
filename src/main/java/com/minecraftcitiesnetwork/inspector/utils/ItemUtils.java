package com.minecraftcitiesnetwork.inspector.utils;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;

public final class ItemUtils {

    public static boolean isContainer(Material type){
        // Check if the material is a container by trying to create a block state
        // This is more reliable than hardcoding material names and works across all versions
        if (type == null) {
            return false;
        }
        try {
            BlockState state = type.createBlockData().createBlockState();
            return state instanceof Container;
        } catch (Exception e) {
            return false;
        }
    }

}
