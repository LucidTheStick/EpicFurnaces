package com.songoda.epicfurnaces.tasks;

import com.songoda.core.compatibility.CompatibleParticleHandler;
import com.songoda.epicfurnaces.EpicFurnaces;
import com.songoda.epicfurnaces.furnace.Furnace;
import com.songoda.epicfurnaces.settings.Settings;
import java.util.HashSet;

import com.sun.org.apache.bcel.internal.generic.FADD;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

public class FurnaceTask extends BukkitRunnable {

    private static FurnaceTask instance;

    private final EpicFurnaces plugin;
    final HashSet<Location> toRemove = new HashSet();
    boolean doParticles;

    private FurnaceTask(EpicFurnaces plugin) {
        this.plugin = plugin;
    }

    public static FurnaceTask startTask(EpicFurnaces plugin) {
        if (instance == null) {
            instance = new FurnaceTask(plugin);
            instance.runTaskTimer(plugin, 0, Settings.TICK_SPEED.getInt());
        }

        return instance;
    }

    @Override
    public void run() {
        doParticles = Settings.OVERHEAT_PARTICLES.getBoolean();
        plugin.getFurnaceManager().getFurnaces().values().stream()
                .filter(Furnace::isInLoadedChunk)
                .forEach(furnace -> {
                    Location location = furnace.getLocation();
                    BlockState state = location.getBlock().getState();

                    if (!(state instanceof org.bukkit.block.Furnace)) {
                        toRemove.add(location);
                    } else if (((org.bukkit.block.Furnace) state).getBurnTime() != 0) {
                        if (furnace.getLevel().getOverheat() != 0) {
                            overheat(furnace);
                        }
                        if (furnace.getLevel().getFuelShare() != 0) {
                            fuelshare(furnace);
                        }
                    }
                });
        if (!toRemove.isEmpty()) {
            toRemove.stream().forEach(l -> plugin.getFurnaceManager().removeFurnace(l));
            toRemove.clear();
        }
    }

    private void overheat(Furnace furnace) {
        if (furnace.getRadius(true) == null || furnace.getRadiusLast(true) != furnace.getLevel().getOverheat()) {
            furnace.setRadiusLast(furnace.getLevel().getOverheat(), true);
            cache(furnace, true);
        }

        for (Location location : furnace.getRadius(true)) {
            int random = ThreadLocalRandom.current().nextInt(0, 10);

            if (random != 1) continue;

            Block block = location.getBlock();

            if (block.getType() == Material.AIR || block.getRelative(BlockFace.UP).getType() != Material.AIR) continue;

            if (block.getType() == Material.SNOW)
                block.setType(Material.AIR);
            else if (block.getType() == Material.ICE || block.getType() == Material.PACKED_ICE)
                block.setType(Material.WATER);
            else
               continue;

            if (doParticles) {
                float xx = (float) (0 + (Math.random() * .75));
                float yy = (float) (0 + (Math.random() * 1));
                float zz = (float) (0 + (Math.random() * .75));

                CompatibleParticleHandler.spawnParticles(CompatibleParticleHandler.ParticleType.SMOKE_NORMAL, location, 25, xx, yy, zz, 0);
            }
        }
    }

    private void fuelshare(Furnace furnace) {
        if (furnace.getRadius(false) == null || furnace.getRadiusLast(false) != furnace.getLevel().getOverheat()) {
            furnace.setRadiusLast(furnace.getLevel().getOverheat(), false);
            cache(furnace, false);
        }

        for (Location location : furnace.getRadius(false)) {
            int random = ThreadLocalRandom.current().nextInt(0, 10);

            if (random != 1) continue;

            Block block = location.getBlock();

            if (!block.getType().name().contains("FURNACE")) continue;
            Furnace furnace1 = plugin.getFurnaceManager().getFurnace(block);
            if (furnace == furnace1) continue;
            org.bukkit.block.Furnace furnaceBlock = ((org.bukkit.block.Furnace) block.getState());
            if (furnaceBlock.getBurnTime() == 0) {
                furnaceBlock.setBurnTime((short) 100);
                furnaceBlock.update();

                if (doParticles) {

                    float xx = (float) (0 + (Math.random() * .75));
                    float yy = (float) (0 + (Math.random() * 1));
                    float zz = (float) (0 + (Math.random() * .75));

                    CompatibleParticleHandler.spawnParticles(CompatibleParticleHandler.ParticleType.SMOKE_NORMAL, location, 25, xx, yy, zz);
                }
            }
        }
    }

    private void cache(Furnace furnace, boolean overheat) {
        Block block = furnace.getLocation().getBlock();
        int radius = 3 * (overheat ? furnace.getLevel().getOverheat() : furnace.getLevel().getFuelShare());
        int rSquared = radius * radius;
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();

        for (int fx = -radius; fx <= radius; fx++) {
            for (int fy = -2; fy <= 1; fy++) {
                for (int fz = -radius; fz <= radius; fz++) {
                    if ((fx * fx) + (fz * fz) <= rSquared) {
                        Location location = new Location(block.getWorld(), bx + fx, by + fy, bz + fz);
                        furnace.addToRadius(location, overheat);
                    }
                }
            }
        }
    }
}