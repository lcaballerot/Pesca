package com.minetoy.pesca.listener;

import com.minetoy.pesca.area.Area;
import com.minetoy.pesca.area.AreaManager;
import com.minetoy.pesca.area.Wand;
import com.minetoy.pesca.text.Msg;
import com.minetoy.pesca.util.Numbers;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Left-click corner picking with the wand: first click sets pos1, the next sets pos2
 * and saves the area. Clicking again starts a fresh pair.
 */
public final class SelectionListener implements Listener {

    private final AreaManager areas;
    private final Wand wand;
    private final Msg msg;

    public SelectionListener(AreaManager areas, Wand wand, Msg msg) {
        this.areas = areas;
        this.wand = wand;
        this.msg = msg;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!wand.isWand(event.getItem())) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("pesca.admin")) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        // Cancel first: in creative a left click destroys the block outright.
        event.setCancelled(true);

        Location loc = block.getLocation();
        int corner = areas.pickCorner(player.getUniqueId(), loc);

        if (corner == 1) {
            msg.send(player, "area.pos1",
                    Msg.p("x", String.valueOf(loc.getBlockX())),
                    Msg.p("y", String.valueOf(loc.getBlockY())),
                    Msg.p("z", String.valueOf(loc.getBlockZ())));
            return;
        }

        Area area = areas.area();
        msg.send(player, "area.pos2",
                Msg.p("x", String.valueOf(loc.getBlockX())),
                Msg.p("y", String.valueOf(loc.getBlockY())),
                Msg.p("z", String.valueOf(loc.getBlockZ())));
        msg.send(player, "area.saved",
                Msg.p("mundo", area.world()),
                Msg.p("bloques", Numbers.plain(area.volume())),
                Msg.p("ancho", String.valueOf(area.maxX() - area.minX() + 1)),
                Msg.p("alto", String.valueOf(area.maxY() - area.minY() + 1)),
                Msg.p("largo", String.valueOf(area.maxZ() - area.minZ() + 1)));
    }

    /** Belt and braces: some client/server combinations still fire the break. */
    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent event) {
        if (wand.isWand(event.getPlayer().getInventory().getItemInMainHand())
                && event.getPlayer().hasPermission("pesca.admin")) {
            event.setCancelled(true);
        }
    }
}
