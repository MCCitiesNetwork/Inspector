package com.minecraftcitiesnetwork.inspector.handlers;

import com.minecraftcitiesnetwork.inspector.InspectorPlugin;
import com.minecraftcitiesnetwork.inspector.hooks.ClaimsProvider;
import com.minecraftcitiesnetwork.inspector.hooks.ClaimsProvider_GriefPrevention;
import com.minecraftcitiesnetwork.inspector.hooks.ClaimsProvider_WorldGuard;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public final class HooksHandler {

    private final EnumMap<ClaimsProvider.ClaimPlugin, ClaimsProvider> claimsProviders = new EnumMap<>(ClaimsProvider.ClaimPlugin.class);

    public HooksHandler(InspectorPlugin plugin) {
        InspectorPlugin.log("Loading providers started...");
        long startTime = System.currentTimeMillis();

        // Direct instantiation - these are hard dependencies
        claimsProviders.put(ClaimsProvider.ClaimPlugin.GRIEF_PREVENTION, new ClaimsProvider_GriefPrevention());
        claimsProviders.put(ClaimsProvider.ClaimPlugin.WORLD_GUARD, new ClaimsProvider_WorldGuard());

        InspectorPlugin.log("Loading providers done (Took " + (System.currentTimeMillis() - startTime) + "ms)");
    }

    public boolean hasRole(ClaimsProvider.ClaimPlugin claimPlugin, Player player, Location location, Collection<String> roles) {
        ClaimsProvider claimsProvider = claimsProviders.get(claimPlugin);
        return claimsProvider == null || claimsProvider.hasRole(player, location, roles);
    }

    public ClaimsProvider.ClaimPlugin getRegionAt(Player player, Location location) {
        for (Map.Entry<ClaimsProvider.ClaimPlugin, ClaimsProvider> entry : claimsProviders.entrySet()) {
            if (entry.getValue().hasRegionAccess(player, location))
                return entry.getKey();
        }

        return ClaimsProvider.ClaimPlugin.NONE;
    }

}
