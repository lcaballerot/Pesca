package com.minetoy.pesca.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Number formatting for values injected into messages. Like {@link Durations}, unit
 * letters are emitted already small-capped because these bypass messages.yml.
 */
public final class Numbers {

    private static final DecimalFormatSymbols ES = new DecimalFormatSymbols(Locale.forLanguageTag("es-ES"));

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##", ES);
    private static final DecimalFormat KG_SMALL = new DecimalFormat("#,##0.000", ES);
    private static final DecimalFormat KG_MID = new DecimalFormat("#,##0.00", ES);
    private static final DecimalFormat KG_LARGE = new DecimalFormat("#,##0.0", ES);
    private static final DecimalFormat PLAIN = new DecimalFormat("#,##0", ES);

    private Numbers() {
    }

    public static String money(double value) {
        return MONEY.format(value);
    }

    public static String plain(long value) {
        return PLAIN.format(value);
    }

    /**
     * {@code 0,340 ᴋɢ} for a sardine, {@code 412,7 ᴋɢ} for a marlin — the precision
     * scales with the size so small fish still read as distinct catches.
     */
    public static String kg(double value) {
        String n;
        if (value < 1.0) {
            n = KG_SMALL.format(value);
        } else if (value < 100.0) {
            n = KG_MID.format(value);
        } else {
            n = KG_LARGE.format(value);
        }
        return n + " ᴋɢ";
    }

    /** The trophy percentage shown in fish lore, e.g. {@code 87%}. */
    public static String percent(double fraction) {
        return Math.round(fraction * 100.0) + "%";
    }
}
