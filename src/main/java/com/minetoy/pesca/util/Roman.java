package com.minetoy.pesca.util;

/** Roman numerals for enchantment levels, as Minecraft writes them. */
public final class Roman {

    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] LETTERS = {
            "m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i"};

    private Roman() {
    }

    /**
     * Lowercase on purpose — the small-caps converter turns these into ɪ, ɪɪ, ɪɪɪ.
     * Levels outside the sane range fall back to the plain number.
     */
    public static String of(int number) {
        if (number <= 0 || number > 3999) {
            return String.valueOf(number);
        }
        StringBuilder sb = new StringBuilder();
        int rest = number;
        for (int i = 0; i < VALUES.length; i++) {
            while (rest >= VALUES[i]) {
                rest -= VALUES[i];
                sb.append(LETTERS[i]);
            }
        }
        return sb.toString();
    }
}
