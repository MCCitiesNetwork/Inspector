package com.minecraftcitiesnetwork.inspector.utils;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;

import java.util.EnumMap;
import java.util.Map;

public final class ItemUtils {

    private static final Map<Material, Boolean> CONTAINER_CACHE = new EnumMap<>(Material.class);

    public static boolean isContainer(Material type){
        if (type == null) {
            return false;
        }
        Boolean cached = CONTAINER_CACHE.get(type);
        if (cached != null) {
            return cached;
        }

        boolean isContainer;
        try {
            BlockState state = type.createBlockData().createBlockState();
            isContainer = state instanceof Container;
        } catch (Exception e) {
            isContainer = false;
        }
        CONTAINER_CACHE.put(type, isContainer);
        return isContainer;
    }

}
