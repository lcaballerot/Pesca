package com.minetoy.pesca.area;

import org.bukkit.Location;
import org.bukkit.World;

/** An axis-aligned cuboid. The only place tournament fish exist. */
public record Area(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static Area between(Location a, Location b) {
        return new Area(
                a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()),
                Math.min(a.getBlockY(), b.getBlockY()),
                Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockY(), b.getBlockY()),
                Math.max(a.getBlockZ(), b.getBlockZ())
        );
    }

    public boolean contains(Location loc) {
        if (loc == null) {
            return false;
        }
        World w = loc.getWorld();
        if (w == null || !w.getName().equals(world)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public String describe() {
        return world + " " + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ;
    }
}
