package com.minetoy.pesca.shop;

import com.minetoy.pesca.config.PescaConfig;
import com.minetoy.pesca.model.Catch;
import com.minetoy.pesca.model.Rarity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The cubo de cebo.
 *
 * <p>Carrying at least one gives every catch inside the area a {@code 1 / chance-one-in}
 * shot at being upgraded: the specimen is promoted a rarity tier and gains a share of
 * extra weight. A bucket is only spent when the bait actually fires, so buying one is a
 * bet on a future catch rather than a per-cast cost.
 */
public final class BaitService {

    private final PescaConfig config;
    private final SpecialItems items;

    public BaitService(PescaConfig config, SpecialItems items) {
        this.config = config;
        this.items = items;
    }

    /**
     * Rolls the bait for this catch.
     *
     * @return the upgraded catch, or the original when the player has no bait or the
     *         roll missed
     */
    public Catch apply(Player player, Catch caught) {
        if (!hasBait(player)) {
            return caught;
        }
        if (ThreadLocalRandom.current().nextInt(config.baitChanceOneIn()) != 0) {
            return caught;
        }
        if (config.baitConsumeOnProc() && !consumeOne(player)) {
            return caught; // vanished between the check and the spend
        }
        Rarity promoted = config.promote(caught.rarity(), config.baitRarityBump());
        return caught.enhanced(promoted, config.baitWeightBonus());
    }

    public boolean hasBait(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (items.isBait(item)) {
                return true;
            }
        }
        return false;
    }

    public int countBait(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (items.isBait(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private boolean consumeOne(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!items.isBait(item)) {
                continue;
            }
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                contents[i] = null;
            }
            inventory.setStorageContents(contents);
            return true;
        }
        return false;
    }
}
