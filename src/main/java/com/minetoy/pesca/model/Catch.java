package com.minetoy.pesca.model;

/**
 * One caught specimen: a species, the weight rolled for it, and the rarity it counts as.
 *
 * <p>Rarity is stored on the catch rather than read from the species, because the special
 * bait can promote a specimen a tier above what its species normally is. Points and the
 * price multiplier both follow the catch's rarity, not the species' default.
 */
public record Catch(Fish fish, double kg, Rarity rarity, boolean enhanced) {

    /** An ordinary catch, at its species' own rarity. */
    public static Catch of(Fish fish, double kg) {
        return new Catch(fish, kg, fish.rarity(), false);
    }

    /** The same specimen after the bait has worked on it. */
    public Catch enhanced(Rarity promoted, double weightBonus) {
        return new Catch(fish, kg * (1.0 + weightBonus), promoted, true);
    }

    public double price() {
        return fish.pricePerKg() * kg * rarity.priceMultiplier();
    }

    public int points() {
        return rarity.points();
    }

    /** True when the bait pushed this specimen above its species' normal tier. */
    public boolean promoted() {
        return enhanced && !rarity.id().equals(fish.rarity().id());
    }
}
