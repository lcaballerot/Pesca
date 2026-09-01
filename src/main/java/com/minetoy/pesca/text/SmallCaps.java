package com.minetoy.pesca.text;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts ordinary lowercase Spanish into the Unicode small-capital letters the
 * MineToy chat format is written in, so {@code messages.yml} and {@code config.yml}
 * can stay readable and hand-editable.
 *
 * <p>Three characters have no small-cap form and are passed through as the house
 * style requires: {@code x} stays lowercase, {@code ñ} stays lowercase, and accented
 * vowels are folded to the unaccented small cap (the accent is lost).
 *
 * <p>Four things are never converted, because small-capping them breaks them:
 * <ul>
 *   <li>{@code <...>} — MiniMessage tags, including {@code <pre:pesca>} and placeholders</li>
 *   <li>{@code %...%} — PlaceholderAPI placeholders</li>
 *   <li>{@code /token} — anything a player has to type, up to the next space</li>
 *   <li>{@code `literal`} — an explicit escape; the backticks are stripped</li>
 * </ul>
 */
public final class SmallCaps {

    private static final Map<Character, String> MAP = new HashMap<>();

    static {
        MAP.put('a', "ᴀ"); // ᴀ
        MAP.put('b', "ʙ"); // ʙ
        MAP.put('c', "ᴄ"); // ᴄ
        MAP.put('d', "ᴅ"); // ᴅ
        MAP.put('e', "ᴇ"); // ᴇ
        MAP.put('f', "ꜰ"); // ꜰ
        MAP.put('g', "ɢ"); // ɢ
        MAP.put('h', "ʜ"); // ʜ
        MAP.put('i', "ɪ"); // ɪ
        MAP.put('j', "ᴊ"); // ᴊ
        MAP.put('k', "ᴋ"); // ᴋ
        MAP.put('l', "ʟ"); // ʟ
        MAP.put('m', "ᴍ"); // ᴍ
        MAP.put('n', "ɴ"); // ɴ
        MAP.put('o', "ᴏ"); // ᴏ
        MAP.put('p', "ᴘ"); // ᴘ
        MAP.put('q', "ꞯ"); // ꞯ
        MAP.put('r', "ʀ"); // ʀ
        MAP.put('s', "ꜱ"); // ꜱ
        MAP.put('t', "ᴛ"); // ᴛ
        MAP.put('u', "ᴜ"); // ᴜ
        MAP.put('v', "ᴠ"); // ᴠ
        MAP.put('w', "ᴡ"); // ᴡ
        MAP.put('y', "ʏ"); // ʏ
        MAP.put('z', "ᴢ"); // ᴢ

        // x and ñ deliberately absent — they have no small-cap form.

        // Accents are dropped: the plain small cap is used.
        MAP.put('á', MAP.get('a')); // á
        MAP.put('é', MAP.get('e')); // é
        MAP.put('í', MAP.get('i')); // í
        MAP.put('ó', MAP.get('o')); // ó
        MAP.put('ú', MAP.get('u')); // ú
        MAP.put('ü', MAP.get('u')); // ü
    }

    private SmallCaps() {
    }

    public static String apply(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        int i = 0;
        int len = input.length();

        while (i < len) {
            char c = input.charAt(i);

            // <...> MiniMessage tag — copy verbatim.
            if (c == '<') {
                int close = input.indexOf('>', i);
                if (close >= 0) {
                    out.append(input, i, close + 1);
                    i = close + 1;
                    continue;
                }
            }

            // %...% PlaceholderAPI — copy verbatim.
            if (c == '%') {
                int close = input.indexOf('%', i + 1);
                if (close > i && close - i <= 64) {
                    out.append(input, i, close + 1);
                    i = close + 1;
                    continue;
                }
            }

            // `literal` — copy verbatim, drop the backticks.
            if (c == '`') {
                int close = input.indexOf('`', i + 1);
                if (close > i) {
                    out.append(input, i + 1, close);
                    i = close + 1;
                    continue;
                }
            }

            // /command — copy the whole token verbatim.
            if (c == '/' && (i == 0 || Character.isWhitespace(input.charAt(i - 1)))) {
                int end = i;
                while (end < len && !Character.isWhitespace(input.charAt(end))) {
                    end++;
                }
                out.append(input, i, end);
                i = end;
                continue;
            }

            String mapped = MAP.get(Character.toLowerCase(c));
            out.append(mapped != null ? mapped : String.valueOf(c));
            i++;
        }
        return out.toString();
    }

    /** Applies the conversion only when the feature is switched on in config.yml. */
    public static String apply(String input, boolean enabled) {
        return enabled ? apply(input) : input;
    }
}
