package com.minetoy.pesca.listener;

import com.minetoy.pesca.config.PescaConfig;
import com.minetoy.pesca.gui.PescaGui;
import com.minetoy.pesca.shop.SpecialItems;
import com.minetoy.pesca.text.Msg;
import com.minetoy.pesca.util.Numbers;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Repairing a rod with the sedal: pick the line up on the cursor, click it onto a
 * damaged fishing rod, and the rod recovers a share of its durability while one unit of
 * line is spent.
 */
public final class RepairListener implements Listener {

    private final PescaConfig config;
    private final SpecialItems items;
    private final Msg msg;

    public RepairListener(PescaConfig config, SpecialItems items, Msg msg) {
        this.config = config;
        this.items = items;
        this.msg = msg;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        // Never inside our own windows — the shop and the loot editor own their clicks.
        if (event.getView().getTopInventory().getHolder() instanceof PescaGui) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack line = event.getCursor();
        ItemStack rod = event.getCurrentItem();

        if (!items.isRepairLine(line) || rod == null || rod.getType() != Material.FISHING_ROD) {
            return;
        }

        ItemMeta meta = rod.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }

        int max = rod.getType().getMaxDurability();
        int damage = damageable.getDamage();
        if (max <= 0) {
            return;
        }

        event.setCancelled(true);

        if (damage <= 0) {
            msg.send(player, "repair.not-damaged");
            player.playSound(Sound.sound(Key.key("minecraft:entity.villager.no"), Sound.Source.MASTER, 1f, 1f));
            return;
        }

        // A rod stacked more than once cannot carry a per-item damage value.
        if (rod.getAmount() > 1) {
            msg.send(player, "repair.single-only");
            return;
        }

        int healed = Math.min(damage, (int) Math.ceil(max * config.repairPercent()));
        damageable.setDamage(damage - healed);
        rod.setItemMeta(meta);
        // Write it back explicitly: getCurrentItem is a live reference on Paper but a
        // copy on some implementations, and a silent no-op would be baffling to debug.
        event.setCurrentItem(rod);

        line.setAmount(line.getAmount() - 1);
        event.getView().setCursor(line.getAmount() <= 0 ? null : line);

        int remaining = max - (damage - healed);
        msg.send(player, "repair.done",
                Msg.p("porcentaje", Numbers.percent(config.repairPercent())),
                Msg.p("durabilidad", Numbers.plain(remaining) + " / " + Numbers.plain(max)));
        player.playSound(Sound.sound(Key.key("minecraft:block.anvil_use"), Sound.Source.MASTER, 0.7f, 1.6f));
    }
}
