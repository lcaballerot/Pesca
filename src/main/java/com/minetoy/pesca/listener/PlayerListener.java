package com.minetoy.pesca.listener;

import com.minetoy.pesca.area.AreaManager;
import com.minetoy.pesca.effect.WinnerEffects;
import com.minetoy.pesca.tournament.TournamentManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerListener implements Listener {

    private final JavaPlugin plugin;
    private final TournamentManager tournament;
    private final AreaManager areas;
    private final WinnerEffects effects;

    public PlayerListener(JavaPlugin plugin, TournamentManager tournament, AreaManager areas,
                          WinnerEffects effects) {
        this.plugin = plugin;
        this.tournament = tournament;
        this.areas = areas;
        this.effects = effects;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // A tick later: joining players are not fully ready to receive items yet.
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> {
                    if (event.getPlayer().isOnline()) {
                        tournament.deliverPendingLoot(event.getPlayer());
                    }
                }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        areas.cancelSelection(event.getPlayer().getUniqueId());
    }

    /** The celebration must not hurt the person being celebrated, or anyone watching. */
    @EventHandler
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Firework firework)) {
            return;
        }
        if (firework.getPersistentDataContainer().has(effects.key(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
