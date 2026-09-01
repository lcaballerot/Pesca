package com.minetoy.pesca.listener;

import com.minetoy.pesca.area.AreaManager;
import com.minetoy.pesca.config.PescaConfig;
import com.minetoy.pesca.fish.FishFactory;
import com.minetoy.pesca.model.Catch;
import com.minetoy.pesca.shop.BaitService;
import com.minetoy.pesca.storage.model.Score;
import com.minetoy.pesca.text.Msg;
import com.minetoy.pesca.tournament.TournamentManager;
import com.minetoy.pesca.util.Numbers;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Turns a catch inside the tournament area into a Pesca fish.
 *
 * <p>Outside the area nothing is touched at all, so the rest of the map keeps vanilla
 * fishing exactly as it was — including drowned farms, treasure rolls and Luck of the
 * Sea behaviour.
 */
public final class FishingListener implements Listener {

    private static final Set<Material> VANILLA_FISH = EnumSet.of(
            Material.COD, Material.SALMON, Material.PUFFERFISH, Material.TROPICAL_FISH);

    private final PescaConfig config;
    private final AreaManager areas;
    private final FishFactory factory;
    private final TournamentManager tournament;
    private final BaitService bait;
    private final Msg msg;

    public FishingListener(PescaConfig config, AreaManager areas, FishFactory factory,
                           TournamentManager tournament, BaitService bait, Msg msg) {
        this.config = config;
        this.areas = areas;
        this.factory = factory;
        this.tournament = tournament;
        this.bait = bait;
        this.msg = msg;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (!(event.getCaught() instanceof Item caughtEntity)) {
            return;
        }
        // The bobber decides, not the player — you have to cast into the water.
        if (!areas.isInside(event.getHook().getLocation())) {
            return;
        }
        if (config.onlyReplaceVanillaFish()
                && !VANILLA_FISH.contains(caughtEntity.getItemStack().getType())) {
            return; // leave junk and treasure alone
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("pesca.use")) {
            return;
        }

        Catch caught = factory.roll();
        if (caught == null) {
            return; // no fish configured; fall through to vanilla
        }

        // The bait may promote the specimen before it is ever turned into an item.
        Catch afterBait = bait.apply(player, caught);
        boolean baited = afterBait.enhanced();
        caught = afterBait;

        caughtEntity.setItemStack(factory.toItem(caught, player.getName()));

        tournament.record(player, caught);

        msg.send(player, "fishing.caught",
                Msg.c("pez", factory.coloredName(caught.fish(), caught.rarity())),
                Msg.c("rareza", factory.coloredRarity(caught.rarity())),
                Msg.p("kg", Numbers.kg(caught.kg())),
                Msg.p("puntos", String.valueOf(caught.points())),
                Msg.p("precio", config.currencySymbol() + Numbers.money(caught.price())));

        if (baited) {
            msg.send(player, "fishing.bait-proc",
                    Msg.c("rareza", factory.coloredRarity(caught.rarity())),
                    Msg.p("bonus", Numbers.percent(config.baitWeightBonus())),
                    Msg.p("restantes", String.valueOf(bait.countBait(player))));
            player.playSound(Sound.sound(
                    Key.key("minecraft:entity.player.levelup"), Sound.Source.PLAYER, 1f, 1.6f));
        }

        // record() has just created the score, so it is always present while running.
        Score score = tournament.scoreOf(player.getUniqueId());
        if (tournament.isRunning() && score != null) {
            msg.send(player, "fishing.total",
                    Msg.p("puntos", Numbers.plain(score.points)),
                    Msg.p("puesto", String.valueOf(tournament.positionOf(player.getUniqueId()))));
        }

        playCatchSound(player, caught);
        announceIfNotable(player, caught);
    }

    private void playCatchSound(Player player, Catch caught) {
        String key = config.catchSound();
        if (key == null || key.isBlank()) {
            return;
        }
        if (caught.points() < config.soundMinPoints()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(key), Sound.Source.PLAYER, 1f, 1f));
        } catch (IllegalArgumentException e) {
            // A bad key in config.yml should not cost the player their fish.
        }
    }

    /** A legendary catch is worth telling the server about — it follows the broadcast setting. */
    private void announceIfNotable(Player player, Catch caught) {
        // The rarity it counts as, so a bait-promoted specimen announces on its new tier.
        if (!config.announceRarities().contains(caught.rarity().id())) {
            return;
        }
        tournament.broadcast(msg.get("fishing.announce",
                Msg.p("jugador", player.getName()),
                Msg.c("pez", factory.coloredName(caught.fish(), caught.rarity())),
                Msg.c("rareza", factory.coloredRarity(caught.rarity())),
                Msg.p("kg", Numbers.kg(caught.kg()))));
    }
}
