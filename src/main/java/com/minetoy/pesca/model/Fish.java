package com.minetoy.pesca.model;

import org.bukkit.Material;

/** A fish species as defined in config.yml. The per-catch weight is rolled separately. */
public record Fish(
        String id,
        String display,
        Rarity rarity,
        Material material,
        int modelData,
        double chance,
        double minKg,
        double maxKg,
        double pricePerKg
) {
    /**
     * Where this specimen sits in its species' size range, 0.0 to 1.0. Used for the
     * trophy marker in the lore — a 100% fish is the biggest the species can be.
     */
    public double trophyFraction(double kg) {
        double span = maxKg - minKg;
        if (span <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (kg - minKg) / span));
    }
}
