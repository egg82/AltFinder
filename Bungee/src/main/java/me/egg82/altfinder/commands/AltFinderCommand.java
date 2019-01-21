package me.egg82.altfinder.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import me.egg82.altfinder.AltFinder;
import me.egg82.altfinder.commands.internal.DeleteCommand;
import me.egg82.altfinder.commands.internal.ReloadCommand;
import me.egg82.altfinder.commands.internal.SearchCommand;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Plugin;

@CommandAlias("altfinder")
public class AltFinderCommand extends BaseCommand {
    private final AltFinder concrete;
    private final Plugin plugin;

    public AltFinderCommand(AltFinder concrete, Plugin plugin) {
        this.concrete = concrete;
        this.plugin = plugin;
    }

    @Subcommand("reload")
    @CommandPermission("altfinder.admin")
    @Description("Reloads the plugin.")
    public void onReload(CommandSender sender) {
        new ReloadCommand(concrete, plugin, sender).run();
    }

    @Subcommand("search|find")
    @CommandPermission("altfinder.search")
    @Description("Finds potential alt accounts on the IP or player specified.")
    @Syntax("<ip|name>")
    public void onSearch(CommandSender sender, String search) {
        new SearchCommand(sender, search).run();
    }

    @Subcommand("delete|del|gdpr")
    @CommandPermission("altfinder.admin")
    @Description("Removes a given IP or player from the system.")
    @Syntax("<ip|name>")
    public void onDelete(CommandSender sender, String search) {
        new DeleteCommand(sender, search).run();
    }

    @CatchUnknown @Default
    @CommandCompletion("@subcommand")
    public void onDefault(CommandSender sender, String[] args) {
        plugin.getProxy().getPluginManager().dispatchCommand(sender, "altfinder help");
    }

    @HelpCommand
    @Syntax("[command]")
    public void onHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();

        if (help.getCommandName() == null || help.getCommandName().isEmpty()) {
            sender.sendMessage(ChatColor.WHITE + "OVERRIDES " + ChatColor.AQUA + "/seen");
        }
    }
}
