package com.minetoy.pesca.area;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds the single fishing area and the in-progress wand selections.
 *
 * <p>With no area set, nothing is replaced anywhere — the server fishes vanilla until
 * an admin defines one. That is deliberate: a half-configured plugin should not start
 * silently rewriting catches across the whole map.
 */
public final class AreaManager {

    private final JavaPlugin plugin;
    private final File file;

    private Area area;

    /** Corners picked so far, per player. Index 0 is pos1, index 1 is pos2. */
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public AreaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "area.yml");
    }

    public void load() {
        if (!file.exists()) {
            area = null;
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (!y.contains("area.world")) {
            area = null;
            return;
        }
        area = new Area(
                y.getString("area.world"),
                y.getInt("area.min.x"), y.getInt("area.min.y"), y.getInt("area.min.z"),
                y.getInt("area.max.x"), y.getInt("area.max.y"), y.getInt("area.max.z")
        );
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        if (area != null) {
            y.set("area.world", area.world());
            y.set("area.min.x", area.minX());
            y.set("area.min.y", area.minY());
            y.set("area.min.z", area.minZ());
            y.set("area.max.x", area.maxX());
            y.set("area.max.y", area.maxY());
            y.set("area.max.z", area.maxZ());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("no se pudo guardar area.yml: " + e.getMessage());
        }
    }

    public Area area() {
        return area;
    }

    public boolean isSet() {
        return area != null;
    }

    public boolean isInside(Location loc) {
        return area != null && area.contains(loc);
    }

    public void clear() {
        area = null;
        save();
    }

    // ---- wand selection ----------------------------------------------------

    public void beginSelection(UUID player) {
        selections.put(player, new Location[2]);
    }

    public void cancelSelection(UUID player) {
        selections.remove(player);
    }

    public boolean isSelecting(UUID player) {
        return selections.containsKey(player);
    }

    /**
     * Records the next corner for this player.
     *
     * @return 1 if pos1 was set, 2 if pos2 was set (and the area is now saved)
     */
    public int pickCorner(UUID player, Location loc) {
        Location[] sel = selections.computeIfAbsent(player, k -> new Location[2]);
        if (sel[0] == null || sel[1] != null) {
            // First click, or a fresh pair after a completed selection.
            sel[0] = loc;
            sel[1] = null;
            return 1;
        }
        sel[1] = loc;
        area = Area.between(sel[0], sel[1]);
        save();
        return 2;
    }

    public Location pos1(UUID player) {
        Location[] sel = selections.get(player);
        return sel == null ? null : sel[0];
    }
}
