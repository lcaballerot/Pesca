package com.minetoy.pesca.loot;

import com.minetoy.pesca.storage.Database;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The prize the tournament winner receives, as configured through
 * {@code /pesca admin loot}.
 *
 * <p>Items go through Paper's own {@code serializeAsBytes} rather than a name/material
 * pair, so everything survives the round trip: enchantments, custom model data, PDC
 * from other plugins, attribute modifiers, book contents, shulker contents.
 */
public final class LootStore {

    private static final String STATE_KEY = "loot";

    private final JavaPlugin plugin;
    private final Database db;

    private List<ItemStack> loot = new ArrayList<>();

    public LootStore(JavaPlugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void load() {
        loot = new ArrayList<>();
        String raw = db.getState(STATE_KEY, "");
        if (raw.isBlank()) {
            return;
        }
        for (String chunk : raw.split(";")) {
            if (chunk.isBlank()) {
                continue;
            }
            try {
                loot.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(chunk)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("se descarto un item del botin ilegible: " + e.getMessage());
            }
        }
    }

    public void save() {
        StringBuilder sb = new StringBuilder();
        for (ItemStack item : loot) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        db.setState(STATE_KEY, sb.toString());
    }

    /** Replaces the whole prize with the contents of the editor inventory. */
    public void set(ItemStack[] contents) {
        loot = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                loot.add(item.clone());
            }
        }
        save();
    }

    /** Fresh copies, so handing the prize out never mutates the stored template. */
    public List<ItemStack> copies() {
        List<ItemStack> out = new ArrayList<>(loot.size());
        for (ItemStack item : loot) {
            out.add(item.clone());
        }
        return out;
    }

    public List<byte[]> serializedCopies() {
        List<byte[]> out = new ArrayList<>(loot.size());
        for (ItemStack item : loot) {
            out.add(item.serializeAsBytes());
        }
        return out;
    }

    public boolean isEmpty() {
        return loot.isEmpty();
    }

    public int size() {
        return loot.size();
    }
}
