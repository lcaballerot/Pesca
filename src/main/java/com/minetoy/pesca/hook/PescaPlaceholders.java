package com.minetoy.pesca.hook;

import com.minetoy.pesca.PescaPlugin;
import com.minetoy.pesca.storage.model.Score;
import com.minetoy.pesca.text.SmallCaps;
import com.minetoy.pesca.tournament.TournamentManager;
import com.minetoy.pesca.util.Durations;
import com.minetoy.pesca.util.Numbers;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * PlaceholderAPI placeholders, mainly for the FancyHolograms sign at the fishing zone.
 *
 * <p>This class is only ever loaded from inside a {@code PlaceholderAPI != null} guard,
 * so the plugin runs fine without PlaceholderAPI installed.
 */
public final class PescaPlaceholders extends PlaceholderExpansion {

    private final PescaPlugin plugin;
    private final TournamentManager tournament;

    public PescaPlaceholders(PescaPlugin plugin, TournamentManager tournament) {
        this.plugin = plugin;
        this.tournament = tournament;
    }

    @NotNull
    @Override
    public String getIdentifier() {
        return "pesca";
    }

    @NotNull
    @Override
    public String getAuthor() {
        return "MineToy";
    }

    @NotNull
    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        // Survives a PlaceholderAPI reload; Pesca unregisters this itself on disable.
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);

        if (key.equals("estado")) {
            return tournament.isRunning()
                    ? "<#6eff4a>" + small("activo")
                    : "<#c93434>" + small("inactivo");
        }
        if (key.equals("tiempo")) {
            return Durations.format(tournament.remaining());
        }
        if (key.equals("duracion")) {
            return Durations.format(tournament.duration());
        }
        if (key.equals("frecuencia")) {
            return Durations.format(tournament.frequency());
        }
        if (key.equals("participantes")) {
            return String.valueOf(tournament.ranking().size());
        }

        if (key.startsWith("top_")) {
            return topEntry(key.substring("top_".length()));
        }

        if (player == null) {
            return "";
        }
        Score own = tournament.scoreOf(player.getUniqueId());
        return switch (key) {
            case "puntos" -> own == null ? "0" : Numbers.plain(own.points);
            case "capturas" -> own == null ? "0" : Numbers.plain(own.catches);
            case "record" -> own == null ? Numbers.kg(0) : Numbers.kg(own.bestKg);
            case "puesto" -> {
                int position = tournament.positionOf(player.getUniqueId());
                yield position == 0 ? "-" : String.valueOf(position);
            }
            default -> null;
        };
    }

    private String topEntry(String rawPosition) {
        int position;
        try {
            position = Integer.parseInt(rawPosition);
        } catch (NumberFormatException e) {
            return null;
        }
        List<Score> ranking = tournament.ranking();
        if (position < 1 || position > ranking.size()) {
            return "<#6d6d6d>" + small("nadie todavia");
        }
        Score score = ranking.get(position - 1);
        return score.name + " <#6d6d6d>— <#b6d9d8>" + Numbers.plain(score.points) + " " + small("pts");
    }

    private String small(String text) {
        return SmallCaps.apply(text, plugin.config().smallCaps());
    }
}
