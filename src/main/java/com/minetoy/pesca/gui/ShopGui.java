package com.minetoy.pesca.gui;

import com.minetoy.pesca.config.PescaConfig;
import com.minetoy.pesca.hook.EconomyHook;
import com.minetoy.pesca.model.ShopItem;
import com.minetoy.pesca.shop.SellService;
import com.minetoy.pesca.shop.SpecialItems;
import com.minetoy.pesca.text.Msg;
import com.minetoy.pesca.util.Numbers;
import com.minetoy.pesca.util.Roman;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code /pesca shop} window.
 *
 * <p>Vanilla enchantment and attribute tooltips are hidden and redrawn from the
 * config's {@code enchant-names}, so an item never shows its enchantments twice — once
 * in English from Minecraft and once in Spanish from the lore.
 */
public final class ShopGui implements PescaGui {

    private final Inventory inventory;
    private final PescaConfig config;
    private final EconomyHook economy;
    private final SellService sell;
    private final SpecialItems special;
    private final Msg msg;
    private final Player viewer;

    private final Map<Integer, ShopItem> bySlot = new HashMap<>();

    public ShopGui(PescaConfig config, EconomyHook economy, SellService sell,
                   SpecialItems special, Msg msg, Player viewer) {
        this.config = config;
        this.economy = economy;
        this.sell = sell;
        this.special = special;
        this.msg = msg;
        this.viewer = viewer;

        int size = config.shopRows() * 9;
        this.inventory = Bukkit.createInventory(this, size, msg.parse(config.shopTitle()));

        fillBackground(size);

        for (ShopItem item : config.shopItems().values()) {
            bySlot.put(item.slot(), item);
            inventory.setItem(item.slot(), render(item));
        }
        if (config.balanceSlot() >= 0) {
            inventory.setItem(config.balanceSlot(), balancePanel());
        }
        if (config.sellAllSlot() >= 0) {
            inventory.setItem(config.sellAllSlot(), button(Material.GOLD_INGOT, "gui.sell-all"));
        }
    }

    private void fillBackground(int size) {
        if (config.shopFiller() == Material.AIR) {
            return;
        }
        ItemStack filler = new ItemStack(config.shopFiller());
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        meta.addItemFlags(ItemFlag.values());
        filler.setItemMeta(meta);
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, filler);
        }
    }

    private ItemStack balancePanel() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(msg.get("gui.balance-name").decoration(TextDecoration.ITALIC, false));
        meta.lore(noItalic(msg.getList("gui.balance-lore",
                Msg.p("saldo", config.currencySymbol() + Numbers.money(economy.balance(viewer))))));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String key) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(msg.get(key + "-name").decoration(TextDecoration.ITALIC, false));
        meta.lore(noItalic(msg.getList(key + "-lore")));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** The shop display: the real item plus generated enchantment, effect and price lore. */
    private ItemStack render(ShopItem shopItem) {
        ItemStack item = build(shopItem);
        ItemMeta meta = item.getItemMeta();

        List<Component> lore = new ArrayList<>();

        for (String line : shopItem.lore()) {
            lore.add(msg.parse(line));
        }

        List<Component> enchantLines = enchantLore(shopItem);
        if (!enchantLines.isEmpty()) {
            lore.add(Component.empty());
            lore.addAll(enchantLines);
        }

        if (shopItem.bait()) {
            lore.add(Component.empty());
            lore.addAll(msg.getList("gui.bait-lore",
                    Msg.p("probabilidad", "1/" + config.baitChanceOneIn()),
                    Msg.p("bonus", Numbers.percent(config.baitWeightBonus())),
                    Msg.p("escalones", String.valueOf(config.baitRarityBump()))));
        }

        if (shopItem.repair()) {
            lore.add(Component.empty());
            lore.addAll(msg.getList("gui.repair-lore",
                    Msg.p("porcentaje", Numbers.percent(config.repairPercent()))));
        }

        lore.add(Component.empty());
        lore.addAll(msg.getList("gui.buy-lore",
                Msg.p("precio", config.currencySymbol() + Numbers.money(shopItem.price())),
                Msg.p("cantidad", String.valueOf(shopItem.amount()))));

        meta.lore(noItalic(lore));
        item.setItemMeta(meta);
        return item;
    }

    /** Spanish enchantment lines, replacing the hidden vanilla ones. */
    private List<Component> enchantLore(ShopItem shopItem) {
        List<Component> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> e : shopItem.enchants().entrySet()) {
            Enchantment ench = enchantment(e.getKey());
            if (ench == null) {
                continue;
            }
            // A single-level enchantment (mending) reads better with no numeral at all.
            String level = ench.getMaxLevel() <= 1 ? "" : " " + Roman.of(e.getValue());
            lines.add(msg.get("gui.enchant-line",
                    Msg.p("encantamiento", msg.value(config.enchantName(e.getKey())) + msg.value(level))));
        }
        return lines;
    }

    /** The item exactly as the player receives it — no shop lore, no price line. */
    private ItemStack build(ShopItem shopItem) {
        ItemStack item = new ItemStack(shopItem.material(), shopItem.amount());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(msg.parse(shopItem.name()).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>(noItalic(msg.parseAll(shopItem.lore())));
        List<Component> enchantLines = enchantLore(shopItem);
        if (!enchantLines.isEmpty()) {
            lore.add(Component.empty());
            lore.addAll(noItalic(enchantLines));
        }
        if (shopItem.bait()) {
            lore.add(Component.empty());
            lore.addAll(noItalic(msg.getList("gui.bait-lore",
                    Msg.p("probabilidad", "1/" + config.baitChanceOneIn()),
                    Msg.p("bonus", Numbers.percent(config.baitWeightBonus())),
                    Msg.p("escalones", String.valueOf(config.baitRarityBump())))));
        }
        if (shopItem.repair()) {
            lore.add(Component.empty());
            lore.addAll(noItalic(msg.getList("gui.repair-lore",
                    Msg.p("porcentaje", Numbers.percent(config.repairPercent())))));
        }
        meta.lore(lore);

        // Minecraft would otherwise print the enchantments again, in English.
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        if (shopItem.bait()) {
            special.tagBait(meta);
        }
        if (shopItem.repair()) {
            special.tagRepair(meta);
        }

        item.setItemMeta(meta);

        for (Map.Entry<String, Integer> e : shopItem.enchants().entrySet()) {
            Enchantment ench = enchantment(e.getKey());
            if (ench != null) {
                item.addUnsafeEnchantment(ench, e.getValue());
            }
        }
        return item;
    }

    private Enchantment enchantment(String key) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
    }

    private List<Component> noItalic(List<Component> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (Component line : lines) {
            out.add(line.decoration(TextDecoration.ITALIC, false));
        }
        return out;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public boolean handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return true;
        }
        if (!inventory.equals(event.getClickedInventory())) {
            return true;
        }
        int slot = event.getSlot();

        if (slot == config.sellAllSlot()) {
            if (sell.requireEconomy(player)) {
                sell.report(player, sell.sellAll(player));
                refreshBalance();
            }
            return true;
        }

        ShopItem shopItem = bySlot.get(slot);
        if (shopItem != null) {
            buy(player, shopItem);
        }
        return true;
    }

    private void buy(Player player, ShopItem shopItem) {
        if (!sell.requireEconomy(player)) {
            return;
        }
        if (!economy.has(player, shopItem.price())) {
            msg.send(player, "shop.no-money",
                    Msg.p("precio", config.currencySymbol() + Numbers.money(shopItem.price())),
                    Msg.p("saldo", config.currencySymbol() + Numbers.money(economy.balance(player))));
            player.playSound(Sound.sound(Key.key("minecraft:entity.villager.no"), Sound.Source.MASTER, 1f, 1f));
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            msg.send(player, "shop.full");
            return;
        }
        if (!economy.withdraw(player, shopItem.price())) {
            msg.send(player, "error.transaction");
            return;
        }
        player.getInventory().addItem(build(shopItem));
        msg.send(player, "shop.bought",
                Msg.c("objeto", msg.parse(shopItem.name())),
                Msg.p("precio", config.currencySymbol() + Numbers.money(shopItem.price())));
        player.playSound(Sound.sound(
                Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.MASTER, 1f, 1.4f));

        refreshBalance();
    }

    private void refreshBalance() {
        if (config.balanceSlot() >= 0) {
            inventory.setItem(config.balanceSlot(), balancePanel());
        }
    }
}
