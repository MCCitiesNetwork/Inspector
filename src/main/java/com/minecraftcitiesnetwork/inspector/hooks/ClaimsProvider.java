package com.minecraftcitiesnetwork.inspector.hooks;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;

public interface ClaimsProvider {

    ClaimPlugin getClaimPlugin();

    boolean hasRole(Player player, Location location, Collection<String> roles);

    boolean hasRegionAccess(Player player, Location location);

    enum ClaimPlugin {

        GRIEF_PREVENTION,
        WORLD_GUARD,

        NONE,
        DEFAULT

    }

}
