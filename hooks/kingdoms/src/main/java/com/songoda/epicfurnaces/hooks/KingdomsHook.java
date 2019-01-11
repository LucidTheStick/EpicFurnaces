package com.songoda.epicfurnaces.hooks;

import com.songoda.epicfurnaces.hook.ProtectionPluginHook;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.kingdoms.constants.land.Land;
import org.kingdoms.constants.land.SimpleChunkLocation;
import org.kingdoms.constants.player.KingdomPlayer;
import org.kingdoms.main.Kingdoms;
import org.kingdoms.manager.game.GameManagement;

public class KingdomsHook implements ProtectionPluginHook {

    private final Kingdoms kingdoms;

    public KingdomsHook() {
        this.kingdoms = Kingdoms.getInstance();
    }

    public JavaPlugin getPlugin() {
        return kingdoms;
    }

    public boolean canBuild(Player player, Location location) {
        KingdomPlayer kPlayer = GameManagement.getPlayerManager().getOfflineKingdomPlayer(player).getKingdomPlayer();
        if (kPlayer.getKingdom() == null) return true;

        SimpleChunkLocation chunkLocation = new SimpleChunkLocation(location.getChunk());
        Land land = GameManagement.getLandManager().getOrLoadLand(chunkLocation);
        String owner = land.getOwner();

        return owner == null || kPlayer.getKingdom().getKingdomName().equals(owner);
    }

}