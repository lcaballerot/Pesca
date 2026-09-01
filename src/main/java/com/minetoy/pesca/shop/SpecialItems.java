package com.minetoy.pesca.shop;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Persistent-data tags for the two shop items that do something.
 *
 * <p>Tagging rather than matching on material means an ordinary tropical fish bucket or
 * a stack of vanilla string is never mistaken for the real thing — only what came out of
 * {@code /pesca shop} works.
 */
public final class SpecialItems {

    private final NamespacedKey baitKey;
    private final NamespacedKey repairKey;

    public SpecialItems(Plugin plugin) {
        this.baitKey = new NamespacedKey(plugin, "bait");
        this.repairKey = new NamespacedKey(plugin, "repair_line");
    }

    public void tagBait(ItemMeta meta) {
        meta.getPersistentDataContainer().set(baitKey, PersistentDataType.BYTE, (byte) 1);
    }

    public void tagRepair(ItemMeta meta) {
        meta.getPersistentDataContainer().set(repairKey, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isBait(ItemStack item) {
        return has(item, baitKey);
    }

    public boolean isRepairLine(ItemStack item) {
        return has(item, repairKey);
    }

    private boolean has(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
