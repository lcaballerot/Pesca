package com.minetoy.pesca.fish;

import com.minetoy.pesca.config.PescaConfig;
import com.minetoy.pesca.model.Catch;
import com.minetoy.pesca.model.Fish;
import com.minetoy.pesca.model.Rarity;
import com.minetoy.pesca.text.Msg;
import com.minetoy.pesca.util.Numbers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolls catches and turns them into items.
 *
 * <p>The species id and the rolled weight are written into the item's persistent data,
 * so {@code /pesca sell} prices a fish from its real identity rather than by matching
 * its display name — renaming a fish in an anvil cannot change what it is worth.
 */
public final class FishFactory {

    private static final int BAR_SEGMENTS = 10;

    private final PescaConfig config;
    private final Msg msg;
    private final NamespacedKey keyId;
    private final NamespacedKey keyKg;
    private final NamespacedKey keyRarity;
    private final NamespacedKey keyEnhanced;

    public FishFactory(Plugin plugin, PescaConfig config, Msg msg) {
        this.config = config;
        this.msg = msg;
        this.keyId = new NamespacedKey(plugin, "fish_id");
        this.keyKg = new NamespacedKey(plugin, "fish_kg");
        this.keyRarity = new NamespacedKey(plugin, "fish_rarity");
        this.keyEnhanced = new NamespacedKey(plugin, "fish_enhanced");
    }

    // ---- rolling -----------------------------------------------------------

    /** Picks a species by relative chance, then rolls its weight. Null if no fish are configured. */
    public Catch roll() {
        Fish species = rollSpecies();
        if (species == null) {
            return null;
        }
        return Catch.of(species, rollWeight(species));
    }

    private Fish rollSpecies() {
        double total = config.totalChance();
        if (total <= 0) {
            return null;
        }
        double pick = ThreadLocalRandom.current().nextDouble(total);
        double running = 0;
        for (Fish f : config.fish().values()) {
            running += f.chance();
            if (pick < running) {
                return f;
            }
        }
        // Floating point can leave `pick` just past the end of the last bucket.
        Fish last = null;
        for (Fish f : config.fish().values()) {
            last = f;
        }
        return last;
    }

    /**
     * Weight is biased towards the small end of the range: a trophy specimen has to be
     * rare for a record to mean anything. {@code weight-curve} of 1.0 makes it uniform.
     */
    private double rollWeight(Fish species) {
        double r = ThreadLocalRandom.current().nextDouble();
        double biased = Math.pow(r, config.weightCurve());
        return species.minKg() + (species.maxKg() - species.minKg()) * biased;
    }

    // ---- items -------------------------------------------------------------

    public ItemStack toItem(Catch caught, String anglerName) {
        Fish f = caught.fish();
        Rarity r = caught.rarity();
        ItemStack item = new ItemStack(f.material(), 1);
        ItemMeta meta = item.getItemMeta();

        TagResolver[] resolvers = resolvers(caught, anglerName);

        meta.displayName(noItalic(msg.get("item.name", resolvers)));

        List<Component> lore = new ArrayList<>();
        for (Component line : msg.getList("item.lore", resolvers)) {
            lore.add(noItalic(line));
        }
        if (caught.enhanced()) {
            for (Component line : msg.getList("item.lore-enhanced", resolvers)) {
                lore.add(noItalic(line));
            }
        }
        meta.lore(lore);

        if (f.modelData() > 0) {
            meta.setCustomModelData(f.modelData());
        }

        meta.getPersistentDataContainer().set(keyId, PersistentDataType.STRING, f.id());
        meta.getPersistentDataContainer().set(keyKg, PersistentDataType.DOUBLE, caught.kg());
        meta.getPersistentDataContainer().set(keyRarity, PersistentDataType.STRING, r.id());
        if (caught.enhanced()) {
            meta.getPersistentDataContainer().set(keyEnhanced, PersistentDataType.BYTE, (byte) 1);
        }

        item.setItemMeta(meta);
        return item;
    }

    private TagResolver[] resolvers(Catch caught, String anglerName) {
        Fish f = caught.fish();
        Rarity r = caught.rarity();
        return new TagResolver[]{
                Msg.c("pez", coloredName(f, r)),
                Msg.c("rareza", coloredRarity(r)),
                Msg.c("rareza_base", coloredRarity(f.rarity())),
                Msg.c("barra", trophyBar(r, f.trophyFraction(caught.kg()))),
                Msg.p("kg", Numbers.kg(caught.kg())),
                Msg.p("trofeo", Numbers.percent(f.trophyFraction(caught.kg()))),
                Msg.p("precio", config.currencySymbol() + Numbers.money(caught.price())),
                Msg.p("pescador", anglerName == null ? "?" : anglerName),
                Msg.p("puntos", String.valueOf(r.points()))
        };
    }

    /** Reads a stack back into a catch, or null if it is not one of ours. */
    public Catch read(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        Fish species = config.fish(id);
        if (species == null) {
            // The species was removed from config.yml after this fish was caught.
            return null;
        }
        Double kg = meta.getPersistentDataContainer().get(keyKg, PersistentDataType.DOUBLE);

        // Rarity is stored per catch, because the bait can promote a specimen above its
        // species' normal tier. Fish caught before that existed fall back to the species.
        String rarityId = meta.getPersistentDataContainer().get(keyRarity, PersistentDataType.STRING);
        Rarity rarity = rarityId == null ? species.rarity() : config.rarity(rarityId);
        boolean enhanced = meta.getPersistentDataContainer().has(keyEnhanced, PersistentDataType.BYTE);

        return new Catch(species, kg == null ? species.minKg() : kg, rarity, enhanced);
    }

    public boolean isPescaFish(ItemStack item) {
        return read(item) != null;
    }

    // ---- presentation ------------------------------------------------------

    /** The species name in its own tier's colour. */
    public Component coloredName(Fish f) {
        return coloredName(f, f.rarity());
    }

    /** The species name in the colour of the tier this specimen actually counts as. */
    public Component coloredName(Fish f, Rarity r) {
        return msg.parse(f.display()).colorIfAbsent(color(r));
    }

    public Component coloredRarity(Rarity r) {
        return msg.parse(r.display()).colorIfAbsent(color(r));
    }

    private TextColor color(Rarity r) {
        TextColor c = TextColor.fromHexString(r.color());
        return c == null ? TextColor.color(0xFFFFFF) : c;
    }

    /** {@code ▰▰▰▰▰▰▱▱▱▱} — how close this specimen is to the species maximum. */
    private Component trophyBar(Rarity r, double fraction) {
        int filled = (int) Math.round(fraction * BAR_SEGMENTS);
        Component bar = Component.empty();
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            bar = bar.append(i < filled
                    ? Component.text("▰", color(r))
                    : Component.text("▱", TextColor.color(0x3A4A4D)));
        }
        return bar;
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
