package com.minetoy.pesca.tournament;

import java.util.Locale;

/** Who sees the tournament announcements. */
public enum BroadcastMode {

    /** Everyone online, plus the console. */
    ALL,
    /** Nobody. The tournament still runs, silently. */
    NONE,
    /** Only the admins who switched debug on for themselves. */
    DEBUG;

    public static BroadcastMode parse(String raw) {
        if (raw == null) {
            return ALL;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "all", "si", "yes", "on" -> ALL;
            case "false", "none", "no", "off" -> NONE;
            case "debug" -> DEBUG;
            default -> ALL;
        };
    }

    /** The token used in config.yml and by {@code /pesca admin broadcasts}. */
    public String token() {
        return switch (this) {
            case ALL -> "true";
            case NONE -> "false";
            case DEBUG -> "debug";
        };
    }
}
