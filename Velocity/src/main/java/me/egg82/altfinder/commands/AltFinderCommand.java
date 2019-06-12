package me.egg82.altfinder.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;
import me.egg82.altfinder.commands.internal.DeleteCommand;
import me.egg82.altfinder.commands.internal.ReloadCommand;
import me.egg82.altfinder.commands.internal.SearchCommand;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;

@CommandAlias("altfinder")
public class AltFinderCommand extends BaseCommand {
    private final Object plugin;
    private final ProxyServer proxy;
    private final PluginDescription pluginDescription;

    public AltFinderCommand(Object plugin, ProxyServer proxy, PluginDescription description) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.pluginDescription = description;
    }

    @Subcommand("reload")
    @CommandPermission("altfinder.admin")
    @Description("Reloads the plugin.")
    public void onReload(CommandSource source) {
        new ReloadCommand(plugin, proxy, pluginDescription, source).run();
    }

    @Subcommand("search|find")
    @CommandPermission("altfinder.search")
    @Description("Finds potential alt accounts on the IP or player specified.")
    @Syntax("<ip|name>")
    public void onSearch(CommandSource source, String search) {
        new SearchCommand(source, search, proxy).run();
    }

    @Subcommand("delete|del|gdpr")
    @CommandPermission("altfinder.admin")
    @Description("Removes a given IP or player from the system.")
    @Syntax("<ip|name>")
    public void onDelete(CommandSource source, String search) {
        new DeleteCommand(source, search, proxy).run();
    }

    @CatchUnknown @Default
    @CommandCompletion("@subcommand")
    public void onDefault(CommandSource source, String[] args) {
        proxy.getCommandManager().execute(source, "altfinder help");
    }

    @HelpCommand
    @Syntax("[command]")
    public void onHelp(CommandSource source, CommandHelp help) {
        help.showHelp();

        if (help.getCommandName() == null || help.getCommandName().isEmpty()) {
            source.sendMessage(TextComponent.builder("OVERRIDES ").color(TextColor.WHITE).append(TextComponent.of("/seen").color(TextColor.AQUA)).build());
        }
    }
}
