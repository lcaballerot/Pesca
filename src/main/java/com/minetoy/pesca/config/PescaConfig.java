package com.minetoy.pesca.config;

import com.minetoy.pesca.model.Fish;
import com.minetoy.pesca.model.Rarity;
import com.minetoy.pesca.model.ShopItem;
import com.minetoy.pesca.util.Durations;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Everything read out of config.yml, validated once at load. */
public final class PescaConfig {

    private final JavaPlugin plugin;

    private boolean smallCaps = true;
    private boolean onlyReplaceVanillaFish = true;
    private double weightCurve = 2.4;
    private String catchSound = "minecraft:entity.player.levelup";
    private int soundMinPoints = 8;
    private Set<String> announceRarities = Set.of();

    private long defaultDuration = 2 * 60 * 60 * 1000L;
    private long defaultFrequency = 24 * 60 * 60 * 1000L;
    private String defaultBroadcasts = "true";
    private List<Long> reminders = new ArrayList<>();
    private int topSize = 10;
    private boolean deliverLootOnJoin = true;

    private String currencySymbol = "$";

    private final Map<String, Rarity> rarities = new LinkedHashMap<>();
    private final Map<String, Fish> fish = new LinkedHashMap<>();
    private double totalChance;

    private int baitChanceOneIn = 75;
    private int baitRarityBump = 1;
    private double baitWeightBonus = 0.35;
    private boolean baitConsumeOnProc = true;

    private double repairPercent = 0.25;

    private final Map<String, String> enchantNames = new LinkedHashMap<>();

    private String shopTitle = "tienda de pesca";
    private int shopRows = 5;
    private int sellAllSlot = 40;
    private int balanceSlot = 4;
    private Material shopFiller = Material.BLACK_STAINED_GLASS_PANE;
    private final Map<String, ShopItem> shopItems = new LinkedHashMap<>();

    /** Rarities in the order config.yml declares them — that order defines the tier ladder. */
    private final List<Rarity> rarityLadder = new ArrayList<>();

    public PescaConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        smallCaps = c.getBoolean("small-caps", true);
        onlyReplaceVanillaFish = c.getBoolean("fishing.only-replace-vanilla-fish", true);
        weightCurve = Math.max(0.1, c.getDouble("fishing.weight-curve", 2.4));
        catchSound = c.getString("fishing.effects.sound", "minecraft:entity.player.levelup");
        soundMinPoints = c.getInt("fishing.effects.min-points", 8);
        announceRarities = new LinkedHashSet<>(c.getStringList("fishing.announce-rarities"));

        defaultDuration = duration(c.getString("tournament.duration", "2h"), 2 * 60 * 60 * 1000L, "tournament.duration");
        defaultFrequency = duration(c.getString("tournament.frequency", "1d"), 24 * 60 * 60 * 1000L, "tournament.frequency");
        defaultBroadcasts = String.valueOf(c.get("tournament.broadcasts", "true")).toLowerCase(Locale.ROOT);
        topSize = Math.max(1, Math.min(50, c.getInt("tournament.top-size", 10)));
        deliverLootOnJoin = c.getBoolean("tournament.deliver-loot-on-join", true);

        reminders = new ArrayList<>();
        for (String r : c.getStringList("tournament.reminders")) {
            long ms = Durations.parse(r);
            if (ms > 0) {
                reminders.add(ms);
            } else {
                warn("tournament.reminders", "'" + r + "' no es una duracion valida");
            }
        }
        Collections.sort(reminders);

        currencySymbol = c.getString("economy.currency-symbol", "$");

        baitChanceOneIn = Math.max(1, c.getInt("bait.chance-one-in", 75));
        baitRarityBump = Math.max(0, c.getInt("bait.rarity-bump", 1));
        baitWeightBonus = c.getDouble("bait.weight-bonus", 0.35);
        baitConsumeOnProc = c.getBoolean("bait.consume-on-proc", true);
        repairPercent = Math.max(0.01, Math.min(1.0, c.getDouble("repair.percent", 0.25)));

        enchantNames.clear();
        ConfigurationSection names = c.getConfigurationSection("enchant-names");
        if (names != null) {
            for (String key : names.getKeys(false)) {
                enchantNames.put(key.toUpperCase(Locale.ROOT), names.getString(key, key));
            }
        }

        loadRarities(c);
        loadFish(c);
        loadShop(c);
    }

    private void loadRarities(FileConfiguration c) {
        rarities.clear();
        rarityLadder.clear();
        ConfigurationSection sec = c.getConfigurationSection("rarities");
        if (sec == null) {
            warn("rarities", "no hay ninguna rareza definida, se usara una por defecto");
            rarities.put(Rarity.FALLBACK.id(), Rarity.FALLBACK);
            rarityLadder.add(Rarity.FALLBACK);
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection r = sec.getConfigurationSection(id);
            if (r == null) {
                continue;
            }
            rarities.put(id, new Rarity(
                    id,
                    r.getString("display", id),
                    r.getString("color", "#ffffff"),
                    r.getInt("points", 1),
                    r.getDouble("price-multiplier", 1.0)
            ));
        }
        if (rarities.isEmpty()) {
            rarities.put(Rarity.FALLBACK.id(), Rarity.FALLBACK);
        }
        rarityLadder.addAll(rarities.values());
    }

    private void loadFish(FileConfiguration c) {
        fish.clear();
        totalChance = 0;
        ConfigurationSection sec = c.getConfigurationSection("fish");
        if (sec == null) {
            warn("fish", "no hay ningun pez definido; la pesca en el area seguira siendo vanilla");
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection f = sec.getConfigurationSection(id);
            if (f == null) {
                continue;
            }

            Material material = Material.matchMaterial(f.getString("material", "COD"));
            if (material == null || !material.isItem()) {
                warn("fish." + id, "material desconocido '" + f.getString("material") + "', se omite el pez");
                continue;
            }

            String rarityId = f.getString("rarity", "comun");
            Rarity rarity = rarities.get(rarityId);
            if (rarity == null) {
                warn("fish." + id, "rareza desconocida '" + rarityId + "', se usa la primera definida");
                rarity = rarities.values().iterator().next();
            }

            double min = f.getDouble("kg.min", 0.1);
            double max = f.getDouble("kg.max", Math.max(min, 1.0));
            if (max < min) {
                warn("fish." + id, "kg.max es menor que kg.min, se intercambian");
                double tmp = min;
                min = max;
                max = tmp;
            }
            if (min <= 0) {
                min = 0.01;
            }

            double chance = f.getDouble("chance", 1.0);
            if (chance <= 0) {
                warn("fish." + id, "chance debe ser mayor que 0, se omite el pez");
                continue;
            }

            Fish entry = new Fish(
                    id,
                    f.getString("display", id.replace('_', ' ')),
                    rarity,
                    material,
                    f.getInt("model-data", 0),
                    chance,
                    min,
                    max,
                    f.getDouble("price-per-kg", 10.0)
            );
            fish.put(id, entry);
            totalChance += chance;
        }
    }

    private void loadShop(FileConfiguration c) {
        shopItems.clear();
        shopTitle = c.getString("shop.title", "tienda de pesca");
        shopRows = Math.max(1, Math.min(6, c.getInt("shop.rows", 5)));
        sellAllSlot = c.getInt("shop.sell-all-slot", 40);
        balanceSlot = c.getInt("shop.balance-slot", 4);

        Material filler = Material.matchMaterial(c.getString("shop.filler", "BLACK_STAINED_GLASS_PANE"));
        if (filler == null || !filler.isItem()) {
            warn("shop.filler", "material desconocido, se usa BLACK_STAINED_GLASS_PANE");
            filler = Material.BLACK_STAINED_GLASS_PANE;
        }
        shopFiller = filler;

        int size = shopRows * 9;
        ConfigurationSection sec = c.getConfigurationSection("shop.items");
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            Material material = Material.matchMaterial(s.getString("material", "STONE"));
            if (material == null || !material.isItem()) {
                warn("shop.items." + id, "material desconocido, se omite");
                continue;
            }
            int slot = s.getInt("slot", -1);
            if (slot < 0 || slot >= size) {
                warn("shop.items." + id, "slot " + slot + " fuera del inventario (0-" + (size - 1) + "), se omite");
                continue;
            }
            Map<String, Integer> enchants = new LinkedHashMap<>();
            ConfigurationSection e = s.getConfigurationSection("enchants");
            if (e != null) {
                for (String key : e.getKeys(false)) {
                    enchants.put(key, e.getInt(key, 1));
                }
            }
            shopItems.put(id, new ShopItem(
                    id,
                    slot,
                    material,
                    Math.max(1, s.getInt("amount", 1)),
                    s.getDouble("price", 0.0),
                    s.getString("name", id.replace('_', ' ')),
                    s.getStringList("lore"),
                    enchants,
                    s.getBoolean("bait", false),
                    s.getBoolean("repair", false)
            ));
        }
        if (sellAllSlot >= size) {
            sellAllSlot = -1;
        }
        if (balanceSlot >= size) {
            balanceSlot = -1;
        }
    }

    private long duration(String raw, long fallback, String path) {
        long ms = Durations.parse(raw);
        if (ms <= 0) {
            warn(path, "'" + raw + "' no es una duracion valida, se usa " + Durations.canonical(fallback));
            return fallback;
        }
        return ms;
    }

    private void warn(String path, String message) {
        plugin.getLogger().warning("config.yml -> " + path + ": " + message);
    }

    // ---- accessors ---------------------------------------------------------

    public boolean smallCaps() {
        return smallCaps;
    }

    public boolean onlyReplaceVanillaFish() {
        return onlyReplaceVanillaFish;
    }

    public double weightCurve() {
        return weightCurve;
    }

    public String catchSound() {
        return catchSound;
    }

    public int soundMinPoints() {
        return soundMinPoints;
    }

    public Set<String> announceRarities() {
        return announceRarities;
    }

    public long defaultDuration() {
        return defaultDuration;
    }

    public long defaultFrequency() {
        return defaultFrequency;
    }

    public String defaultBroadcasts() {
        return defaultBroadcasts;
    }

    public List<Long> reminders() {
        return reminders;
    }

    public int topSize() {
        return topSize;
    }

    public boolean deliverLootOnJoin() {
        return deliverLootOnJoin;
    }

    public String currencySymbol() {
        return currencySymbol;
    }

    public Map<String, Rarity> rarities() {
        return rarities;
    }

    public Rarity rarity(String id) {
        return rarities.getOrDefault(id, Rarity.FALLBACK);
    }

    public Map<String, Fish> fish() {
        return fish;
    }

    public Fish fish(String id) {
        return fish.get(id);
    }

    public double totalChance() {
        return totalChance;
    }

    public String shopTitle() {
        return shopTitle;
    }

    public int shopRows() {
        return shopRows;
    }

    public int sellAllSlot() {
        return sellAllSlot;
    }

    public int balanceSlot() {
        return balanceSlot;
    }

    public Material shopFiller() {
        return shopFiller;
    }

    public Map<String, ShopItem> shopItems() {
        return shopItems;
    }

    public Map<String, String> enchantNames() {
        return enchantNames;
    }

    public String enchantName(String key) {
        return enchantNames.getOrDefault(key.toUpperCase(Locale.ROOT),
                key.toLowerCase(Locale.ROOT).replace('_', ' '));
    }

    // ---- bait --------------------------------------------------------------

    public int baitChanceOneIn() {
        return baitChanceOneIn;
    }

    public int baitRarityBump() {
        return baitRarityBump;
    }

    public double baitWeightBonus() {
        return baitWeightBonus;
    }

    public boolean baitConsumeOnProc() {
        return baitConsumeOnProc;
    }

    public double repairPercent() {
        return repairPercent;
    }

    // ---- rarity ladder -----------------------------------------------------

    public List<Rarity> rarityLadder() {
        return rarityLadder;
    }

    /**
     * The rarity {@code steps} tiers above the given one, clamped at the top of the
     * ladder. Used by the bait to promote a catch.
     */
    public Rarity promote(Rarity from, int steps) {
        int index = rarityLadder.indexOf(from);
        if (index < 0) {
            return from;
        }
        return rarityLadder.get(Math.min(rarityLadder.size() - 1, index + steps));
    }
}
