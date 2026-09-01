package com.minetoy.pesca.listener;

import com.minetoy.pesca.gui.PescaGui;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** Routes inventory events to whichever {@link PescaGui} owns the inventory. */
public final class GuiListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        PescaGui gui = guiOf(event.getInventory().getHolder());
        if (gui == null) {
            return;
        }
        if (gui.handleClick(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        PescaGui gui = guiOf(event.getInventory().getHolder());
        if (gui == null) {
            return;
        }
        if (gui.handleDrag(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        PescaGui gui = guiOf(event.getInventory().getHolder());
        if (gui != null) {
            gui.handleClose(event);
        }
    }

    private PescaGui guiOf(InventoryHolder holder) {
        return holder instanceof PescaGui gui ? gui : null;
    }
}
