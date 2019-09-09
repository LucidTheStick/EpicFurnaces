package com.songoda.epicfurnaces.commands;

import com.songoda.core.commands.AbstractCommand;
import com.songoda.core.configuration.editor.PluginConfigGui;
import com.songoda.epicfurnaces.EpicFurnaces;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class CommandSettings extends AbstractCommand {

    final EpicFurnaces plugin;

    public CommandSettings(EpicFurnaces plugin) {
        super(true, "settings");
        this.plugin = plugin;
    }

    @Override
    protected ReturnType runCommand(CommandSender sender, String... args) {
        plugin.getGuiManager().showGUI((Player) sender, new PluginConfigGui(plugin));
        return ReturnType.SUCCESS;
    }

    @Override
    protected List<String> onTab(CommandSender commandSender, String... strings) {
        return null;
    }

    @Override
    public String getPermissionNode() {
        return "epicfurnaces.admin";
    }

    @Override
    public String getSyntax() {
        return "/ef settings";
    }

    @Override
    public String getDescription() {
        return "Edit the EpicFurnaces Settings.";
    }
}
