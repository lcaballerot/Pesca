package com.minetoy.pesca.model;

/**
 * A rarity tier. {@code points} is the only thing that counts towards a tournament;
 * {@code priceMultiplier} only affects the sell value.
 */
public record Rarity(
        String id,
        String display,
        String color,
        int points,
        double priceMultiplier
) {
    public static final Rarity FALLBACK =
            new Rarity("comun", "comun", "#a8b4b4", 1, 1.0);
}
