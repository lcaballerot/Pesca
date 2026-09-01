package com.minetoy.pesca.storage;

import com.minetoy.pesca.storage.model.Score;
import com.minetoy.pesca.storage.model.TournamentRecord;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite store for scores, tournament history and plugin state.
 *
 * <p>Every method here blocks. Callers on the main thread must only use the ones that
 * touch a handful of rows; leaderboard reads and score flushes are pushed onto the
 * plugin's async executor by {@code TournamentManager}.
 */
public final class Database {

    private final JavaPlugin plugin;
    private Connection connection;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new SQLException("no se pudo crear " + folder);
        }
        try {
            // Loaded through plugin.yml `libraries:`; the explicit load keeps the
            // failure legible if that download did not happen.
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("falta el driver de sqlite (revisa `libraries:` en plugin.yml)", e);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + new File(folder, "pesca.db"));
        createTables();
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("error al cerrar pesca.db: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tournaments (
                      id            INTEGER PRIMARY KEY AUTOINCREMENT,
                      started_at    INTEGER NOT NULL,
                      ends_at       INTEGER NOT NULL,
                      finished      INTEGER NOT NULL DEFAULT 0,
                      winner_uuid   TEXT,
                      winner_name   TEXT,
                      winner_points INTEGER NOT NULL DEFAULT 0
                    )""");
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS scores (
                      tournament_id INTEGER NOT NULL,
                      uuid          TEXT NOT NULL,
                      name          TEXT NOT NULL,
                      points        INTEGER NOT NULL DEFAULT 0,
                      catches       INTEGER NOT NULL DEFAULT 0,
                      best_kg       REAL NOT NULL DEFAULT 0,
                      best_fish     TEXT,
                      PRIMARY KEY (tournament_id, uuid)
                    )""");
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS players (
                      uuid          TEXT PRIMARY KEY,
                      name          TEXT NOT NULL,
                      total_points  INTEGER NOT NULL DEFAULT 0,
                      total_catches INTEGER NOT NULL DEFAULT 0,
                      best_kg       REAL NOT NULL DEFAULT 0,
                      best_fish     TEXT,
                      earned        REAL NOT NULL DEFAULT 0,
                      wins          INTEGER NOT NULL DEFAULT 0
                    )""");
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pending_loot (
                      id   INTEGER PRIMARY KEY AUTOINCREMENT,
                      uuid TEXT NOT NULL,
                      item BLOB NOT NULL
                    )""");
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS state (
                      k TEXT PRIMARY KEY,
                      v TEXT NOT NULL
                    )""");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_scores_points ON scores (tournament_id, points DESC)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_loot_uuid ON pending_loot (uuid)");
        }
    }

    // ---- state -------------------------------------------------------------

    public String getState(String key, String fallback) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT v FROM state WHERE k = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : fallback;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("no se pudo leer el estado '" + key + "': " + e.getMessage());
            return fallback;
        }
    }

    public void setState(String key, String value) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO state (k, v) VALUES (?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("no se pudo guardar el estado '" + key + "': " + e.getMessage());
        }
    }

    // ---- tournaments -------------------------------------------------------

    public int createTournament(long startedAt, long endsAt) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tournaments (started_at, ends_at) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, startedAt);
            ps.setLong(2, endsAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    public void finishTournament(int id, UUID winner, String winnerName, int winnerPoints) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE tournaments SET finished = 1, winner_uuid = ?, winner_name = ?, winner_points = ? WHERE id = ?")) {
            ps.setString(1, winner == null ? null : winner.toString());
            ps.setString(2, winnerName);
            ps.setInt(3, winnerPoints);
            ps.setInt(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("no se pudo cerrar el torneo " + id + ": " + e.getMessage());
        }
    }

    public void setTournamentEnd(int id, long endsAt) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE tournaments SET ends_at = ? WHERE id = ?")) {
            ps.setLong(1, endsAt);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("no se pudo actualizar el final del torneo: " + e.getMessage());
        }
    }

    public TournamentRecord lastFinishedTournament() {
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT id, started_at, ends_at, winner_uuid, winner_name, winner_points "
                             + "FROM tournaments WHERE finished = 1 ORDER BY id DESC LIMIT 1")) {
            if (!rs.next()) {
                return null;
            }
            String uuid = rs.getString("winner_uuid");
            return new TournamentRecord(
                    rs.getInt("id"),
                    rs.getLong("started_at"),
                    rs.getLong("ends_at"),
                    uuid == null ? null : UUID.fromString(uuid),
                    rs.getString("winner_name"),
                    rs.getInt("winner_points"));
        } catch (SQLException e) {
            plugin.getLogger().warning("no se pudo leer el ultimo torneo: " + e.getMessage());
            return null;
        }
    }

    // ---- scores ------------------------------------------------------------

    public Map<UUID, Score> loadScores(int tournamentId) {
        Map<UUID, Score> out = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, name, points, catches, best_kg, best_fish FROM scores WHERE tournament_id = ?")) {
            ps.setInt(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("uuid"));
                    Score score = new Score(id, rs.getString("name"));
                    score.points = rs.getInt("points");
                    score.catches = rs.getInt("catches");
                    score.bestKg = rs.getDouble("best_kg");
                    score.bestFish = rs.getString("best_fish");
                    out.put(id, score);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("no se pudieron cargar las puntuaciones: " + e.getMessage());
        }
        return out;
    }

    public void saveScores(int tournamentId, Iterable<Score> scores) {
        String sql = "INSERT INTO scores (tournament_id, uuid, name, points, catches, best_kg, best_fish) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(tournament_id, uuid) DO UPDATE SET "
                + "name = excluded.name, points = excluded.points, catches = excluded.catches, "
                + "best_kg = excluded.best_kg, best_fish = excluded.best_fish";
        boolean autoCommit = true;
        try {
            autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Score s : scores) {
                    ps.setInt(1, tournamentId);
                    ps.setString(2, s.uuid.toString());
                    ps.setString(3, s.name);
                    ps.setInt(4, s.points);
                    ps.setInt(5, s.catches);
                    ps.setDouble(6, s.bestKg);
                    ps.setString(7, s.bestFish);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            plugin.getLogger().warning("no se pudieron guardar las puntuaciones: " + e.getMessage());
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // Nothing useful to do; the next flush will retry.
            }
        } finally {
            try {
                connection.setAutoCommit(autoCommit);
            } catch (SQLException ignored) {
                // As above.
            }
        }
    }

    // ---- lifetime player stats --------------------------------------------

    public void addPlayerStats(UUID uuid, String name, int points, int catches, double kg,
                               String fishId, double earned, int wins) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO players (uuid, name, total_points, total_catches, best_kg, best_fish, earned, wins)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                  name = excluded.name,
                  total_points = total_points + excluded.total_points,
                  total_catches = total_catches + excluded.total_catches,
                  best_kg = MAX(best_kg, excluded.best_kg),
                  best_fish = CASE WHEN excluded.best_kg > best_kg THEN excluded.best_fish ELSE best_fish END,
                  earned = earned + excluded.earned,
                  wins = wins + excluded.wins""")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setInt(3, points);
            ps.setInt(4, catches);
            ps.setDouble(5, kg);
            ps.setString(6, fishId);
            ps.setDouble(7, earned);
            ps.setInt(8, wins);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("no se pudieron guardar las estadisticas de " + name + ": " + e.getMessage());
        }
    }

    // ---- pending loot ------------------------------------------------------

    public void addPendingLoot(UUID uuid, List<byte[]> items) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pending_loot (uuid, item) VALUES (?, ?)")) {
            for (byte[] item : items) {
                ps.setString(1, uuid.toString());
                ps.setBytes(2, item);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().severe("no se pudo guardar el botin pendiente de " + uuid + ": " + e.getMessage());
        }
    }

    public List<byte[]> takePendingLoot(UUID uuid) {
        List<byte[]> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT item FROM pending_loot WHERE uuid = ? ORDER BY id")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getBytes(1));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("no se pudo leer el botin pendiente: " + e.getMessage());
            return List.of();
        }
        if (!out.isEmpty()) {
            try (PreparedStatement del = connection.prepareStatement("DELETE FROM pending_loot WHERE uuid = ?")) {
                del.setString(1, uuid.toString());
                del.executeUpdate();
            } catch (SQLException e) {
                // The loot was read but not cleared; better to log loudly than to
                // hand it out twice on the next join.
                plugin.getLogger().severe("botin entregado pero no borrado de la base de datos para "
                        + uuid + ": " + e.getMessage());
            }
        }
        return out;
    }

    public boolean hasPendingLoot(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM pending_loot WHERE uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }
}
