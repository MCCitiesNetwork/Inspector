package com.minecraftcitiesnetwork.inspector.hooks;

import com.minecraftcitiesnetwork.inspector.InspectorPlugin;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;

public final class ClaimsProvider_GriefPrevention implements ClaimsProvider {

    public ClaimsProvider_GriefPrevention() {
        InspectorPlugin.log(" - Using GriefPrevention as ClaimsProvider.");
    }

    @Override
    public ClaimPlugin getClaimPlugin() {
        return ClaimPlugin.GRIEF_PREVENTION;
    }

    @Override
    public boolean hasRole(Player player, Location location, Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return true;
        }

        DataStore dataStore = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore;
        PlayerData playerData = dataStore.getPlayerData(player.getUniqueId());
        Claim claim = dataStore.getClaimAt(location, false, playerData.lastClaim);
        
        if (claim == null) {
            return false;
        }

        boolean isOwner = playerData.getClaims().contains(claim);

        for (String role : roles) {
            String roleLower = role.toLowerCase();
            if (roleLower.equals("owner") && isOwner) {
                return true;
            }
            if (roleLower.equals("member") && isOwner) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean hasRegionAccess(Player player, Location location) {
        DataStore dataStore = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore;
        PlayerData playerData = dataStore.getPlayerData(player.getUniqueId());
        Claim claim = dataStore.getClaimAt(location, false, playerData.lastClaim);
        return claim != null && playerData.getClaims().contains(claim);
    }
}
