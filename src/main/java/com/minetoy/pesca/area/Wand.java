package com.minetoy.pesca.area;

import com.minetoy.pesca.text.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * The stone axe handed out by {@code /pesca admin setarea}.
 *
 * <p>Identified by persistent data rather than by name or material, so a plain stone
 * axe someone happens to be carrying is never mistaken for the wand.
 */
public final class Wand {

    private final NamespacedKey key;
    private final Msg msg;

    public Wand(Plugin plugin, Msg msg) {
        this.key = new NamespacedKey(plugin, "area_wand");
        this.msg = msg;
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.STONE_AXE, 1);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(msg.get("area.wand-name").decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        for (Component line : msg.getList("area.wand-lore")) {
            lore.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.STONE_AXE || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
