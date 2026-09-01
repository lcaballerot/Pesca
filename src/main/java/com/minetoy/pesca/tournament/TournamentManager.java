package com.minetoy.pesca.tournament;

import com.minetoy.pesca.PescaPlugin;
import com.minetoy.pesca.config.PescaConfig;
import com.minetoy.pesca.effect.WinnerEffects;
import com.minetoy.pesca.loot.LootStore;
import com.minetoy.pesca.model.Catch;
import com.minetoy.pesca.storage.Database;
import com.minetoy.pesca.storage.model.Score;
import com.minetoy.pesca.storage.model.TournamentRecord;
import com.minetoy.pesca.text.Msg;
import com.minetoy.pesca.util.Durations;
import com.minetoy.pesca.util.Numbers;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the tournament clock, the live scoreboard and the prize handout.
 *
 * <p>The schedule is stored as absolute timestamps rather than countdowns, so a server
 * restart mid-tournament resumes exactly where it left off instead of restarting the
 * clock or skipping the tournament entirely.
 */
public final class TournamentManager {

    private static final String K_DURATION = "duration";
    private static final String K_FREQUENCY = "frequency";
    private static final String K_BROADCASTS = "broadcasts";
    private static final String K_DEBUG_VIEWERS = "debug_viewers";
    private static final String K_ACTIVE_ID = "active_id";
    private static final String K_ACTIVE_STARTED = "active_started";
    private static final String K_ACTIVE_ENDS = "active_ends";
    private static final String K_NEXT_START = "next_start";
    private static final String K_REMINDERS_SENT = "reminders_sent";

    /** How often the in-memory scoreboard is written to SQLite, in ticks. */
    private static final long FLUSH_TICKS = 20L * 30;

    private final PescaPlugin plugin;
    private final PescaConfig config;
    private final Database db;
    private final Msg msg;
    private final LootStore loot;
    private final WinnerEffects effects;

    private long duration;
    private long frequency;
    private BroadcastMode broadcastMode = BroadcastMode.ALL;
    private final Set<UUID> debugViewers = new LinkedHashSet<>();

    private int activeId = -1;
    private long startedAt;
    private long endsAt;
    private long nextStart;

    private final Map<UUID, Score> scores = new HashMap<>();
    private final Set<Long> remindersSent = new HashSet<>();
    private boolean scoresDirty;

    private BukkitTask tickTask;
    private BukkitTask flushTask;

    public TournamentManager(PescaPlugin plugin, PescaConfig config, Database db, Msg msg,
                             LootStore loot, WinnerEffects effects) {
        this.plugin = plugin;
        this.config = config;
        this.db = db;
        this.msg = msg;
        this.loot = loot;
        this.effects = effects;
    }

    // ---- lifecycle ---------------------------------------------------------

    public void load() {
        duration = parseState(K_DURATION, config.defaultDuration());
        frequency = parseState(K_FREQUENCY, config.defaultFrequency());
        broadcastMode = BroadcastMode.parse(db.getState(K_BROADCASTS, config.defaultBroadcasts()));

        debugViewers.clear();
        for (String raw : db.getState(K_DEBUG_VIEWERS, "").split(",")) {
            if (!raw.isBlank()) {
                try {
                    debugViewers.add(UUID.fromString(raw.trim()));
                } catch (IllegalArgumentException ignored) {
                    // A malformed uuid is just dropped from the debug list.
                }
            }
        }

        activeId = (int) parseState(K_ACTIVE_ID, -1);
        startedAt = parseState(K_ACTIVE_STARTED, 0);
        endsAt = parseState(K_ACTIVE_ENDS, 0);
        nextStart = parseState(K_NEXT_START, 0);

        remindersSent.clear();
        for (String raw : db.getState(K_REMINDERS_SENT, "").split(",")) {
            if (!raw.isBlank()) {
                try {
                    remindersSent.add(Long.parseLong(raw.trim()));
                } catch (NumberFormatException ignored) {
                    // As above.
                }
            }
        }

        scores.clear();
        if (activeId > 0) {
            scores.putAll(db.loadScores(activeId));
        }

        // First ever start: schedule one frequency out rather than firing immediately,
        // so installing the plugin does not launch a tournament nobody was told about.
        if (activeId <= 0 && nextStart <= 0) {
            nextStart = System.currentTimeMillis() + frequency;
            db.setState(K_NEXT_START, String.valueOf(nextStart));
        }

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushScores, FLUSH_TICKS, FLUSH_TICKS);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (flushTask != null) {
            flushTask.cancel();
        }
        flushScoresBlocking();
    }

    private long parseState(String key, long fallback) {
        try {
            return Long.parseLong(db.getState(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---- clock -------------------------------------------------------------

    private void tick() {
        long now = System.currentTimeMillis();
        if (isRunning()) {
            if (now >= endsAt) {
                finish(false);
            } else {
                checkReminders(now);
            }
        } else if (nextStart > 0 && now >= nextStart) {
            start();
        }
    }

    private void checkReminders(long now) {
        long remaining = endsAt - now;
        for (long threshold : config.reminders()) {
            if (remindersSent.contains(threshold)) {
                continue;
            }
            // Fire once, as the countdown crosses the threshold from above.
            if (remaining <= threshold && remaining > threshold - 1500) {
                remindersSent.add(threshold);
                persistReminders();
                broadcast(msg.get("tournament.reminder",
                        Msg.p("tiempo", Durations.format(remaining))));
                return;
            }
        }
    }

    private void persistReminders() {
        StringBuilder sb = new StringBuilder();
        for (long l : remindersSent) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(l);
        }
        db.setState(K_REMINDERS_SENT, sb.toString());
    }

    // ---- start / finish ----------------------------------------------------

    public boolean isRunning() {
        return activeId > 0;
    }

    public long endsAt() {
        return endsAt;
    }

    public long nextStart() {
        return nextStart;
    }

    /** Milliseconds left in the tournament, or until the next one starts. */
    public long remaining() {
        long now = System.currentTimeMillis();
        return Math.max(0, (isRunning() ? endsAt : nextStart) - now);
    }

    /** @return false if a tournament is already running */
    public boolean start() {
        if (isRunning()) {
            return false;
        }
        long now = System.currentTimeMillis();
        try {
            activeId = db.createTournament(now, now + duration);
        } catch (SQLException e) {
            plugin.getLogger().severe("no se pudo crear el torneo: " + e.getMessage());
            activeId = -1;
            return false;
        }
        startedAt = now;
        endsAt = now + duration;
        nextStart = 0;
        scores.clear();
        remindersSent.clear();
        scoresDirty = false;

        db.setState(K_ACTIVE_ID, String.valueOf(activeId));
        db.setState(K_ACTIVE_STARTED, String.valueOf(startedAt));
        db.setState(K_ACTIVE_ENDS, String.valueOf(endsAt));
        db.setState(K_NEXT_START, "0");
        db.setState(K_REMINDERS_SENT, "");

        if (loot.isEmpty()) {
            plugin.getLogger().warning(
                    "el torneo ha empezado sin botin configurado — usa /pesca admin loot");
        }

        broadcast(msg.getList("tournament.start",
                Msg.p("duracion", Durations.format(duration))));
        return true;
    }

    /**
     * Ends the running tournament, hands out the prize and schedules the next one.
     *
     * @param forced true when an admin ran {@code /pesca admin forcestop}
     * @return false if nothing was running
     */
    public boolean finish(boolean forced) {
        if (!isRunning()) {
            return false;
        }
        int finishedId = activeId;
        flushScoresBlocking();

        List<Score> ranking = ranking();
        Score winner = ranking.isEmpty() ? null : ranking.get(0);

        db.finishTournament(finishedId,
                winner == null ? null : winner.uuid,
                winner == null ? null : winner.name,
                winner == null ? 0 : winner.points);

        // Reset the clock before announcing, so anything the announcement triggers
        // sees a consistent "not running" state.
        activeId = -1;
        startedAt = 0;
        endsAt = 0;
        nextStart = System.currentTimeMillis() + frequency;
        remindersSent.clear();

        db.setState(K_ACTIVE_ID, "-1");
        db.setState(K_ACTIVE_STARTED, "0");
        db.setState(K_ACTIVE_ENDS, "0");
        db.setState(K_NEXT_START, String.valueOf(nextStart));
        db.setState(K_REMINDERS_SENT, "");

        if (forced) {
            broadcast(msg.get("tournament.forced-stop"));
        }

        if (winner == null) {
            broadcast(msg.getList("tournament.end-nobody",
                    Msg.p("proximo", Durations.format(frequency))));
        } else {
            broadcast(msg.getList("tournament.end-winner",
                    Msg.p("jugador", winner.name),
                    Msg.p("puntos", Numbers.plain(winner.points)),
                    Msg.p("capturas", Numbers.plain(winner.catches)),
                    Msg.p("proximo", Durations.format(frequency))));

            for (int i = 1; i < Math.min(3, ranking.size()); i++) {
                Score s = ranking.get(i);
                broadcast(msg.get("tournament.end-runner-up",
                        Msg.p("puesto", String.valueOf(i + 1)),
                        Msg.p("jugador", s.name),
                        Msg.p("puntos", Numbers.plain(s.points))));
            }

            awardWinner(winner);
        }

        // Lifetime totals are only rolled up once the tournament closes.
        List<Score> snapshot = new ArrayList<>(ranking);
        UUID winnerId = winner == null ? null : winner.uuid;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Score s : snapshot) {
                db.addPlayerStats(s.uuid, s.name, s.points, s.catches, s.bestKg, s.bestFish, 0.0,
                        s.uuid.equals(winnerId) ? 1 : 0);
            }
        });

        scores.clear();
        return true;
    }

    private void awardWinner(Score winner) {
        if (loot.isEmpty()) {
            plugin.getLogger().warning("no habia botin configurado, el ganador no recibe nada");
            return;
        }
        Player online = Bukkit.getPlayer(winner.uuid);
        if (online != null && online.isOnline()) {
            giveLoot(online);
            effects.celebrate(online);
        } else if (config.deliverLootOnJoin()) {
            db.addPendingLoot(winner.uuid, loot.serializedCopies());
        } else {
            plugin.getLogger().warning("el ganador " + winner.name
                    + " estaba desconectado y deliver-loot-on-join esta en false; el botin se pierde");
        }
    }

    /** Gives the configured prize, dropping at the player's feet whatever will not fit. */
    public void giveLoot(Player player) {
        deliver(player, loot.copies());
        msg.send(player, "tournament.loot-given");
    }

    private void deliver(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    /** Called on join: hands over a prize won while the player was offline. */
    public void deliverPendingLoot(Player player) {
        if (!config.deliverLootOnJoin() || !db.hasPendingLoot(player.getUniqueId())) {
            return;
        }
        List<byte[]> raw = db.takePendingLoot(player.getUniqueId());
        if (raw.isEmpty()) {
            return;
        }
        List<ItemStack> items = new ArrayList<>(raw.size());
        for (byte[] bytes : raw) {
            try {
                items.add(ItemStack.deserializeBytes(bytes));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("botin pendiente ilegible para " + player.getName()
                        + ": " + e.getMessage());
            }
        }
        deliver(player, items);
        msg.send(player, "tournament.loot-pending");
        effects.celebrate(player);
    }

    // ---- scoring -----------------------------------------------------------

    /** Records a catch. Only the rarity's points count; the weight is a stat, not a score. */
    public void record(Player player, Catch caught) {
        if (!isRunning()) {
            return;
        }
        Score score = scores.computeIfAbsent(player.getUniqueId(),
                id -> new Score(id, player.getName()));
        score.name = player.getName();
        score.points += caught.points();
        score.catches++;
        if (caught.kg() > score.bestKg) {
            score.bestKg = caught.kg();
            score.bestFish = caught.fish().id();
        }
        scoresDirty = true;
    }

    public Score scoreOf(UUID uuid) {
        return scores.get(uuid);
    }

    public List<Score> ranking() {
        List<Score> list = new ArrayList<>(scores.values());
        list.sort(Comparator
                .comparingInt((Score s) -> s.points).reversed()
                .thenComparingDouble(s -> -s.bestKg)
                .thenComparing(s -> s.name));
        return list;
    }

    /** 1-based position of a player in the live ranking, or 0 if unranked. */
    public int positionOf(UUID uuid) {
        List<Score> list = ranking();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid.equals(uuid)) {
                return i + 1;
            }
        }
        return 0;
    }

    public TournamentRecord lastFinished() {
        return db.lastFinishedTournament();
    }

    public Map<UUID, Score> lastFinishedScores() {
        TournamentRecord last = db.lastFinishedTournament();
        return last == null ? Map.of() : db.loadScores(last.id());
    }

    private void flushScores() {
        if (!scoresDirty || activeId <= 0) {
            return;
        }
        int id = activeId;
        List<Score> snapshot = new ArrayList<>(scores.values());
        scoresDirty = false;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> db.saveScores(id, snapshot));
    }

    private void flushScoresBlocking() {
        if (activeId > 0 && !scores.isEmpty()) {
            db.saveScores(activeId, new ArrayList<>(scores.values()));
            scoresDirty = false;
        }
    }

    // ---- settings ----------------------------------------------------------

    public long duration() {
        return duration;
    }

    /** Changing the duration mid-tournament moves the current end time with it. */
    public void setDuration(long millis) {
        this.duration = millis;
        db.setState(K_DURATION, String.valueOf(millis));
        if (isRunning()) {
            endsAt = startedAt + millis;
            db.setState(K_ACTIVE_ENDS, String.valueOf(endsAt));
            db.setTournamentEnd(activeId, endsAt);
            remindersSent.clear();
            db.setState(K_REMINDERS_SENT, "");
        }
    }

    public long frequency() {
        return frequency;
    }

    /** Changing the frequency while idle re-bases the pending countdown onto the new value. */
    public void setFrequency(long millis) {
        long old = frequency;
        this.frequency = millis;
        db.setState(K_FREQUENCY, String.valueOf(millis));
        if (!isRunning() && nextStart > 0) {
            long previousBase = nextStart - old;
            nextStart = previousBase + millis;
            long now = System.currentTimeMillis();
            if (nextStart < now) {
                nextStart = now;
            }
            db.setState(K_NEXT_START, String.valueOf(nextStart));
        }
    }

    /** {@code /pesca admin forcestop} — end now and restart the frequency countdown. */
    public boolean forceStop() {
        return finish(true);
    }

    /** {@code /pesca admin forcestart} — start now, for the configured duration. */
    public boolean forceStart() {
        return start();
    }

    // ---- broadcasts --------------------------------------------------------

    public BroadcastMode broadcastMode() {
        return broadcastMode;
    }

    public void setBroadcastMode(BroadcastMode mode) {
        this.broadcastMode = mode;
        db.setState(K_BROADCASTS, mode.token());
    }

    /** @return true if the player is now receiving debug announcements */
    public boolean toggleDebugViewer(UUID uuid) {
        boolean added;
        if (debugViewers.contains(uuid)) {
            debugViewers.remove(uuid);
            added = false;
        } else {
            debugViewers.add(uuid);
            added = true;
        }
        StringBuilder sb = new StringBuilder();
        for (UUID id : debugViewers) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(id);
        }
        db.setState(K_DEBUG_VIEWERS, sb.toString());
        return added;
    }

    public boolean isDebugViewer(UUID uuid) {
        return debugViewers.contains(uuid);
    }

    public int debugViewerCount() {
        return debugViewers.size();
    }

    public void broadcast(Component message) {
        switch (broadcastMode) {
            case NONE -> {
                // Silent by design.
            }
            case ALL -> {
                Bukkit.getServer().sendMessage(message);
            }
            case DEBUG -> {
                for (UUID id : debugViewers) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) {
                        p.sendMessage(message);
                    }
                }
                Bukkit.getConsoleSender().sendMessage(message);
            }
        }
    }

    public void broadcast(List<Component> lines) {
        for (Component line : lines) {
            broadcast(line);
        }
    }

    /** Whether this sender would see a broadcast right now — used by /pesca admin info. */
    public boolean wouldSee(CommandSender sender) {
        return switch (broadcastMode) {
            case ALL -> true;
            case NONE -> false;
            case DEBUG -> sender instanceof Player p && debugViewers.contains(p.getUniqueId());
        };
    }

    /** Resolves a stored winner uuid to a name for display. */
    public String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        String name = off.getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    /** Only used by the loot editor, to keep Base64 handling in one place. */
    public static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
