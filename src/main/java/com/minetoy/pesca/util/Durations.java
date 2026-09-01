package com.minetoy.pesca.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Duration parsing for {@code /pesca admin duration|frequency}, and formatting for
 * display.
 *
 * <p>Formatted output is emitted with small-cap unit letters already applied
 * ({@code 2ʜ 30ᴍ}) because these are runtime values injected as unparsed
 * placeholders — they never pass through the messages.yml small-cap conversion.
 */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)([wdhms])");

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long WEEK = 7 * DAY;

    private Durations() {
    }

    /**
     * Parses {@code 1d}, {@code 1w}, {@code 90m}, {@code 1d12h} into milliseconds.
     *
     * @return the duration in ms, or {@code -1} if nothing parseable was found
     */
    public static long parse(String input) {
        if (input == null || input.isBlank()) {
            return -1;
        }
        String s = input.trim().toLowerCase().replace(" ", "");
        Matcher m = PART.matcher(s);
        long total = 0;
        int consumed = 0;

        while (m.find()) {
            consumed += m.group(0).length();
            long value = Long.parseLong(m.group(1));
            total += switch (m.group(2).charAt(0)) {
                case 'w' -> value * WEEK;
                case 'd' -> value * DAY;
                case 'h' -> value * HOUR;
                case 'm' -> value * MINUTE;
                default -> value * SECOND;
            };
        }
        // Every character must belong to a unit, so "1d basura" is rejected rather
        // than silently accepted as 1d.
        if (consumed != s.length() || total <= 0) {
            return -1;
        }
        return total;
    }

    /** {@code 2ʜ 30ᴍ}. Shows at most the two largest non-zero units. */
    public static String format(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long days = millis / DAY;
        long hours = (millis % DAY) / HOUR;
        long minutes = (millis % HOUR) / MINUTE;
        long seconds = (millis % MINUTE) / SECOND;

        StringBuilder sb = new StringBuilder();
        int units = 0;
        if (days > 0) {
            sb.append(days).append("ᴅ");
            units++;
        }
        if (hours > 0 && units < 2) {
            if (units > 0) sb.append(' ');
            sb.append(hours).append("ʜ");
            units++;
        }
        if (minutes > 0 && units < 2) {
            if (units > 0) sb.append(' ');
            sb.append(minutes).append("ᴍ");
            units++;
        }
        if (seconds > 0 && units < 2 && days == 0) {
            if (units > 0) sb.append(' ');
            sb.append(seconds).append("ꜱ");
            units++;
        }
        if (units == 0) {
            sb.append("0ꜱ");
        }
        return sb.toString();
    }

    /** Canonical short form for storing back into config.yml: {@code 2h}, {@code 1d12h}. */
    public static String canonical(long millis) {
        StringBuilder sb = new StringBuilder();
        long rest = Math.max(0, millis);
        long days = rest / DAY;
        rest %= DAY;
        long hours = rest / HOUR;
        rest %= HOUR;
        long minutes = rest / MINUTE;
        rest %= MINUTE;
        long seconds = rest / SECOND;

        if (days > 0) sb.append(days).append('d');
        if (hours > 0) sb.append(hours).append('h');
        if (minutes > 0) sb.append(minutes).append('m');
        if (seconds > 0) sb.append(seconds).append('s');
        return sb.isEmpty() ? "0s" : sb.toString();
    }
}
