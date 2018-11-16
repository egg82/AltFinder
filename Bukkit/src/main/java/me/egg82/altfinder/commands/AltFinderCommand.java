package me.egg82.altfinder.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import co.aikar.taskchain.TaskChainFactory;
import me.egg82.altfinder.commands.internal.DeleteCommand;
import me.egg82.altfinder.commands.internal.ReloadCommand;
import me.egg82.altfinder.commands.internal.SearchCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

@CommandAlias("altfinder")
public class AltFinderCommand extends BaseCommand {
    private final Plugin plugin;
    private final TaskChainFactory taskFactory;

    public AltFinderCommand(Plugin plugin, TaskChainFactory taskFactory) {
        this.plugin = plugin;
        this.taskFactory = taskFactory;
    }

    @Subcommand("reload")
    @CommandPermission("altfinder.admin")
    @Description("Reloads the plugin.")
    public void onReload(CommandSender sender) {
        new ReloadCommand(plugin, taskFactory.newChain(), sender).run();
    }

    @Subcommand("search|find")
    @CommandPermission("altfinder.search")
    @Description("Finds potential alt accounts on the IP or player specified.")
    @Syntax("<ip|name>")
    public void onSearch(CommandSender sender, String search) {
        new SearchCommand(taskFactory.newChain(), sender, search).run();
    }

    @Subcommand("delete|del|gdpr")
    @CommandPermission("altfinder.admin")
    @Description("Removes a given IP or player from the system.")
    @Syntax("<ip|name>")
    public void onDelete(CommandSender sender, String search) {
        new DeleteCommand(taskFactory.newChain(), sender, search).run();
    }

    @CatchUnknown @Default
    @CommandCompletion("@subcommand")
    public void onDefault(CommandSender sender, String[] args) {
        Bukkit.getServer().dispatchCommand(sender, "altfinder help");
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
