package com.minecraftcitiesnetwork.inspector.hooks;

import com.minecraftcitiesnetwork.inspector.InspectorPlugin;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class ClaimsProvider_WorldGuard implements ClaimsProvider {

    private final RegionContainer regionContainer;

    public ClaimsProvider_WorldGuard() {
        this.regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
        InspectorPlugin.log(" - Using WorldGuard as ClaimsProvider.");
    }

    @Override
    public ClaimPlugin getClaimPlugin() {
        return ClaimPlugin.WORLD_GUARD;
    }

    @Override
    public boolean hasRole(Player player, Location location, Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return true;
        }

        ProtectedRegion region = getRegionAt(location);
        if (region == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        Set<UUID> owners = getOwners(region);
        Set<UUID> members = getMembers(region);

        for (String role : roles) {
            String roleLower = role.toLowerCase();
            if (roleLower.equals("owner") && owners.contains(playerId)) {
                return true;
            }
            if (roleLower.equals("member") && (owners.contains(playerId) || members.contains(playerId))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean hasRegionAccess(Player player, Location location) {
        ProtectedRegion region = getRegionAt(location);
        if (region == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        Set<UUID> owners = getOwners(region);
        Set<UUID> members = getMembers(region);

        return owners.contains(playerId) || members.contains(playerId);
    }

    private ProtectedRegion getRegionAt(Location location) {
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(location.getWorld());
        RegionManager manager = regionContainer.get(weWorld);
        if (manager == null) {
            return null;
        }

        com.sk89q.worldedit.math.BlockVector3 vector = BukkitAdapter.asBlockVector(location);
        return manager.getApplicableRegions(vector).getRegions().stream()
                .max((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()))
                .orElse(null);
    }

    private Set<UUID> getOwners(ProtectedRegion region) {
        Set<UUID> ownerSet = new LinkedHashSet<>();
        ProtectedRegion cursor = region;
        while (cursor != null) {
            ownerSet.addAll(cursor.getOwners().getPlayerDomain().getUniqueIds());
            cursor = cursor.getParent();
        }
        return ownerSet;
    }

    private Set<UUID> getMembers(ProtectedRegion region) {
        Set<UUID> memberSet = new LinkedHashSet<>();
        ProtectedRegion cursor = region;
        while (cursor != null) {
            memberSet.addAll(cursor.getMembers().getPlayerDomain().getUniqueIds());
            cursor = cursor.getParent();
        }
        return memberSet;
    }
}
