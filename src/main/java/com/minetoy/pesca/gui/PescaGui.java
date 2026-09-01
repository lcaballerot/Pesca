package com.minetoy.pesca.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker for inventories this plugin owns.
 *
 * <p>Identifying a GUI by its holder rather than by title means a player cannot open a
 * renamed chest and have their clicks handled as shop purchases.
 */
public interface PescaGui extends InventoryHolder {

    /** @return true if the click should be cancelled */
    default boolean handleClick(InventoryClickEvent event) {
        return true;
    }

    default boolean handleDrag(InventoryDragEvent event) {
        return true;
    }

    default void handleClose(InventoryCloseEvent event) {
    }
}
