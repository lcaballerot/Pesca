package com.minetoy.pesca.shop;

import com.minetoy.pesca.config.PescaConfig;
import com.minetoy.pesca.fish.FishFactory;
import com.minetoy.pesca.hook.EconomyHook;
import com.minetoy.pesca.model.Catch;
import com.minetoy.pesca.storage.Database;
import com.minetoy.pesca.text.Msg;
import com.minetoy.pesca.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Selling Pesca fish. Vanilla fish and every other item are left untouched, so
 * {@code /pesca sell all} can never eat something a player did not mean to sell.
 */
public final class SellService {

    /** What a sell attempt did, so callers can pick the right message. */
    public record Result(int sold, double total) {
        public boolean isEmpty() {
            return sold == 0;
        }
    }

    private final JavaPlugin plugin;
    private final PescaConfig config;
    private final FishFactory factory;
    private final EconomyHook economy;
    private final Database db;
    private final Msg msg;

    public SellService(JavaPlugin plugin, PescaConfig config, FishFactory factory,
                       EconomyHook economy, Database db, Msg msg) {
        this.plugin = plugin;
        this.config = config;
        this.factory = factory;
        this.economy = economy;
        this.db = db;
        this.msg = msg;
    }

    public boolean requireEconomy(Player player) {
        if (economy.isAvailable()) {
            return true;
        }
        msg.send(player, "error.no-economy");
        return false;
    }

    public Result sellHand(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack hand = inv.getItemInMainHand();
        Catch caught = factory.read(hand);
        if (caught == null) {
            return new Result(0, 0);
        }
        int amount = hand.getAmount();
        double total = caught.price() * amount;

        inv.setItemInMainHand(null);
        pay(player, total);
        return new Result(amount, total);
    }

    public Result sellAll(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();

        int sold = 0;
        double total = 0;
        for (int i = 0; i < contents.length; i++) {
            Catch caught = factory.read(contents[i]);
            if (caught == null) {
                continue;
            }
            int amount = contents[i].getAmount();
            sold += amount;
            total += caught.price() * amount;
            contents[i] = null;
        }
        if (sold == 0) {
            return new Result(0, 0);
        }
        inv.setStorageContents(contents);
        pay(player, total);
        return new Result(sold, total);
    }

    private void pay(Player player, double total) {
        economy.deposit(player, total);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                db.addPlayerStats(player.getUniqueId(), player.getName(), 0, 0, 0, null, total, 0));
    }

    /** Sends the right feedback line for a result. */
    public void report(Player player, Result result) {
        if (result.isEmpty()) {
            msg.send(player, "sell.nothing");
            return;
        }
        msg.send(player, "sell.done",
                Msg.p("cantidad", Numbers.plain(result.sold())),
                Msg.p("total", config.currencySymbol() + Numbers.money(result.total())));
    }
}
