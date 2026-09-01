package com.minetoy.pesca;

import com.minetoy.pesca.area.AreaManager;
import com.minetoy.pesca.area.Wand;
import com.minetoy.pesca.command.PescaCommand;
import com.minetoy.pesca.config.PescaConfig;
import com.minetoy.pesca.effect.WinnerEffects;
import com.minetoy.pesca.fish.FishFactory;
import com.minetoy.pesca.gui.PescaGui;
import com.minetoy.pesca.hook.EconomyHook;
import com.minetoy.pesca.hook.PescaPlaceholders;
import com.minetoy.pesca.listener.FishingListener;
import com.minetoy.pesca.listener.GuiListener;
import com.minetoy.pesca.listener.PlayerListener;
import com.minetoy.pesca.listener.RepairListener;
import com.minetoy.pesca.listener.SelectionListener;
import com.minetoy.pesca.loot.LootStore;
import com.minetoy.pesca.shop.BaitService;
import com.minetoy.pesca.shop.SellService;
import com.minetoy.pesca.shop.SpecialItems;
import com.minetoy.pesca.storage.Database;
import com.minetoy.pesca.text.Msg;
import com.minetoy.pesca.tournament.TournamentManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point.
 *
 * <p>Everything is created in {@code onEnable} and torn down in {@code onDisable} —
 * no static state, no listeners or tasks left behind — so the plugin can be unloaded
 * and reloaded with Plugman without leaking a classloader or duplicating handlers.
 */
public final class PescaPlugin extends JavaPlugin {

    private PescaConfig config;
    private Msg msg;
    private Database database;
    private LootStore loot;
    private AreaManager areas;
    private Wand wand;
    private FishFactory factory;
    private EconomyHook economy;
    private WinnerEffects effects;
    private TournamentManager tournament;
    private SellService sell;
    private SpecialItems specialItems;
    private BaitService bait;

    private PescaPlaceholders placeholders;

    @Override
    public void onEnable() {
        config = new PescaConfig(this);
        config.reload();

        msg = new Msg(this);
        msg.reload(config.smallCaps());

        database = new Database(this);
        try {
            database.connect();
        } catch (SQLException e) {
            getLogger().severe("no se pudo abrir pesca.db: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loot = new LootStore(this, database);
        loot.load();

        areas = new AreaManager(this);
        areas.load();

        wand = new Wand(this, msg);
        factory = new FishFactory(this, config, msg);
        economy = new EconomyHook(this);
        economy.resolve();
        effects = new WinnerEffects(this);

        tournament = new TournamentManager(this, config, database, msg, loot, effects);
        tournament.load();

        sell = new SellService(this, config, factory, economy, database, msg);
        specialItems = new SpecialItems(this);
        bait = new BaitService(config, specialItems);

        registerListeners();
        registerCommand();
        registerPlaceholders();

        if (!areas.isSet()) {
            getLogger().warning("no hay area de pesca definida — usa /pesca admin setarea. "
                    + "Hasta entonces toda la pesca del servidor sigue siendo vanilla.");
        }
        getLogger().info("Pesca listo: " + config.fish().size() + " peces, "
                + config.rarities().size() + " rarezas.");
    }

    @Override
    public void onDisable() {
        // Close our GUIs while the listeners are still registered, so the loot editor
        // gets its chance to save. Iterate a copy: closing modifies the viewer list.
        closeOpenGuis();

        if (placeholders != null) {
            placeholders.unregister();
            placeholders = null;
        }

        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);

        if (tournament != null) {
            tournament.shutdown();
        }
        if (areas != null) {
            areas.save();
        }
        if (database != null) {
            database.close();
        }

        PluginCommand command = getCommand("pesca");
        if (command != null) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    private void closeOpenGuis() {
        List<Player> toClose = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof PescaGui) {
                toClose.add(player);
            }
        }
        for (Player player : toClose) {
            player.closeInventory();
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new FishingListener(config, areas, factory, tournament, bait, msg), this);
        getServer().getPluginManager().registerEvents(
                new RepairListener(config, specialItems, msg), this);
        getServer().getPluginManager().registerEvents(
                new SelectionListener(areas, wand, msg), this);
        getServer().getPluginManager().registerEvents(
                new PlayerListener(this, tournament, areas, effects), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
    }

    private void registerCommand() {
        PescaCommand executor = new PescaCommand(this, config, msg, tournament, areas, sell);
        PluginCommand command = getCommand("pesca");
        if (command == null) {
            getLogger().severe("el comando /pesca no esta declarado en plugin.yml");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            placeholders = new PescaPlaceholders(this, tournament);
            placeholders.register();
            getLogger().info("placeholders de PlaceholderAPI registrados como %pesca_...%");
        } catch (Throwable t) {
            // A PlaceholderAPI version mismatch must not take the whole plugin down.
            getLogger().warning("no se pudieron registrar los placeholders: " + t.getMessage());
            placeholders = null;
        }
    }

    /** {@code /pesca admin reload} — config.yml, messages.yml and area.yml, without a restart. */
    public void reloadEverything() {
        config.reload();
        msg.reload(config.smallCaps());
        areas.load();
        loot.load();
        economy.resolve();
    }

    // ---- accessors ---------------------------------------------------------

    public PescaConfig config() {
        return config;
    }

    public Msg messages() {
        return msg;
    }

    public Database database() {
        return database;
    }

    public LootStore loot() {
        return loot;
    }

    public AreaManager areas() {
        return areas;
    }

    public Wand wand() {
        return wand;
    }

    public FishFactory factory() {
        return factory;
    }

    public EconomyHook economy() {
        return economy;
    }

    public TournamentManager tournament() {
        return tournament;
    }

    public SellService sell() {
        return sell;
    }

    public SpecialItems specialItems() {
        return specialItems;
    }

    public BaitService bait() {
        return bait;
    }
}
