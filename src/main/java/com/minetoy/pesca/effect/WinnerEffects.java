package com.minetoy.pesca.effect;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The winner's firework show.
 *
 * <p>Fireworks are tagged in persistent data and their damage is cancelled by
 * {@code FireworkDamageListener} — a celebration that kills the person it is
 * celebrating is not a celebration.
 */
public final class WinnerEffects {

    /** MineToy palette: ᴛᴏʀɴᴇᴏ gold, ᴘᴇꜱᴄᴀ aqua, positive green, and white. */
    private static final Color[] PALETTE = {
            Color.fromRGB(0xFF, 0xB0, 0x1F),
            Color.fromRGB(0x5E, 0xC8, 0xD8),
            Color.fromRGB(0x6E, 0xFF, 0x4A),
            Color.fromRGB(0xFF, 0xFF, 0xFF),
            Color.fromRGB(0xB6, 0xD9, 0xD8)
    };

    private static final FireworkEffect.Type[] SHAPES = {
            FireworkEffect.Type.BALL_LARGE,
            FireworkEffect.Type.STAR,
            FireworkEffect.Type.BURST,
            FireworkEffect.Type.BALL
    };

    private static final int WAVES = 6;
    private static final long WAVE_TICKS = 14L;

    private final JavaPlugin plugin;
    private final NamespacedKey key;

    public WinnerEffects(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "celebration_firework");
    }

    public NamespacedKey key() {
        return key;
    }

    /** Runs the show at the winner's feet, following them for its duration. */
    public void celebrate(Player winner) {
        if (winner == null || !winner.isOnline()) {
            return;
        }

        winner.playSound(Sound.sound(
                Key.key("minecraft:ui.toast.challenge_complete"), Sound.Source.MASTER, 1f, 1f));

        new BukkitRunnable() {
            int wave = 0;

            @Override
            public void run() {
                if (wave >= WAVES || !winner.isOnline()) {
                    cancel();
                    return;
                }
                Location base = winner.getLocation();
                // The last wave is the big one: a wide ring of large bursts.
                int count = (wave == WAVES - 1) ? 5 : 1 + ThreadLocalRandom.current().nextInt(2);
                for (int i = 0; i < count; i++) {
                    launch(base, wave == WAVES - 1);
                }
                base.getWorld().spawnParticle(Particle.END_ROD, base.clone().add(0, 1.2, 0),
                        20, 0.4, 0.6, 0.4, 0.02);
                base.getWorld().playSound(base,
                        org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.4f, 1f);
                wave++;
            }
        }.runTaskTimer(plugin, 0L, WAVE_TICKS);
    }

    private void launch(Location base, boolean finale) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double spread = finale ? 3.0 : 1.4;
        Location at = base.clone().add(
                rng.nextDouble(-spread, spread),
                1.0,
                rng.nextDouble(-spread, spread));

        world.spawn(at, Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();

            int colors = finale ? 3 : 1 + rng.nextInt(2);
            FireworkEffect.Builder b = FireworkEffect.builder()
                    .with(finale ? FireworkEffect.Type.BALL_LARGE : SHAPES[rng.nextInt(SHAPES.length)])
                    .trail(true)
                    .flicker(true);
            for (int i = 0; i < colors; i++) {
                b.withColor(PALETTE[rng.nextInt(PALETTE.length)]);
            }
            b.withFade(PALETTE[rng.nextInt(PALETTE.length)]);

            meta.addEffect(b.build());
            meta.setPower(finale ? 2 : 1);
            fw.setFireworkMeta(meta);

            fw.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
    }
}
