package com.minetoy.pesca.command;

import com.minetoy.pesca.PescaPlugin;
import com.minetoy.pesca.area.Area;
import com.minetoy.pesca.area.AreaManager;
import com.minetoy.pesca.config.PescaConfig;
import com.minetoy.pesca.gui.LootEditorGui;
import com.minetoy.pesca.gui.ShopGui;
import com.minetoy.pesca.shop.SellService;
import com.minetoy.pesca.storage.model.Score;
import com.minetoy.pesca.storage.model.TournamentRecord;
import com.minetoy.pesca.text.Msg;
import com.minetoy.pesca.tournament.BroadcastMode;
import com.minetoy.pesca.tournament.TournamentManager;
import com.minetoy.pesca.util.Durations;
import com.minetoy.pesca.util.Numbers;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PescaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT = List.of("top", "shop", "sell");
    private static final List<String> ADMIN = List.of(
            "loot", "duration", "frequency", "forcestop", "forcestart",
            "broadcasts", "setarea", "area", "info", "reload");
    private static final List<String> DURATION_HINTS = List.of("1h", "2h", "6h", "1d", "1w");

    private final PescaPlugin plugin;
    private final PescaConfig config;
    private final Msg msg;
    private final TournamentManager tournament;
    private final AreaManager areas;
    private final SellService sell;

    public PescaCommand(PescaPlugin plugin, PescaConfig config, Msg msg,
                        TournamentManager tournament, AreaManager areas, SellService sell) {
        this.plugin = plugin;
        this.config = config;
        this.msg = msg;
        this.tournament = tournament;
        this.areas = areas;
        this.sell = sell;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "top" -> top(sender);
            case "shop", "tienda" -> shop(sender);
            case "sell", "vender" -> sell(sender, args);
            case "admin" -> admin(sender, args);
            default -> help(sender);
        }
        return true;
    }

    // ---- player commands ---------------------------------------------------

    private void help(CommandSender sender) {
        msg.sendList(sender, "help.player");
        if (sender.hasPermission("pesca.admin")) {
            msg.sendList(sender, "help.admin");
        }
    }

    private void top(CommandSender sender) {
        if (tournament.isRunning()) {
            List<Score> ranking = tournament.ranking();
            msg.sendList(sender, "top.header-live",
                    Msg.p("tiempo", Durations.format(tournament.remaining())));
            printRanking(sender, ranking);
            if (sender instanceof Player player) {
                printOwnPosition(player, ranking);
            }
            return;
        }

        TournamentRecord last = tournament.lastFinished();
        if (last == null) {
            msg.sendList(sender, "top.none",
                    Msg.p("proximo", Durations.format(tournament.remaining())));
            return;
        }
        Map<UUID, Score> scores = tournament.lastFinishedScores();
        List<Score> ranking = new ArrayList<>(scores.values());
        ranking.sort(Comparator.comparingInt((Score s) -> s.points).reversed()
                .thenComparingDouble(s -> -s.bestKg)
                .thenComparing(s -> s.name));

        msg.sendList(sender, "top.header-last",
                Msg.p("proximo", Durations.format(tournament.remaining())));
        printRanking(sender, ranking);
    }

    private void printRanking(CommandSender sender, List<Score> ranking) {
        if (ranking.isEmpty()) {
            msg.send(sender, "top.empty");
            return;
        }
        int limit = Math.min(config.topSize(), ranking.size());
        for (int i = 0; i < limit; i++) {
            Score score = ranking.get(i);
            msg.send(sender, positionKey(i + 1),
                    Msg.p("puesto", String.valueOf(i + 1)),
                    Msg.p("jugador", score.name),
                    Msg.p("puntos", Numbers.plain(score.points)),
                    Msg.p("capturas", Numbers.plain(score.catches)),
                    Msg.p("record", Numbers.kg(score.bestKg)));
        }
    }

    /** The podium gets its own three message keys so it can be coloured differently. */
    private String positionKey(int position) {
        String key = "top.entry-" + position;
        return position <= 3 && msg.has(key) ? key : "top.entry";
    }

    private void printOwnPosition(Player player, List<Score> ranking) {
        int position = tournament.positionOf(player.getUniqueId());
        if (position == 0) {
            msg.send(player, "top.you-unranked");
            return;
        }
        Score own = tournament.scoreOf(player.getUniqueId());
        msg.send(player, "top.you",
                Msg.p("puesto", String.valueOf(position)),
                Msg.p("puntos", Numbers.plain(own.points)),
                Msg.p("capturas", Numbers.plain(own.catches)));
    }

    private void shop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, "error.players-only");
            return;
        }
        if (config.shopItems().isEmpty()) {
            msg.send(player, "shop.empty");
            return;
        }
        player.openInventory(new ShopGui(config, plugin.economy(), sell,
                plugin.specialItems(), msg, player).getInventory());
    }

    private void sell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, "error.players-only");
            return;
        }
        if (args.length < 2) {
            msg.send(player, "sell.usage");
            return;
        }
        if (!sell.requireEconomy(player)) {
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "hand", "mano" -> sell.report(player, sell.sellHand(player));
            case "all", "todo" -> sell.report(player, sell.sellAll(player));
            default -> msg.send(player, "sell.usage");
        }
    }

    // ---- admin -------------------------------------------------------------

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pesca.admin")) {
            msg.send(sender, "error.no-permission");
            return;
        }
        if (args.length < 2) {
            msg.sendList(sender, "help.admin");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "loot" -> adminLoot(sender);
            case "duration" -> adminDuration(sender, args);
            case "frequency" -> adminFrequency(sender, args);
            case "forcestop" -> adminForceStop(sender);
            case "forcestart" -> adminForceStart(sender);
            case "broadcasts" -> adminBroadcasts(sender, args);
            case "setarea" -> adminSetArea(sender);
            case "area" -> adminArea(sender);
            case "info" -> adminInfo(sender);
            case "reload" -> adminReload(sender);
            default -> msg.sendList(sender, "help.admin");
        }
    }

    private void adminLoot(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, "error.players-only");
            return;
        }
        player.openInventory(new LootEditorGui(plugin.loot(), msg).getInventory());
    }

    private void adminDuration(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg.send(sender, "admin.duration-usage",
                    Msg.p("actual", Durations.format(tournament.duration())));
            return;
        }
        long ms = Durations.parse(args[2]);
        if (ms <= 0) {
            msg.send(sender, "error.bad-duration", Msg.p("valor", args[2]));
            return;
        }
        tournament.setDuration(ms);
        msg.send(sender, "admin.duration-set", Msg.p("duracion", Durations.format(ms)));
        if (tournament.isRunning()) {
            msg.send(sender, "admin.duration-applied",
                    Msg.p("tiempo", Durations.format(tournament.remaining())));
        }
    }

    private void adminFrequency(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg.send(sender, "admin.frequency-usage",
                    Msg.p("actual", Durations.format(tournament.frequency())));
            return;
        }
        long ms = Durations.parse(args[2]);
        if (ms <= 0) {
            msg.send(sender, "error.bad-duration", Msg.p("valor", args[2]));
            return;
        }
        tournament.setFrequency(ms);
        msg.send(sender, "admin.frequency-set", Msg.p("frecuencia", Durations.format(ms)));
        if (!tournament.isRunning()) {
            msg.send(sender, "admin.next-in", Msg.p("tiempo", Durations.format(tournament.remaining())));
        }
    }

    private void adminForceStop(CommandSender sender) {
        if (!tournament.forceStop()) {
            msg.send(sender, "admin.not-running");
            return;
        }
        msg.send(sender, "admin.forced-stop",
                Msg.p("tiempo", Durations.format(tournament.remaining())));
    }

    private void adminForceStart(CommandSender sender) {
        if (!areas.isSet()) {
            msg.send(sender, "admin.no-area");
            return;
        }
        if (!tournament.forceStart()) {
            msg.send(sender, "admin.already-running",
                    Msg.p("tiempo", Durations.format(tournament.remaining())));
            return;
        }
        msg.send(sender, "admin.forced-start",
                Msg.p("duracion", Durations.format(tournament.duration())));
        if (plugin.loot().isEmpty()) {
            msg.send(sender, "admin.loot-empty-warning");
        }
    }

    private void adminBroadcasts(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg.send(sender, "admin.broadcasts-usage",
                    Msg.p("actual", tournament.broadcastMode().token()));
            return;
        }
        String raw = args[2].toLowerCase(Locale.ROOT);
        if (!raw.equals("true") && !raw.equals("false") && !raw.equals("debug")) {
            msg.send(sender, "admin.broadcasts-usage",
                    Msg.p("actual", tournament.broadcastMode().token()));
            return;
        }

        BroadcastMode mode = BroadcastMode.parse(raw);
        tournament.setBroadcastMode(mode);

        if (mode != BroadcastMode.DEBUG) {
            msg.send(sender, "admin.broadcasts-" + mode.token());
            return;
        }

        // Debug is per-admin: running it adds you to the audience, running it again removes you.
        if (!(sender instanceof Player player)) {
            msg.send(sender, "error.players-only");
            return;
        }
        boolean watching = tournament.toggleDebugViewer(player.getUniqueId());
        msg.send(player, watching ? "admin.broadcasts-debug-on" : "admin.broadcasts-debug-off",
                Msg.p("total", String.valueOf(tournament.debugViewerCount())));
    }

    private void adminSetArea(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, "error.players-only");
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            msg.send(player, "error.inventory-full");
            return;
        }
        areas.beginSelection(player.getUniqueId());
        player.getInventory().addItem(plugin.wand().create());
        msg.sendList(player, "area.wand-given");
    }

    private void adminArea(CommandSender sender) {
        Area area = areas.area();
        if (area == null) {
            msg.send(sender, "admin.no-area");
            return;
        }
        msg.send(sender, "area.current",
                Msg.p("mundo", area.world()),
                Msg.p("min", area.minX() + ", " + area.minY() + ", " + area.minZ()),
                Msg.p("max", area.maxX() + ", " + area.maxY() + ", " + area.maxZ()),
                Msg.p("bloques", Numbers.plain(area.volume())));
    }

    private void adminInfo(CommandSender sender) {
        Area area = areas.area();
        msg.sendList(sender, "admin.info",
                Msg.p("estado", msg.value(tournament.isRunning() ? "activo" : "inactivo")),
                Msg.p("tiempo", Durations.format(tournament.remaining())),
                Msg.p("duracion", Durations.format(tournament.duration())),
                Msg.p("frecuencia", Durations.format(tournament.frequency())),
                Msg.p("anuncios", tournament.broadcastMode().token()),
                Msg.p("debug", String.valueOf(tournament.debugViewerCount())),
                Msg.p("botin", String.valueOf(plugin.loot().size())),
                Msg.p("peces", String.valueOf(config.fish().size())),
                Msg.p("area", area == null ? msg.value("sin definir") : area.describe()),
                Msg.p("economia", msg.value(plugin.economy().isAvailable() ? "vault" : "sin economia")),
                Msg.p("participantes", String.valueOf(tournament.ranking().size())));
    }

    private void adminReload(CommandSender sender) {
        plugin.reloadEverything();
        msg.send(sender, "admin.reloaded", Msg.p("peces", String.valueOf(config.fish().size())));
    }

    // ---- tab completion ----------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            out.addAll(ROOT);
            if (sender.hasPermission("pesca.admin")) {
                out.add("admin");
            }
            return filter(out, args[0]);
        }

        if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("sell") || first.equals("vender")) {
                return filter(List.of("hand", "all"), args[1]);
            }
            if (first.equals("admin") && sender.hasPermission("pesca.admin")) {
                return filter(ADMIN, args[1]);
            }
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("pesca.admin")) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "duration", "frequency" -> filter(DURATION_HINTS, args[2]);
                case "broadcasts" -> filter(List.of("true", "false", "debug"), args[2]);
                default -> out;
            };
        }
        return out;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
