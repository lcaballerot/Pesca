package com.minetoy.pesca.hook;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Vault economy, resolved lazily.
 *
 * <p>The lookup runs on first use rather than only at enable, because under Plugman a
 * hot-reload of Pesca can happen before or after Vault's provider is registered.
 */
public final class EconomyHook {

    private final JavaPlugin plugin;
    private Economy economy;
    private boolean checked;

    public EconomyHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Forces a fresh lookup — called on enable and on /pesca admin reload. */
    public void resolve() {
        checked = false;
        economy = null;
        economy();
    }

    public Economy economy() {
        if (checked) {
            return economy;
        }
        checked = true;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault no esta instalado: la tienda y la venta quedan desactivadas");
            return null;
        }
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning(
                    "Vault esta instalado pero ningun plugin de economia se ha registrado");
            return null;
        }
        economy = rsp.getProvider();
        plugin.getLogger().info("economia enlazada a " + economy.getName());
        return economy;
    }

    public boolean isAvailable() {
        return economy() != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        Economy eco = economy();
        return eco != null && eco.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        Economy eco = economy();
        if (eco == null) {
            return false;
        }
        EconomyResponse response = eco.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        Economy eco = economy();
        if (eco == null) {
            return false;
        }
        EconomyResponse response = eco.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            plugin.getLogger().warning("no se pudo pagar a " + player.getName()
                    + ": " + response.errorMessage);
            return false;
        }
        return true;
    }

    public double balance(OfflinePlayer player) {
        Economy eco = economy();
        return eco == null ? 0 : eco.getBalance(player);
    }
}
