package com.songoda.epicfurnaces.furnace;

import com.songoda.core.compatibility.ServerVersion;
import com.songoda.core.gui.GuiManager;
import com.songoda.core.hooks.EconomyManager;
import com.songoda.epicfurnaces.EpicFurnaces;
import com.songoda.epicfurnaces.boost.BoostData;
import com.songoda.epicfurnaces.furnace.levels.Level;
import com.songoda.epicfurnaces.gui.GUIOverview;
import com.songoda.epicfurnaces.settings.Settings;
import com.songoda.epicfurnaces.utils.CostType;
import com.songoda.epicfurnaces.utils.Methods;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.InventoryHolder;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.*;

/**
 * Created by songoda on 3/7/2017.
 */
public class Furnace {

    private final EpicFurnaces plugin = EpicFurnaces.getInstance();

    private final Location location;
    private Level level = plugin.getLevelManager().getLowestLevel();
    private String nickname = null;
    private UUID placedBy = null;
    private int uses, tolevel, radiusOverheatLast, radiusFuelshareLast = 0;
    private final List<Location> radiusOverheat = new ArrayList<>();
    private final List<Location> radiusFuelshare = new ArrayList<>();
    private final List<String> accessList = new ArrayList<>();
    private final Map<String, Integer> cache = new HashMap<>();

    public Furnace(Location location) {
        this.location = location;
    }

    public void overview(GuiManager guiManager, Player player) {
        if (placedBy == null) placedBy = player.getUniqueId();

        if (!player.hasPermission("epicfurnaces.overview")) return;
        guiManager.showGUI(player, new GUIOverview(plugin, this, player));
    }

    public void plus(FurnaceSmeltEvent e) {
        Block block = location.getBlock();
        if (!block.getType().name().contains("FURNACE")) return;

        this.uses++;
        this.tolevel++;

        int multi = Settings.LEVEL_MULTIPLIER.getInt();

        if (level.getReward() == null) return;

        String reward = level.getReward();
        String[] amt = {"1", "1"};
        if (reward.contains(":")) {
            String[] rewardSplit = reward.split(":");
            reward = rewardSplit[0].substring(0, rewardSplit[0].length() - 1);
            if (rewardSplit[1].contains("-"))
                amt = rewardSplit[1].split("-");
            else {
                amt[0] = rewardSplit[1];
                amt[1] = rewardSplit[0];
            }
        }


        int needed = ((multi * level.getLevel()) - tolevel) - 1;

        if (Settings.UPGRADE_BY_SMELTING.getBoolean()
                && needed <= 0
                && plugin.getLevelManager().getLevel(level.getLevel() + 1) != null) {
            tolevel = 0;
            level = plugin.getLevelManager().getLevel(this.level.getLevel() + 1);
        }

        this.updateCook();

        FurnaceInventory i = (FurnaceInventory) ((InventoryHolder) block.getState()).getInventory();

        int num = Integer.parseInt(reward);
        double rand = Math.random() * 100;
        if (rand >= num
                || e.getResult().equals(Material.SPONGE)
                || Settings.NO_REWARDS_FROM_RECIPES.getBoolean()
                && plugin.getFurnaceRecipeFile().contains("Recipes." + i.getSmelting().getType().toString())) {
            return;
        }

        int r = Integer.parseInt(amt[0]);
        if (Integer.parseInt(amt[0]) !=
                Integer.parseInt(amt[1].replace("%", "")))
            r = (int) (Math.random() * ((Integer.parseInt(amt[1].replace("%", "")) - Integer.parseInt(amt[0])))) + Integer.parseInt(amt[0]);

        BoostData boostData = plugin.getBoostManager().getBoost(placedBy);
        r = r * (boostData == null ? 1 : boostData.getMultiplier());


        e.getResult().setAmount(e.getResult().getAmount() + r);
    }

    public void upgrade(Player player, CostType type) {
        if (plugin.getLevelManager().getLevels().containsKey(this.level.getLevel() + 1)) {

            Level level = plugin.getLevelManager().getLevel(this.level.getLevel() + 1);
            int cost = type == CostType.ECONOMY ? level.getCostEconomy() : level.getCostExperience();

            if (type == CostType.ECONOMY) {
                if (!EconomyManager.isEnabled()) {
                    player.sendMessage("Economy not enabled.");
                    return;
                }
                if (!EconomyManager.hasBalance(player, cost)) {

                    plugin.getLocale().getMessage("event.upgrade.cannotafford").sendPrefixedMessage(player);
                    return;
                }
                EconomyManager.withdrawBalance(player, cost);
                upgradeFinal(level, player);
            } else if (type == CostType.EXPERIENCE) {
                if (player.getLevel() >= cost || player.getGameMode() == GameMode.CREATIVE) {
                    if (player.getGameMode() != GameMode.CREATIVE) {
                        player.setLevel(player.getLevel() - cost);
                    }
                    upgradeFinal(level, player);
                } else {
                    plugin.getLocale().getMessage("event.upgrade.cannotafford").sendPrefixedMessage(player);
                }
            }
        }
    }

    private void upgradeFinal(Level level, Player player) {
        this.level = level;
        syncName();
        if (plugin.getLevelManager().getHighestLevel() != level) {
            plugin.getLocale().getMessage("event.upgrade.success")
                    .processPlaceholder("level", level.getLevel()).sendPrefixedMessage(player);

        } else {
            plugin.getLocale().getMessage("event.upgrade.maxed")
                    .processPlaceholder("level", level.getLevel()).sendPrefixedMessage(player);
        }
        Location loc = location.clone().add(.5, .5, .5);

        if (!ServerVersion.isServerVersionAtLeast(ServerVersion.V1_12)) return;

        player.getWorld().spawnParticle(org.bukkit.Particle.valueOf(plugin.getConfig().getString("Main.Upgrade Particle Type")), loc, 200, .5, .5, .5);

        if (plugin.getLevelManager().getHighestLevel() != level) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 15.0F);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 2F, 25.0F);

            if (!ServerVersion.isServerVersionAtLeast(ServerVersion.V1_13)) return;

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 2F, 25.0F);
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2F, 35.0F), 5L);
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.8F, 35.0F), 10L);
        }
    }

    private void syncName() {
        org.bukkit.block.Furnace furnace = (org.bukkit.block.Furnace) location.getBlock().getState();
        if (ServerVersion.isServerVersionAtLeast(ServerVersion.V1_10))
            furnace.setCustomName(Methods.formatName(level.getLevel(), uses, false));
        furnace.update(true);
    }

    public void updateCook() {
        Block block = location.getBlock();
        if (!block.getType().name().contains("FURNACE")) return;

        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            int num = getPerformanceTotal();

            if (num > 200)
                num = 200;

            if (num != 0) {
                BlockState bs = (block.getState()); // max is 200
                ((org.bukkit.block.Furnace) bs).setCookTime(Short.parseShort(Integer.toString(num)));
                bs.update();
            }
        }, 1L);
    }


    public Level getLevel() {
        return level;
    }


    public List<UUID> getAccessList() {
        List<UUID> list = new ArrayList<>();
        for (String line : accessList) {
            String[] halfs = line.split(":");
            list.add(UUID.fromString(halfs[0]));
        }

        return list;
    }

    public List<String> getRawAccessList() {
        return new ArrayList<>(accessList);
    }


    public int getPerformanceTotal() {
        String equation = "(" + level.getPerformance() + " / 100) * 200";
        try {
            if (!cache.containsKey(equation)) {
                ScriptEngineManager mgr = new ScriptEngineManager();
                ScriptEngine engine = mgr.getEngineByName("JavaScript");
                int num = (int) Math.round(Double.parseDouble(engine.eval("(" + level.getPerformance() + " / 100) * 200").toString()));
                cache.put(equation, num);
                return num;
            } else {
                return cache.get(equation);
            }
        } catch (ScriptException e) {
            e.printStackTrace();
        }
        return 0;
    }


    public List<String> getOriginalAccessList() {
        return Collections.unmodifiableList(accessList);
    }


    public boolean addToAccessList(Player player) {
        String formatted = player.getUniqueId().toString() + ":" + player.getName();
        if (accessList.contains(formatted)) return false;
        return addToAccessList(formatted);
    }

    public boolean addToAccessList(String formatted) {
        if (accessList.contains(formatted)) return false;
        return accessList.add(formatted);
    }


    public boolean removeFromAccessList(String string) {
        return accessList.remove(string);
    }


    public void clearAccessList() {
        accessList.clear();
    }

    public List<Location> getRadius(boolean overHeat) {
        if (overHeat)
            return radiusOverheat.isEmpty() ? null : Collections.unmodifiableList(radiusOverheat);
        else
            return radiusFuelshare.isEmpty() ? null : Collections.unmodifiableList(radiusFuelshare);

    }


    public void addToRadius(Location location, boolean overHeat) {
        if (overHeat)
            radiusOverheat.add(location);
        else
            radiusFuelshare.add(location);

    }


    public void clearRadius(boolean overHeat) {
        if (overHeat)
            radiusOverheat.clear();
        else
            radiusFuelshare.clear();
    }


    public int getRadiusLast(boolean overHeat) {
        if (overHeat)
            return radiusOverheatLast;
        else
            return radiusFuelshareLast;
    }


    public void setRadiusLast(int radiusLast, boolean overHeat) {
        if (overHeat)
            this.radiusOverheatLast = radiusLast;
        else
            this.radiusFuelshareLast = radiusLast;
    }

    public Location getLocation() {
        return location.clone();
    }

    public boolean isInLoadedChunk() {
        return location != null && location.getWorld() != null && location.getWorld().isChunkLoaded(((int) location.getX()) >> 4, ((int) location.getZ()) >> 4);
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public UUID getPlacedBy() {
        return placedBy;
    }

    public void setPlacedBy(UUID placedBy) {
        this.placedBy = placedBy;
    }

    public int getUses() {
        return uses;
    }

    public void setUses(int uses) {
        this.uses = uses;
    }

    public int getTolevel() {
        return tolevel;
    }

    public void setTolevel(int tolevel) {
        this.tolevel = tolevel;
    }

    public int getRadiusOverheatLast() {
        return radiusOverheatLast;
    }

    public void setRadiusOverheatLast(int radiusOverheatLast) {
        this.radiusOverheatLast = radiusOverheatLast;
    }

    public int getRadiusFuelshareLast() {
        return radiusFuelshareLast;
    }

    public void setRadiusFuelshareLast(int radiusFuelshareLast) {
        this.radiusFuelshareLast = radiusFuelshareLast;
    }
}