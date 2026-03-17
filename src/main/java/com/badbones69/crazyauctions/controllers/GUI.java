package com.badbones69.crazyauctions.controllers;

import com.badbones69.crazyauctions.Methods;
import com.badbones69.crazyauctions.api.*;
import com.badbones69.crazyauctions.api.FileManager.Files;
import com.badbones69.crazyauctions.api.enums.CancelledReason;
import com.badbones69.crazyauctions.api.events.AuctionBuyEvent;
import com.badbones69.crazyauctions.api.events.AuctionCancelledEvent;
import com.badbones69.crazyauctions.api.events.AuctionNewBidEvent;
import com.badbones69.crazyauctions.currency.CurrencyManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.scheduler.BukkitTask;

public class GUI implements Listener {

    private static HashMap<Player, Integer> bidding = new HashMap<>();
    private static HashMap<Player, String> biddingID = new HashMap<>();
    private static HashMap<Player, ShopType> shopType = new HashMap<>(); // Shop Type
    private static HashMap<Player, Category> shopCategory = new HashMap<>(); // Category Type
    private static HashMap<Player, Map<Integer, Integer>> List = new HashMap<>();
    private static HashMap<Player, String> IDs = new HashMap<>();
    private static CrazyAuctions crazyAuctions = CrazyAuctions.getInstance();
    private static Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin("CrazyAuctions");

    private static final HashMap<UUID, BukkitTask> activeAnimations = new HashMap<>();

    private static boolean isBase64Texture(String value) {
        if (value == null || value.length() < 20) return false;
        try {
            String decoded = new String(Base64.getDecoder().decode(value));
            return decoded.contains("textures");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static ItemStack buildSkullFromBase64(String base64, int amount, String name) {
        return buildSkullFromBase64(base64, amount, name, null);
    }

    @SuppressWarnings("unchecked")
    private static ItemStack buildSkullFromBase64(String base64, int amount, String name, java.util.List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, amount);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            try {
                Class<?> gpClass   = Class.forName("com.mojang.authlib.GameProfile");
                Class<?> propClass = Class.forName("com.mojang.authlib.properties.Property");
                java.lang.reflect.Constructor<?> gpCtor   = gpClass.getConstructor(UUID.class, String.class);
                java.lang.reflect.Constructor<?> propCtor = propClass.getConstructor(String.class, String.class);
                Object profile  = gpCtor.newInstance(UUID.nameUUIDFromBytes(base64.getBytes()), "CustomSkull");
                Object property = propCtor.newInstance("textures", base64);
                Object propMap  = gpClass.getMethod("getProperties").invoke(profile);
                propMap.getClass().getMethod("put", Object.class, Object.class).invoke(propMap, "textures", property);
                Field pf = meta.getClass().getDeclaredField("profile");
                pf.setAccessible(true);
                pf.set(meta, profile);
            } catch (Exception ex) {
                Bukkit.getLogger().log(Level.WARNING, "[CrazyAuctions] Не удалось получить base64 текстуру.", ex);
            }
            if (name != null && !name.isEmpty()) meta.setDisplayName(Methods.color(name));
            if (lore != null && !lore.isEmpty()) {
                java.util.List<String> colored = new ArrayList<>();
                for (String line : lore) colored.add(Methods.color(line));
                meta.setLore(colored);
            }
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private static ItemStack makeItemSafe(String id, int amount, String name) {
        if (isBase64Texture(id)) return buildSkullFromBase64(id, amount, name);
        return Methods.makeItem(id, amount, name);
    }

    private static ItemStack makeItemSafe(String id, int amount, String name, java.util.List<String> lore) {
        if (isBase64Texture(id)) return buildSkullFromBase64(id, amount, name, lore);
        return Methods.makeItem(id, amount, name, lore);
    }

    private static java.util.List<Integer> getDisplaySlots(FileConfiguration config) {
        if (config.contains("Settings.GUISettings.DisplaySlots")) {
            java.util.List<Integer> slots = config.getIntegerList("Settings.GUISettings.DisplaySlots");
            if (!slots.isEmpty()) return slots;
        }
        java.util.List<Integer> all = new ArrayList<>();
        for (int i = 0; i < 54; i++) all.add(i);
        return all;
    }

    private static void fillEmptySlots(Inventory inv, FileConfiguration config) {
        String basePath = "Settings.GUISettings";
        if (!config.contains(basePath)) return;

        org.bukkit.configuration.ConfigurationSection guiSection = config.getConfigurationSection(basePath);
        if (guiSection == null) return;

        for (String key : guiSection.getKeys(false)) {
            if (!key.startsWith("Filler_")) continue;

            String path = basePath + "." + key;
            String fillerId   = config.getString(path + ".Item", "BLACK_STAINED_GLASS_PANE");
            String fillerName = config.getString(path + ".Name", " ");
            java.util.List<String> fillerLore = config.getStringList(path + ".Lore");
            java.util.List<Integer> slots = config.getIntegerList(path + ".Slots");

            ItemStack filler = fillerLore.isEmpty()
                    ? makeItemSafe(fillerId, 1, fillerName)
                    : makeItemSafe(fillerId, 1, fillerName, fillerLore);

            for (int slot : slots) {
                if (slot >= 0 && slot < inv.getSize() && inv.getItem(slot) == null) {
                    inv.setItem(slot, filler);
                }
            }
        }
    }
    private static int getMaxPageCustom(int itemCount, int pageSize) {
        if (itemCount == 0 || pageSize == 0) return 1;
        return (int) Math.ceil((double) itemCount / pageSize);
    }

    private static <T> java.util.List<T> getPageCustom(java.util.List<T> list, int page, int pageSize) {
        if (list.isEmpty()) return new ArrayList<>();
        int from = (page - 1) * pageSize;
        if (from >= list.size()) return new ArrayList<>();
        int to = Math.min(from + pageSize, list.size());
        return new ArrayList<>(list.subList(from, to));
    }

    private static void cancelAnimation(UUID uuid) {
        BukkitTask task = activeAnimations.remove(uuid);
        if (task != null) task.cancel();
    }

    private static java.util.List<java.util.List<Integer>> buildFrames(String type, Set<Integer> slots, int rows) {
        java.util.List<java.util.List<Integer>> frames = new ArrayList<>();
        switch (type) {
            case "RIGHT":
                for (int col = 0; col < 9; col++) {
                    java.util.List<Integer> f = new ArrayList<>();
                    for (int s : slots) if (s % 9 == col) f.add(s);
                    if (!f.isEmpty()) frames.add(f);
                }
                break;
            case "LEFT":
                for (int col = 8; col >= 0; col--) {
                    java.util.List<Integer> f = new ArrayList<>();
                    for (int s : slots) if (s % 9 == col) f.add(s);
                    if (!f.isEmpty()) frames.add(f);
                }
                break;
            case "TOP_DOWN":
                for (int row = 0; row < rows; row++) {
                    java.util.List<Integer> f = new ArrayList<>();
                    for (int s : slots) if (s / 9 == row) f.add(s);
                    if (!f.isEmpty()) frames.add(f);
                }
                break;
            case "BOTTOM_UP":
                for (int row = rows - 1; row >= 0; row--) {
                    java.util.List<Integer> f = new ArrayList<>();
                    for (int s : slots) if (s / 9 == row) f.add(s);
                    if (!f.isEmpty()) frames.add(f);
                }
                break;
            case "CENTER":
            default:
                int l = 0, r = 8;
                while (l <= r) {
                    java.util.List<Integer> f = new ArrayList<>();
                    for (int s : slots) { int c = s % 9; if (c == l || c == r) f.add(s); }
                    if (!f.isEmpty()) frames.add(f);
                    l++; r--;
                }
                break;
        }
        return frames;
    }

    private static void openWithAnimation(Player player, Inventory inv, Map<Integer, ItemStack> targetSlots,
                                          FileConfiguration config) {
        cancelAnimation(player.getUniqueId());

        boolean enabled = config.getBoolean("Settings.Animation.Enabled", false);

        player.openInventory(inv);

        if (!enabled || targetSlots.isEmpty()) {
            targetSlots.forEach(inv::setItem);
            return;
        }

        String type = config.getString("Settings.Animation.Type", "CENTER").toUpperCase();
        if (type.equals("RANDOM")) {
            String[] types = {"CENTER", "RIGHT", "LEFT", "TOP_DOWN", "BOTTOM_UP"};
            type = types[new Random().nextInt(types.length)];
        }
        int delay = config.getInt("Settings.Animation.DelayTicks", 2);
        int rows = inv.getSize() / 9;

        // Place filler in every animated slot
        String fillerMat  = config.getString("Settings.Animation.FillerMaterial", "AIR");
        String fillerName = config.getString("Settings.Animation.FillerName", "");
        ItemStack filler = null;
        try {
            Material fillerMaterial = Material.matchMaterial(fillerMat.toUpperCase());
            if (fillerMaterial != null && fillerMaterial != Material.AIR) {
                filler = makeItemSafe(fillerMat, 1, fillerName);
            }
        } catch (Exception ignored) {}
        final ItemStack fillerItem = filler;
        for (int slot : targetSlots.keySet()) {
            inv.setItem(slot, fillerItem);
        }

        java.util.List<java.util.List<Integer>> frames = buildFrames(type, targetSlots.keySet(), rows);
        final String[] frameType = {type};
        final int[] frameIdx = {0};

        boolean soundEnabled = config.getBoolean("Settings.Animation.Sound.Enabled", false);
        String soundName     = config.getString("Settings.Animation.Sound.Name", "UI_BUTTON_CLICK");
        float  soundVolume   = (float) config.getDouble("Settings.Animation.Sound.Volume", 0.5);
        float  soundPitch    = (float) config.getDouble("Settings.Animation.Sound.Pitch", 1.5);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (frameIdx[0] >= frames.size() || !player.isOnline()) {
                targetSlots.forEach(inv::setItem);
                cancelAnimation(player.getUniqueId());
                return;
            }
            for (int slot : frames.get(frameIdx[0])) {
                ItemStack item = targetSlots.get(slot);
                if (item != null) inv.setItem(slot, item);
            }
            if (soundEnabled) {
                try {
                    Sound sound = Sound.valueOf(soundName.toUpperCase());
                    player.playSound(player.getLocation(), sound, soundVolume, soundPitch);
                } catch (Exception ignored) {}
            }
            frameIdx[0]++;
        }, delay, delay);

        activeAnimations.put(player.getUniqueId(), task);
    }

    public static void openShop(Player player, ShopType sell, Category cat, int page) {
        cancelAnimation(player.getUniqueId());
        Methods.updateAuction();
        FileConfiguration config = Files.CONFIG.getFile();
        FileConfiguration data = Files.DATA.getFile();
        java.util.List<ItemStack> items = new ArrayList<>();
        java.util.List<Integer> ID = new ArrayList<>();
        if (!data.contains("Items")) {
            data.set("Items.Clear", null);
            Files.DATA.saveFile();
        }
        if (cat != null) {
            shopCategory.put(player, cat);
        } else {
            shopCategory.put(player, Category.NONE);
        }
        if (data.contains("Items")) {
            for (String i : data.getConfigurationSection("Items").getKeys(false)) {
                java.util.List<String> lore = new ArrayList<>();
                if (data.getItemStack("Items." + i + ".Item") != null && (cat.getItems().contains(data.getItemStack("Items." + i + ".Item").getType()) || cat == Category.NONE)) {
                    if (data.getBoolean("Items." + i + ".Biddable")) {
                        if (sell == ShopType.BID) {
                            String seller = data.getString("Items." + i + ".Seller");
                            String topbidder = data.getString("Items." + i + ".TopBidder");
                            for (String l : config.getStringList("Settings.GUISettings.Bidding")) {
                                lore.add(l.replace("%TopBid%", Methods.getPrice(i, false)).replace("%topbid%", Methods.getPrice(i, false)).replace("%Seller%", seller).replace("%seller%", seller).replace("%TopBidder%", topbidder).replace("%topbidder%", topbidder).replace("%Time%", Methods.convertToTime(data.getLong("Items." + i + ".Time-Till-Expire"))).replace("%time%", Methods.convertToTime(data.getLong("Items." + i + ".Time-Till-Expire"))));
                            }
                            items.add(Methods.addLore(data.getItemStack("Items." + i + ".Item").clone(), lore));
                            ID.add(data.getInt("Items." + i + ".StoreID"));
                        }
                    } else {
                        if (sell == ShopType.SELL) {
                            for (String l : config.getStringList("Settings.GUISettings.SellingItemLore")) {
                                lore.add(l.replace("%Price%", String.format(Locale.ENGLISH, "%,d", Long.parseLong(Methods.getPrice(i, false)))).replace("%price%", String.format(Locale.ENGLISH, "%,d", Long.parseLong(Methods.getPrice(i, false)))).replace("%Seller%", data.getString("Items." + i + ".Seller")).replace("%seller%", data.getString("Items." + i + ".Seller")).replace("%Time%", Methods.convertToTime(data.getLong("Items." + i + ".Time-Till-Expire"))).replace("%time%", Methods.convertToTime(data.getLong("Items." + i + ".Time-Till-Expire"))));
                            }
                            items.add(Methods.addLore(data.getItemStack("Items." + i + ".Item").clone(), lore));
                            ID.add(data.getInt("Items." + i + ".StoreID"));
                        }
                    }
                }
            }
        }
        java.util.List<Integer> displaySlotsS = getDisplaySlots(config);
        int pageSizeS = displaySlotsS.size();
        int maxPage = getMaxPageCustom(items.size(), pageSizeS);
        page = Math.max(1, Math.min(page, maxPage));
        Inventory inv = Bukkit.createInventory(null, 54, Methods.color(config.getString("Settings.GUIName") + " #" + page));
        java.util.List<String> options = new ArrayList<>();
        options.add("SellingItems");
        options.add("Cancelled/ExpiredItems");
        options.add("PreviousPage");
        options.add("Refesh");
        options.add("NextPage");
        options.add("Category1");
        options.add("Category2");
        if (sell == ShopType.SELL) {
            shopType.put(player, ShopType.SELL);
            if (crazyAuctions.isBiddingEnabled()) {
                options.add("Bidding/Selling.Selling");
            }
            options.add("WhatIsThis.SellingShop");
        }
        if (sell == ShopType.BID) {
            shopType.put(player, ShopType.BID);
            if (crazyAuctions.isSellingEnabled()) {
                options.add("Bidding/Selling.Bidding");
            }
            options.add("WhatIsThis.BiddingShop");
        }
        for (String o : options) {
            if (config.contains("Settings.GUISettings.OtherSettings." + o + ".Toggle")) {
                if (!config.getBoolean("Settings.GUISettings.OtherSettings." + o + ".Toggle")) {
                    continue;
                }
            }
            String id = config.getString("Settings.GUISettings.OtherSettings." + o + ".Item");
            String name = config.getString("Settings.GUISettings.OtherSettings." + o + ".Name");
            java.util.List<String> lore = new ArrayList<>();
            int slot = config.getInt("Settings.GUISettings.OtherSettings." + o + ".Slot");
            String cName = Methods.color(config.getString("Settings.GUISettings.Category-Settings." + shopCategory.get(player).getName() + ".Name"));
            if (config.contains("Settings.GUISettings.OtherSettings." + o + ".Lore")) {
                for (String l : config.getStringList("Settings.GUISettings.OtherSettings." + o + ".Lore")) {
                    lore.add(l.replace("%Category%", cName).replace("%category%", cName));
                }
                inv.setItem(slot - 1, makeItemSafe(id, 1, name, lore));
            } else {
                inv.setItem(slot - 1, makeItemSafe(id, 1, name));
            }
        }
        java.util.List<Integer> displaySlots = displaySlotsS;
        java.util.List<ItemStack> pageItems = getPageCustom(items, page, pageSizeS);
        java.util.List<Integer> pageIds = getPageCustom(ID, page, pageSizeS);
        Map<Integer, Integer> slotToId = new HashMap<>();
        int displayIndex = 0;
        for (int slot : displaySlots) {
            if (displayIndex >= pageItems.size()) break;
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, pageItems.get(displayIndex));
                slotToId.put(slot, pageIds.get(displayIndex));
                displayIndex++;
            }
        }
        fillEmptySlots(inv, config);
        List.put(player, slotToId);
        Map<Integer, ItemStack> animSlots = new LinkedHashMap<>();
        for (int s = 0; s < inv.getSize(); s++) {
            ItemStack it = inv.getItem(s);
            if (it != null) animSlots.put(s, it.clone());
            inv.setItem(s, null);
        }
        openWithAnimation(player, inv, animSlots, config);
    }

    public static void openCategories(Player player, ShopType shop) {
        cancelAnimation(player.getUniqueId());
        Methods.updateAuction();
        FileConfiguration config = Files.CONFIG.getFile();
        Inventory inv = Bukkit.createInventory(null, 54, Methods.color(config.getString("Settings.Categories")));
        java.util.List<String> options = new ArrayList<>();
        options.add("OtherSettings.Back");
        options.add("OtherSettings.WhatIsThis.Categories");
        options.add("Category-Settings.Armor");
        options.add("Category-Settings.Weapons");
        options.add("Category-Settings.Tools");
        options.add("Category-Settings.Food");
        options.add("Category-Settings.Potions");
        options.add("Category-Settings.Blocks");
        options.add("Category-Settings.Other");
        options.add("Category-Settings.None");
        for (String o : options) {
            if (config.contains("Settings.GUISettings." + o + ".Toggle")) {
                if (!config.getBoolean("Settings.GUISettings." + o + ".Toggle")) {
                    continue;
                }
            }
            String id = config.getString("Settings.GUISettings." + o + ".Item");
            String name = config.getString("Settings.GUISettings." + o + ".Name");
            int slot = config.getInt("Settings.GUISettings." + o + ".Slot");
            if (config.contains("Settings.GUISettings." + o + ".Lore")) {
                inv.setItem(slot - 1, makeItemSafe(id, 1, name, config.getStringList("Settings.GUISettings." + o + ".Lore")));
            } else {
                inv.setItem(slot - 1, makeItemSafe(id, 1, name));
            }
        }
        shopType.put(player, shop);
        fillEmptySlots(inv, config);
        Map<Integer, ItemStack> animSlots = new LinkedHashMap<>();
        for (int s = 0; s < inv.getSize(); s++) {
            ItemStack it = inv.getItem(s);
            if (it != null) animSlots.put(s, it.clone());
            inv.setItem(s, null);
        }
        openWithAnimation(player, inv, animSlots, config);
    }

    public static void openPlayersCurrentList(Player player, int page) {
        cancelAnimation(player.getUniqueId());
        Methods.updateAuction();
        FileConfiguration config = Files.CONFIG.getFile();
        FileConfiguration data = Files.DATA.getFile();
        java.util.List<ItemStack> items = new ArrayList<>();
        java.util.List<Integer> ID = new ArrayList<>();
        Inventory inv = Bukkit.createInventory(null, 54, Methods.color(config.getString("Settings.Players-Current-Items")));
        java.util.List<String> options = new ArrayList<>();
        options.add("Back");
        options.add("WhatIsThis.CurrentItems");
        for (String o : options) {
            if (config.contains("Settings.GUISettings.OtherSettings." + o + ".Toggle")) {
                if (!config.getBoolean("Settings.GUISettings.OtherSettings." + o + ".Toggle")) {
                    continue;
                }
            }
            String id = config.getString("Settings.GUISettings.OtherSettings." + o + ".Item");
            String name = config.getString("Settings.GUISettings.OtherSettings." + o + ".Name");
            int slot = config.getInt("Settings.GUISettings.OtherSettings." + o + ".Slot");
            if (config.contains("Settings.GUISettings.OtherSettings." + o + ".Lore")) {
                inv.setItem(slot - 1, makeItemSafe(id, 1, name, config.getStringList("Settings.GUISettings.OtherSettings." + o + ".Lore")));
            } else {
                inv.setItem(slot - 1, makeItemSafe(id, 1, name));
            }
        }
        if (data.contains("Items")) {
            for (String i : data.getConfigurationSection("Items").getKeys(false)) {
                if (data.getString("Items." + i + ".Seller").equalsIgnoreCase(player.getName())) {
                    java.util.List<String> lore = new ArrayList<>();
                    for (String l : config.getStringList("Settings.GUISettings.CurrentLore")) {
                        lore.add(l.replace("%Price%", Methods.getPrice(i, false)).replace("%price%", Methods.getPrice(i, false)).replace("%Time%", Methods.convertToTime(data.getLong("Items." + i + ".Time-Till-Expire"))).replace("%time%", Methods.convertToTime(data.getLong("Items." + i + ".Time-Till-Expire"))));
                    }
                    items.add(Methods.addLore(data.getItemStack("Items." + i + ".Item").clone(), lore));
                    ID.add(data.getInt("Items." + i + ".StoreID"));
                }
            }
        }
        java.util.List<Integer> displaySlots = getDisplaySlots(config);
        int ps = displaySlots.size();
        java.util.List<ItemStack> pageItems = getPageCustom(items, page, ps);
        java.util.List<Integer> pageIds = getPageCustom(ID, page, ps);
        Map<Integer, Integer> slotToId = new HashMap<>();
        int displayIndex = 0;
        for (int slot : displaySlots) {
            if (displayIndex >= pageItems.size()) break;
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, pageItems.get(displayIndex));
                slotToId.put(slot, pageIds.get(displayIndex));
                displayIndex++;
            }
        }
        fillEmptySlots(inv, config);
        List.put(player, slotToId);
        Map<Integer, ItemStack> animSlots = new LinkedHashMap<>();
        for (int s = 0; s < inv.getSize(); s++) {
            ItemStack it = inv.getItem(s);
            if (it != null) animSlots.put(s, it.clone());
            inv.setItem(s, null);
        }
        openWithAnimation(player, inv, animSlots, config);
    }

    public static void openPlayersExpiredList(Player player, int page) {
        cancelAnimation(player.getUniqueId());
        Methods.updateAuction();
        FileConfiguration config = Files.CONFIG.getFile();
        FileConfiguration data = Files.DATA.getFile();
        java.util.List<ItemStack> items = new ArrayList<>();
        java.util.List<Integer> ID = new ArrayList<>();
        if (data.contains("OutOfTime/Cancelled")) {
            for (String i : data.getConfigurationSection("OutOfTime/Cancelled").getKeys(false)) {
                if (data.getString("OutOfTime/Cancelled." + i + ".Seller") != null) {
                    if (data.getString("OutOfTime/Cancelled." + i + ".Seller").equalsIgnoreCase(player.getName())) {
                        java.util.List<String> lore = new ArrayList<>();
                        for (String l : config.getStringList("Settings.GUISettings.Cancelled/ExpiredLore")) {
                            lore.add(l.replace("%Price%", Methods.getPrice(i, true)).replace("%price%", Methods.getPrice(i, true)).replace("%Time%", Methods.convertToTime(data.getLong("OutOfTime/Cancelled." + i + ".Full-Time"))).replace("%time%", Methods.convertToTime(data.getLong("OutOfTime/Cancelled." + i + ".Full-Time"))));
                        }
                        items.add(Methods.addLore(data.getItemStack("OutOfTime/Cancelled." + i + ".Item").clone(), lore));
                        ID.add(data.getInt("OutOfTime/Cancelled." + i + ".StoreID"));
                    }
                }
            }
        }
        java.util.List<Integer> displaySlotsExp = getDisplaySlots(config);
        int pageSizeExp = displaySlotsExp.size();
        int maxPage = getMaxPageCustom(items.size(), pageSizeExp);
        page = Math.max(1, Math.min(page, maxPage));
        Inventory inv = Bukkit.createInventory(null, 54, Methods.color(config.getString("Settings.Cancelled/Expired-Items") + " #" + page));
        java.util.List<String> options = new ArrayList<>();
        options.add("Back");
        options.add("PreviousPage");
        options.add("Return");
        options.add("NextPage");
        options.add("WhatIsThis.Cancelled/ExpiredItems");
        for (String o : options) {
            if (config.contains("Settings.GUISettings.OtherSettings." + o + ".Toggle")) {
                if (!config.getBoolean("Settings.GUISettings.OtherSettings." + o + ".Toggle")) {
                    continue;
                }
            }
            String id = config.getString("Settings.GUISettings.OtherSettings." + o + ".Item");
            String name = config.getString("Settings.GUISettings.OtherSettings." + o + ".Name");
            int slot = config.getInt("Settings.GUISettings.OtherSettings." + o + ".Slot");
            if (config.contains("Settings.GUISettings.OtherSettings." + o + ".Lore")) {
                inv.setItem(slot - 1, makeItemSafe(id, 1, name, config.getStringList("Settings.GUISettings.OtherSettings." + o + ".Lore")));
            } else {
                inv.setItem(slot - 1, makeItemSafe(id, 1, name));
            }
        }
        java.util.List<Integer> displaySlots = displaySlotsExp;
        java.util.List<ItemStack> pageItems = getPageCustom(items, page, pageSizeExp);
        java.util.List<Integer> pageIds = getPageCustom(ID, page, pageSizeExp);
        Map<Integer, Integer> slotToId = new HashMap<>();
        int displayIndex = 0;
        for (int slot : displaySlots) {
            if (displayIndex >= pageItems.size()) break;
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, pageItems.get(displayIndex));
                slotToId.put(slot, pageIds.get(displayIndex));
                displayIndex++;
            }
        }
        fillEmptySlots(inv, config);
        List.put(player, slotToId);
        Map<Integer, ItemStack> animSlots = new LinkedHashMap<>();
        for (int s = 0; s < inv.getSize(); s++) {
            ItemStack it = inv.getItem(s);
            if (it != null) animSlots.put(s, it.clone());
            inv.setItem(s, null);
        }
        openWithAnimation(player, inv, animSlots, config);
    }

    public static void openBuying(Player player, String ID) {
        Methods.updateAuction();
        FileConfiguration config = Files.CONFIG.getFile();
        FileConfiguration data = Files.DATA.getFile();
        if (!data.contains("Items." + ID)) {
            openShop(player, ShopType.SELL, shopCategory.get(player), 1);
            player.sendMessage(Messages.ITEM_DOESNT_EXIST.getMessage());
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 9, Methods.color(config.getString("Settings.Buying-Item")));
        java.util.List<String> options = new ArrayList<>();
        options.add("Confirm");
        options.add("Cancel");
        for (String o : options) {
            String id = config.getString("Settings.GUISettings.OtherSettings." + o + ".Item");
            String name = config.getString("Settings.GUISettings.OtherSettings." + o + ".Name");
            ItemStack item;
            if (config.contains("Settings.GUISettings.OtherSettings." + o + ".Lore")) {
                item = makeItemSafe(id, 1, name, config.getStringList("Settings.GUISettings.OtherSettings." + o + ".Lore"));
            } else {
                item = makeItemSafe(id, 1, name);
            }
            if (o.equals("Confirm")) {
                inv.setItem(0, item);
                inv.setItem(1, item);
                inv.setItem(2, item);
                inv.setItem(3, item);
            }
            if (o.equals("Cancel")) {
                inv.setItem(5, item);
                inv.setItem(6, item);
                inv.setItem(7, item);
                inv.setItem(8, item);
            }
        }
        ItemStack item = data.getItemStack("Items." + ID + ".Item");
        java.util.List<String> lore = new ArrayList<>();
        for (String l : config.getStringList("Settings.GUISettings.SellingItemLore")) {
            lore.add(l.replace("%Price%", Methods.getPrice(ID, false)).replace("%price%", Methods.getPrice(ID, false)).replace("%Seller%", data.getString("Items." + ID + ".Seller")).replace("%seller%", data.getString("Items." + ID + ".Seller")).replace("%Time%", Methods.convertToTime(data.getLong("Items." + l + ".Time-Till-Expire"))).replace("%time%", Methods.convertToTime(data.getLong("Items." + l + ".Time-Till-Expire"))));
        }
        inv.setItem(4, Methods.addLore(item.clone(), lore));
        IDs.put(player, ID);
        player.openInventory(inv);
    }

    public static void openBidding(Player player, String ID) {
        Methods.updateAuction();
        FileConfiguration config = Files.CONFIG.getFile();
        FileConfiguration data = Files.DATA.getFile();
        if (!data.contains("Items." + ID)) {
            openShop(player, ShopType.BID, shopCategory.get(player), 1);
            player.sendMessage(Messages.ITEM_DOESNT_EXIST.getMessage());
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, Methods.color(config.getString("Settings.Bidding-On-Item")));
        if (!bidding.containsKey(player)) bidding.put(player, 0);
        if (Version.isNewer(Version.v1_12_R1)) {
            inv.setItem(9, Methods.makeItem("LIME_STAINED_GLASS_PANE", 1, "&a+1"));
            inv.setItem(10, Methods.makeItem("LIME_STAINED_GLASS_PANE", 1, "&a+10"));
            inv.setItem(11, Methods.makeItem("LIME_STAINED_GLASS_PANE", 1, "&a+100"));
            inv.setItem(12, Methods.makeItem("LIME_STAINED_GLASS_PANE", 1, "&a+1000"));
            inv.setItem(14, Methods.makeItem("RED_STAINED_GLASS_PANE", 1, "&c-1000"));
            inv.setItem(15, Methods.makeItem("RED_STAINED_GLASS_PANE", 1, "&c-100"));
            inv.setItem(16, Methods.makeItem("RED_STAINED_GLASS_PANE", 1, "&c-10"));
            inv.setItem(17, Methods.makeItem("RED_STAINED_GLASS_PANE", 1, "&c-1"));
        } else {
            inv.setItem(9, Methods.makeItem("160:5", 1, "&a+1"));
            inv.setItem(10, Methods.makeItem("160:5", 1, "&a+10"));
            inv.setItem(11, Methods.makeItem("160:5", 1, "&a+100"));
            inv.setItem(12, Methods.makeItem("160:5", 1, "&a+1000"));
            inv.setItem(14, Methods.makeItem("160:14", 1, "&c-1000"));
            inv.setItem(15, Methods.makeItem("160:14", 1, "&c-100"));
            inv.setItem(16, Methods.makeItem("160:14", 1, "&c-10"));
            inv.setItem(17, Methods.makeItem("160:14", 1, "&c-1"));
        }
        inv.setItem(13, getBiddingGlass(player, ID));
        inv.setItem(22, makeItemSafe(config.getString("Settings.GUISettings.OtherSettings.Bid.Item"), 1, config.getString("Settings.GUISettings.OtherSettings.Bid.Name"), config.getStringList("Settings.GUISettings.OtherSettings.Bid.Lore")));
        inv.setItem(4, getBiddingItem(player, ID));
        player.openInventory(inv);
    }

    public static void openViewer(Player player, String other, int page) {
        cancelAnimation(player.getUniqueId());
        Methods.updateAuction();
        FileConfiguration config = Files.CONFIG.getFile();
        FileConfiguration data = Files.DATA.getFile();
        java.util.List<ItemStack> items = new ArrayList<>();
        java.util.List<Integer> ID = new ArrayList<>();
        if (!data.contains("Items")) {
            data.set("Items.Clear", null);
            Files.DATA.saveFile();
        }
        if (data.contains("Items")) {
            for (String i : data.getConfigurationSection("Items").getKeys(false)) {
                if (data.getString("Items." + i + ".Seller").equalsIgnoreCase(other)) {
                    java.util.List<String> lore = new ArrayList<>();
                    if (data.getBoolean("Items." + i + ".Biddable")) {
                        String seller = data.getString("Items." + i + ".Seller");
                        String topbidder = data.getString("Items." + i + ".TopBidder");
                        for (String l : config.getStringList("Settings.GUISettings.Bidding")) {
                            lore.add(l.replace("%TopBid%", Methods.getPrice(i, false)).replace("%topbid%", Methods.getPrice(i, false)).replace("%Seller%", seller).replace("%seller%", seller).replace("%TopBidder%", topbidder).replace("%topbidder%", topbidder).replace("%Time%", Methods.convertToTime(data.getLong("Items." + i + ".Time-Till-Expire"))).replace("%time%", Methods.convertToTime(data.getLong("Items." + i + ".Time-Till-Expire"))));
                        }
                    } else {
                        for (String l : config.getStringList("Settings.GUISettings.SellingItemLore")) {
                            lore.add(l.replace("%Price%", Methods.getPrice(i, false)).replace("%price%", Methods.getPrice(i, false)).replace("%Seller%", data.getString("Items." + i + ".Seller")).replace("%seller%", data.getString("Items." + i + ".Seller")).replace("%Time%", Methods.convertToTime(data.getLong("Items." + i + ".Time-Till-Expire"))).replace("%time%", Methods.convertToTime(data.getLong("Items." + i + ".Time-Till-Expire"))));
                        }
                    }
                    items.add(Methods.addLore(data.getItemStack("Items." + i + ".Item").clone(), lore));
                    ID.add(data.getInt("Items." + i + ".StoreID"));
                }
            }
        }
        java.util.List<Integer> displaySlotsV = getDisplaySlots(config);
        int pageSizeV = displaySlotsV.size();
        int maxPage = getMaxPageCustom(items.size(), pageSizeV);
        page = Math.max(1, Math.min(page, maxPage));
        Inventory inv = Bukkit.createInventory(null, 54, Methods.color(config.getString("Settings.GUIName") + " #" + page));
        java.util.List<String> options = new ArrayList<>();
        options.add("WhatIsThis.Viewing");
        for (String o : options) {
            if (config.contains("Settings.GUISettings.OtherSettings." + o + ".Toggle")) {
                if (!config.getBoolean("Settings.GUISettings.OtherSettings." + o + ".Toggle")) {
                    continue;
                }
            }
            String id = config.getString("Settings.GUISettings.OtherSettings." + o + ".Item");
            String name = config.getString("Settings.GUISettings.OtherSettings." + o + ".Name");
            int slot = config.getInt("Settings.GUISettings.OtherSettings." + o + ".Slot");
            if (config.contains("Settings.GUISettings.OtherSettings." + o + ".Lore")) {
                inv.setItem(slot - 1, makeItemSafe(id, 1, name, config.getStringList("Settings.GUISettings.OtherSettings." + o + ".Lore")));
            } else {
                inv.setItem(slot - 1, makeItemSafe(id, 1, name));
            }
        }
        java.util.List<Integer> displaySlots = displaySlotsV;
        java.util.List<ItemStack> pageItems = getPageCustom(items, page, pageSizeV);
        java.util.List<Integer> pageIds = getPageCustom(ID, page, pageSizeV);
        Map<Integer, Integer> slotToId = new HashMap<>();
        int displayIndex = 0;
        for (int slot : displaySlots) {
            if (displayIndex >= pageItems.size()) break;
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, pageItems.get(displayIndex));
                slotToId.put(slot, pageIds.get(displayIndex));
                displayIndex++;
            }
        }
        fillEmptySlots(inv, config);
        List.put(player, slotToId);
        Map<Integer, ItemStack> animSlots = new LinkedHashMap<>();
        for (int s = 0; s < inv.getSize(); s++) {
            ItemStack it = inv.getItem(s);
            if (it != null) animSlots.put(s, it.clone());
            inv.setItem(s, null);
        }
        openWithAnimation(player, inv, animSlots, config);
    }

    public static ItemStack getBiddingGlass(Player player, String ID) {
        FileConfiguration config = Files.CONFIG.getFile();
        String id = config.getString("Settings.GUISettings.OtherSettings.Bidding.Item");
        String name = config.getString("Settings.GUISettings.OtherSettings.Bidding.Name");
        ItemStack item;
        int bid = bidding.get(player);
        if (config.contains("Settings.GUISettings.OtherSettings.Bidding.Lore")) {
            java.util.List<String> lore = new ArrayList<>();
            for (String l : config.getStringList("Settings.GUISettings.OtherSettings.Bidding.Lore")) {
                lore.add(l.replace("%Bid%", bid + "").replace("%bid%", bid + "").replace("%TopBid%", Methods.getPrice(ID, false)).replace("%topbid%", Methods.getPrice(ID, false)));
            }
            item = makeItemSafe(id, 1, name, lore);
        } else {
            item = makeItemSafe(id, 1, name);
        }
        return item;
    }

    public static ItemStack getBiddingItem(Player player, String ID) {
        FileConfiguration config = Files.CONFIG.getFile();
        FileConfiguration data = Files.DATA.getFile();
        String seller = data.getString("Items." + ID + ".Seller");
        String topbidder = data.getString("Items." + ID + ".TopBidder");
        ItemStack item = data.getItemStack("Items." + ID + ".Item");
        java.util.List<String> lore = new ArrayList<>();
        for (String l : config.getStringList("Settings.GUISettings.Bidding")) {
            lore.add(l.replace("%TopBid%", Methods.getPrice(ID, false)).replace("%topbid%", Methods.getPrice(ID, false)).replace("%Seller%", seller).replace("%seller%", seller).replace("%TopBidder%", topbidder).replace("%topbidder%", topbidder).replace("%Time%", Methods.convertToTime(data.getLong("Items." + ID + ".Time-Till-Expire"))).replace("%time%", Methods.convertToTime(data.getLong("Items." + ID + ".Time-Till-Expire"))));
        }
        return Methods.addLore(item.clone(), lore);
    }

    private static void playClick(Player player) {
        if (Files.CONFIG.getFile().contains("Settings.Sounds.Toggle")) {
            if (Files.CONFIG.getFile().getBoolean("Settings.Sounds.Toggle")) {
                String sound = Files.CONFIG.getFile().getString("Settings.Sounds.Sound");
                try {
                    player.playSound(player.getLocation(), Sound.valueOf(sound), 1, 1);
                } catch (Exception e) {
                    if (Methods.getVersion() >= 191) {
                        player.playSound(player.getLocation(), Sound.valueOf("UI_BUTTON_CLICK"), 1, 1);
                    } else {
                        player.playSound(player.getLocation(), Sound.valueOf("CLICK"), 1, 1);
                    }
                    Bukkit.getLogger().log(Level.WARNING, "[Crazy Auctions]>> You set the sound to " + sound + " and this is not a sound for your minecraft version. " + "Please go to the config and set a correct sound or turn the sound off in the toggle setting.");
                }
            }
        } else {
            if (Methods.getVersion() >= 191) {
                player.playSound(player.getLocation(), Sound.valueOf("UI_BUTTON_CLICK"), 1, 1);
            } else {
                player.playSound(player.getLocation(), Sound.valueOf("CLICK"), 1, 1);
            }
        }
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        FileConfiguration config = Files.CONFIG.getFile();
        Inventory inv = e.getInventory();
        Player player = (Player) e.getPlayer();
        cancelAnimation(player.getUniqueId());
        if (inv != null) {
            if (e.getView().getTitle().contains(Methods.color(config.getString("Settings.Bidding-On-Item")))) {
                bidding.remove(player);
            }
        }
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        FileConfiguration config = Files.CONFIG.getFile();
        FileConfiguration data = Files.DATA.getFile();
        Player player = (Player) e.getWhoClicked();
        final Inventory inv = e.getInventory();
        if (inv != null) {
            if (e.getView().getTitle().contains(Methods.color(config.getString("Settings.Categories")))) {
                e.setCancelled(true);
                int slot = e.getRawSlot();
                if (slot <= inv.getSize()) {
                    if (e.getCurrentItem() != null) {
                        ItemStack item = e.getCurrentItem();
                        if (item.hasItemMeta()) {
                            if (item.getItemMeta().hasDisplayName()) {
                                for (Category cat : Category.values()) {
                                    if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.Category-Settings." + cat.getName() + ".Name")))) {
                                        openShop(player, shopType.get(player), cat, 1);
                                        playClick(player);
                                        return;
                                    }
                                    if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Back.Name")))) {
                                        openShop(player, shopType.get(player), shopCategory.get(player), 1);
                                        playClick(player);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (e.getView().getTitle().contains(Methods.color(config.getString("Settings.Bidding-On-Item")))) {
                e.setCancelled(true);
                int slot = e.getRawSlot();
                if (slot <= inv.getSize()) {
                    if (e.getCurrentItem() != null) {
                        ItemStack item = e.getCurrentItem();
                        if (item.hasItemMeta()) {
                            if (item.getItemMeta().hasDisplayName()) {
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Bid.Name")))) {
                                    String ID = biddingID.get(player);
                                    int bid = bidding.get(player);
                                    String topBidder = data.getString("Items." + ID + ".TopBidder");
                                    if (CurrencyManager.getMoney(player) < bid) {
                                        HashMap<String, String> placeholders = new HashMap<>();
                                        placeholders.put("%Money_Needed%", (bid - CurrencyManager.getMoney(player)) + "");
                                        placeholders.put("%money_needed%", (bid - CurrencyManager.getMoney(player)) + "");
                                        player.sendMessage(Messages.NEED_MORE_MONEY.getMessage(placeholders));
                                        return;
                                    }
                                    if (data.getLong("Items." + ID + ".Price") > bid) {
                                        player.sendMessage(Messages.BID_MORE_MONEY.getMessage());
                                        return;
                                    }
                                    if (data.getLong("Items." + ID + ".Price") >= bid && !topBidder.equalsIgnoreCase("None")) {
                                        player.sendMessage(Messages.BID_MORE_MONEY.getMessage());
                                        return;
                                    }
                                    Bukkit.getPluginManager().callEvent(new AuctionNewBidEvent(player, data.getItemStack("Items." + ID + ".Item"), bid));
                                    data.set("Items." + ID + ".Price", bid);
                                    data.set("Items." + ID + ".TopBidder", player.getName());
                                    HashMap<String, String> placeholders = new HashMap<>();
                                    placeholders.put("%Bid%", bid + "");
                                    player.sendMessage(Messages.BID_MESSAGE.getMessage(placeholders));
                                    Files.DATA.saveFile();
                                    bidding.put(player, 0);
                                    player.closeInventory();
                                    playClick(player);
                                    return;
                                }
                                HashMap<String, Integer> priceEdits = new HashMap<>();
                                priceEdits.put("&a+1", 1);
                                priceEdits.put("&a+10", 10);
                                priceEdits.put("&a+100", 100);
                                priceEdits.put("&a+1000", 1000);
                                priceEdits.put("&c-1", -1);
                                priceEdits.put("&c-10", -10);
                                priceEdits.put("&c-100", -100);
                                priceEdits.put("&c-1000", -1000);
                                for (String price : priceEdits.keySet()) {
                                    if (item.getItemMeta().getDisplayName().equals(Methods.color(price))) {
                                        try {
                                            bidding.put(player, (bidding.get(player) + priceEdits.get(price)));
                                            inv.setItem(4, getBiddingItem(player, biddingID.get(player)));
                                            inv.setItem(13, getBiddingGlass(player, biddingID.get(player)));
                                            playClick(player);
                                            return;
                                        } catch (Exception ex) {
                                            player.closeInventory();
                                            player.sendMessage(Messages.ITEM_DOESNT_EXIST.getMessage());
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (e.getView().getTitle().contains(Methods.color(config.getString("Settings.GUIName")))) {
                e.setCancelled(true);
                final int slot = e.getRawSlot();
                if (slot <= inv.getSize()) {
                    if (e.getCurrentItem() != null) {
                        final ItemStack item = e.getCurrentItem();
                        if (item.hasItemMeta()) {
                            if (item.getItemMeta().hasDisplayName()) {
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.NextPage.Name")))) {
                                    Methods.updateAuction();
                                    int page = Integer.parseInt(e.getView().getTitle().split("#")[1]);
                                    openShop(player, shopType.get(player), shopCategory.get(player), page + 1);
                                    playClick(player);
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.PreviousPage.Name")))) {
                                    Methods.updateAuction();
                                    int page = Integer.parseInt(e.getView().getTitle().split("#")[1]);
                                    if (page == 1) page++;
                                    openShop(player, shopType.get(player), shopCategory.get(player), page - 1);
                                    playClick(player);
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Refesh.Name")))) {
                                    Methods.updateAuction();
                                    int page = Integer.parseInt(e.getView().getTitle().split("#")[1]);
                                    openShop(player, shopType.get(player), shopCategory.get(player), page);
                                    playClick(player);
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Bidding/Selling.Selling.Name")))) {
                                    openShop(player, ShopType.BID, shopCategory.get(player), 1);
                                    playClick(player);
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Bidding/Selling.Bidding.Name")))) {
                                    openShop(player, ShopType.SELL, shopCategory.get(player), 1);
                                    playClick(player);
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Cancelled/ExpiredItems.Name")))) {
                                    openPlayersExpiredList(player, 1);
                                    playClick(player);
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.SellingItems.Name")))) {
                                    openPlayersCurrentList(player, 1);
                                    playClick(player);
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Category1.Name")))) {
                                    openCategories(player, shopType.get(player));
                                    playClick(player);
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Category2.Name")))) {
                                    openCategories(player, shopType.get(player));
                                    playClick(player);
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Your-Item.Name")))) {
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Cant-Afford.Name")))) {
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Top-Bidder.Name")))) {
                                    return;
                                }
                            }
                            if (List.containsKey(player)) {
                                if (List.get(player).containsKey(slot)) {
                                    int id = List.get(player).get(slot);
                                    boolean T = false;
                                    if (data.contains("Items")) {
                                        for (String i : data.getConfigurationSection("Items").getKeys(false)) {
                                            int ID = data.getInt("Items." + i + ".StoreID");
                                            if (id == ID) {
                                                if (player.hasPermission("crazyAuctions.admin") || player.hasPermission("crazyauctions.force-end")) {
                                                    if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                                                        int num = 1;
                                                        for (; data.contains("OutOfTime/Cancelled." + num); num++) ;
                                                        String seller = data.getString("Items." + i + ".Seller");
                                                        Player sellerPlayer = Methods.getPlayer(seller);
                                                        if (Methods.isOnline(seller) && sellerPlayer != null) {
                                                            sellerPlayer.sendMessage(Messages.ADMIN_FORCE_CANCELLED_TO_PLAYER.getMessage());
                                                        }
                                                        AuctionCancelledEvent event = new AuctionCancelledEvent((sellerPlayer != null ? sellerPlayer : Bukkit.getOfflinePlayer(seller)), data.getItemStack("Items." + i + ".Item"), CancelledReason.ADMIN_FORCE_CANCEL);
                                                        Bukkit.getPluginManager().callEvent(event);
                                                        data.set("OutOfTime/Cancelled." + num + ".Seller", data.getString("Items." + i + ".Seller"));
                                                        data.set("OutOfTime/Cancelled." + num + ".Full-Time", data.getLong("Items." + i + ".Full-Time"));
                                                        data.set("OutOfTime/Cancelled." + num + ".StoreID", data.getInt("Items." + i + ".StoreID"));
                                                        data.set("OutOfTime/Cancelled." + num + ".Item", data.getItemStack("Items." + i + ".Item"));
                                                        data.set("Items." + i, null);
                                                        Files.DATA.saveFile();
                                                        player.sendMessage(Messages.ADMIN_FORCE_CENCELLED.getMessage());
                                                        playClick(player);
                                                        int page = Integer.parseInt(e.getView().getTitle().split("#")[1]);
                                                        openShop(player, shopType.get(player), shopCategory.get(player), page);
                                                        return;
                                                    }
                                                }
                                                final Runnable runnable = () -> inv.setItem(slot, item);
                                                if (data.getString("Items." + i + ".Seller").equalsIgnoreCase(player.getName())) {
                                                    String it = config.getString("Settings.GUISettings.OtherSettings.Your-Item.Item");
                                                    String name = config.getString("Settings.GUISettings.OtherSettings.Your-Item.Name");
                                                    ItemStack I;
                                                    if (config.contains("Settings.GUISettings.OtherSettings.Your-Item.Lore")) {
                                                        I = makeItemSafe(it, 1, name, config.getStringList("Settings.GUISettings.OtherSettings.Your-Item.Lore"));
                                                    } else {
                                                        I = makeItemSafe(it, 1, name);
                                                    }
                                                    inv.setItem(slot, I);
                                                    playClick(player);
                                                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, runnable, 3 * 20);
                                                    return;
                                                }
                                                long cost = data.getLong("Items." + i + ".Price");
                                                if (CurrencyManager.getMoney(player) < cost) {
                                                    String it = config.getString("Settings.GUISettings.OtherSettings.Cant-Afford.Item");
                                                    String name = config.getString("Settings.GUISettings.OtherSettings.Cant-Afford.Name");
                                                    ItemStack I;
                                                    if (config.contains("Settings.GUISettings.OtherSettings.Cant-Afford.Lore")) {
                                                        I = makeItemSafe(it, 1, name, config.getStringList("Settings.GUISettings.OtherSettings.Cant-Afford.Lore"));
                                                    } else {
                                                        I = makeItemSafe(it, 1, name);
                                                    }
                                                    inv.setItem(slot, I);
                                                    playClick(player);
                                                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, runnable, 3 * 20);
                                                    return;
                                                }
                                                if (data.getBoolean("Items." + i + ".Biddable")) {
                                                    if (player.getName().equalsIgnoreCase(data.getString("Items." + i + ".TopBidder"))) {
                                                        String it = config.getString("Settings.GUISettings.OtherSettings.Top-Bidder.Item");
                                                        String name = config.getString("Settings.GUISettings.OtherSettings.Top-Bidder.Name");
                                                        ItemStack I;
                                                        if (config.contains("Settings.GUISettings.OtherSettings.Top-Bidder.Lore")) {
                                                            I = makeItemSafe(it, 1, name, config.getStringList("Settings.GUISettings.OtherSettings.Top-Bidder.Lore"));
                                                        } else {
                                                            I = makeItemSafe(it, 1, name);
                                                        }
                                                        inv.setItem(slot, I);
                                                        playClick(player);
                                                        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, runnable, 3 * 20);
                                                        return;
                                                    }
                                                    playClick(player);
                                                    openBidding(player, i);
                                                    biddingID.put(player, i);
                                                } else {
                                                    playClick(player);
                                                    openBuying(player, i);
                                                }
                                                return;
                                            }
                                        }
                                    }
                                    if (!T) {
                                        playClick(player);
                                        openShop(player, shopType.get(player), shopCategory.get(player), 1);
                                        player.sendMessage(Messages.ITEM_DOESNT_EXIST.getMessage());
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (e.getView().getTitle().contains(Methods.color(config.getString("Settings.Buying-Item")))) {
                e.setCancelled(true);
                int slot = e.getRawSlot();
                if (slot <= inv.getSize()) {
                    if (e.getCurrentItem() != null) {
                        ItemStack item = e.getCurrentItem();
                        if (item.hasItemMeta()) {
                            if (item.getItemMeta().hasDisplayName()) {
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Confirm.Name")))) {
                                    String ID = IDs.get(player);
                                    long cost = data.getLong("Items." + ID + ".Price");
                                    String seller = data.getString("Items." + ID + ".Seller");
                                    if (!data.contains("Items." + ID)) {
                                        playClick(player);
                                        openShop(player, shopType.get(player), shopCategory.get(player), 1);
                                        player.sendMessage(Messages.ITEM_DOESNT_EXIST.getMessage());
                                        return;
                                    }
                                    if (Methods.isInvFull(player)) {
                                        playClick(player);
                                        player.closeInventory();
                                        player.sendMessage(Messages.INVENTORY_FULL.getMessage());
                                        return;
                                    }
                                    if (CurrencyManager.getMoney(player) < cost) {
                                        playClick(player);
                                        player.closeInventory();
                                        HashMap<String, String> placeholders = new HashMap<>();
                                        placeholders.put("%Money_Needed%", (cost - CurrencyManager.getMoney(player)) + "");
                                        placeholders.put("%money_needed%", (cost - CurrencyManager.getMoney(player)) + "");
                                        player.sendMessage(Messages.NEED_MORE_MONEY.getMessage(placeholders));
                                        return;
                                    }
                                    ItemStack i = data.getItemStack("Items." + ID + ".Item");
                                    Bukkit.getPluginManager().callEvent(new AuctionBuyEvent(player, i, cost));
                                    CurrencyManager.removeMoney(player, cost);
                                    CurrencyManager.addMoney(Methods.getOfflinePlayer(seller), cost);
                                    HashMap<String, String> placeholders = new HashMap<>();
                                    placeholders.put("%Price%", Methods.getPrice(ID, false));
                                    placeholders.put("%price%", Methods.getPrice(ID, false));
                                    placeholders.put("%Player%", player.getName());
                                    placeholders.put("%player%", player.getName());
                                    player.sendMessage(Messages.BOUGHT_ITEM.getMessage(placeholders));
                                    if (Methods.isOnline(seller) && Methods.getPlayer(seller) != null) {
                                        Player sell = Methods.getPlayer(seller);
                                        sell.sendMessage(Messages.PLAYER_BOUGHT_ITEM.getMessage(placeholders));
                                    }
                                    player.getInventory().addItem(i);
                                    data.set("Items." + ID, null);
                                    Files.DATA.saveFile();
                                    playClick(player);
                                    openShop(player, shopType.get(player), shopCategory.get(player), 1);
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Cancel.Name")))) {
                                    openShop(player, shopType.get(player), shopCategory.get(player), 1);
                                    playClick(player);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
            if (e.getView().getTitle().contains(Methods.color(config.getString("Settings.Players-Current-Items")))) {
                e.setCancelled(true);
                int slot = e.getRawSlot();
                if (slot <= inv.getSize()) {
                    if (e.getCurrentItem() != null) {
                        ItemStack item = e.getCurrentItem();
                        if (item.hasItemMeta()) {
                            if (item.getItemMeta().hasDisplayName()) {
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Back.Name")))) {
                                    openShop(player, shopType.get(player), shopCategory.get(player), 1);
                                    playClick(player);
                                    return;
                                }
                            }
                            if (List.containsKey(player)) {
                                if (List.get(player).containsKey(slot)) {
                                    int id = List.get(player).get(slot);
                                    boolean T = false;
                                    if (data.contains("Items")) {
                                        for (String i : data.getConfigurationSection("Items").getKeys(false)) {
                                            int ID = data.getInt("Items." + i + ".StoreID");
                                            if (id == ID) {
                                                player.sendMessage(Messages.CANCELLED_ITEM.getMessage());
                                                AuctionCancelledEvent event = new AuctionCancelledEvent(player, data.getItemStack("Items." + i + ".Item"), CancelledReason.PLAYER_FORCE_CANCEL);
                                                Bukkit.getPluginManager().callEvent(event);
                                                int num = 1;
                                                for (; data.contains("OutOfTime/Cancelled." + num); num++) ;
                                                data.set("OutOfTime/Cancelled." + num + ".Seller", data.getString("Items." + i + ".Seller"));
                                                data.set("OutOfTime/Cancelled." + num + ".Full-Time", data.getLong("Items." + i + ".Full-Time"));
                                                data.set("OutOfTime/Cancelled." + num + ".StoreID", data.getInt("Items." + i + ".StoreID"));
                                                data.set("OutOfTime/Cancelled." + num + ".Item", data.getItemStack("Items." + i + ".Item"));
                                                data.set("Items." + i, null);
                                                Files.DATA.saveFile();
                                                playClick(player);
                                                openPlayersCurrentList(player, 1);
                                                return;
                                            }
                                        }
                                    }
                                    if (!T) {
                                        playClick(player);
                                        openShop(player, shopType.get(player), shopCategory.get(player), 1);
                                        player.sendMessage(Messages.ITEM_DOESNT_EXIST.getMessage());
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (e.getView().getTitle().contains(Methods.color(config.getString("Settings.Cancelled/Expired-Items")))) {
                e.setCancelled(true);
                final int slot = e.getRawSlot();
                if (slot <= inv.getSize()) {
                    if (e.getCurrentItem() != null) {
                        final ItemStack item = e.getCurrentItem();
                        if (item.hasItemMeta()) {
                            if (item.getItemMeta().hasDisplayName()) {
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Back.Name")))) {
                                    Methods.updateAuction();
                                    playClick(player);
                                    openShop(player, shopType.get(player), shopCategory.get(player), 1);
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.PreviousPage.Name")))) {
                                    Methods.updateAuction();
                                    int page = Integer.parseInt(e.getView().getTitle().split("#")[1]);
                                    if (page == 1) page++;
                                    playClick(player);
                                    openPlayersExpiredList(player, (page - 1));
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.Return.Name")))) {
                                    Methods.updateAuction();
                                    int page = Integer.parseInt(e.getView().getTitle().split("#")[1]);
                                    if (data.contains("OutOfTime/Cancelled")) {
                                        for (String i : data.getConfigurationSection("OutOfTime/Cancelled").getKeys(false)) {
                                            if (data.getString("OutOfTime/Cancelled." + i + ".Seller").equalsIgnoreCase(player.getName())) {
                                                if (Methods.isInvFull(player)) {
                                                    player.sendMessage(Messages.INVENTORY_FULL.getMessage());
                                                    break;
                                                } else {
                                                    player.getInventory().addItem(data.getItemStack("OutOfTime/Cancelled." + i + ".Item"));
                                                    data.set("OutOfTime/Cancelled." + i, null);
                                                }
                                            }
                                        }
                                    }
                                    player.sendMessage(Messages.GOT_ITEM_BACK.getMessage());
                                    Files.DATA.saveFile();
                                    playClick(player);
                                    openPlayersExpiredList(player, page);
                                    return;
                                }
                                if (item.getItemMeta().getDisplayName().equals(Methods.color(config.getString("Settings.GUISettings.OtherSettings.NextPage.Name")))) {
                                    Methods.updateAuction();
                                    int page = Integer.parseInt(e.getView().getTitle().split("#")[1]);
                                    playClick(player);
                                    openPlayersExpiredList(player, (page + 1));
                                    return;
                                }
                            }
                            if (List.containsKey(player)) {
                                if (List.get(player).containsKey(slot)) {
                                    int id = List.get(player).get(slot);
                                    boolean T = false;
                                    if (data.contains("OutOfTime/Cancelled")) {
                                        for (String i : data.getConfigurationSection("OutOfTime/Cancelled").getKeys(false)) {
                                            int ID = data.getInt("OutOfTime/Cancelled." + i + ".StoreID");
                                            if (id == ID) {
                                                if (!Methods.isInvFull(player)) {
                                                    player.sendMessage(Messages.GOT_ITEM_BACK.getMessage());
                                                    ItemStack IT = data.getItemStack("OutOfTime/Cancelled." + i + ".Item");
                                                    player.getInventory().addItem(IT);
                                                    data.set("OutOfTime/Cancelled." + i, null);
                                                    Files.DATA.saveFile();
                                                    playClick(player);
                                                    openPlayersExpiredList(player, 1);
                                                } else {
                                                    player.sendMessage(Messages.INVENTORY_FULL.getMessage());
                                                }
                                                return;
                                            }
                                        }
                                    }
                                    if (!T) {
                                        playClick(player);
                                        openShop(player, shopType.get(player), shopCategory.get(player), 1);
                                        player.sendMessage(Messages.ITEM_DOESNT_EXIST.getMessage());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}