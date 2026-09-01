package com.minetoy.pesca.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * One buyable entry in the {@code /pesca shop} GUI.
 *
 * @param bait   marks this as the special bait: tagged in persistent data and consumed
 *               by {@code BaitService} when it upgrades a catch
 * @param repair marks this as fishing line: dropped onto a damaged rod it mends a share
 *               of its durability and one unit is consumed
 */
public record ShopItem(
        String id,
        int slot,
        Material material,
        int amount,
        double price,
        String name,
        List<String> lore,
        Map<String, Integer> enchants,
        boolean bait,
        boolean repair
) {
}
