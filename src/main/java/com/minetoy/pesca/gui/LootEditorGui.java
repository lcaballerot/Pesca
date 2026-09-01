package com.minetoy.pesca.gui;

import com.minetoy.pesca.loot.LootStore;
import com.minetoy.pesca.text.Msg;
import com.minetoy.pesca.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The prize editor opened by {@code /pesca admin loot}.
 *
 * <p>Deliberately a plain, fully editable double chest: whatever the admin leaves in it
 * is the prize, stored byte-for-byte. Items from other plugins keep their NBT, so an
 * ItemsAdder fish or a custom-enchanted rod survives the round trip unchanged.
 */
public final class LootEditorGui implements PescaGui {

    public static final int SIZE = 54;

    private final Inventory inventory;
    private final LootStore loot;
    private final Msg msg;

    public LootEditorGui(LootStore loot, Msg msg) {
        this.loot = loot;
        this.msg = msg;
        this.inventory = Bukkit.createInventory(this, SIZE, msg.get("gui.loot-title"));

        int slot = 0;
        for (ItemStack item : loot.copies()) {
            if (slot >= SIZE) {
                break;
            }
            inventory.setItem(slot++, item);
        }
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public boolean handleClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        return false; // free editing
    }

    @Override
    public boolean handleDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        return false;
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        loot.set(inventory.getContents());
        HumanEntity who = event.getPlayer();
        msg.send(who, "admin.loot-saved", Msg.p("cantidad", Numbers.plain(loot.size())));
    }
}
